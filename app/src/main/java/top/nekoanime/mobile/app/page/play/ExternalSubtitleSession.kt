package top.nekoanime.mobile.app.page.play

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import top.nekoanime.mobile.app.model.entity.EpisodeSubtitle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 单条字幕最大字节数，防止错误地址返回视频导致内存失控。 */
internal const val MaxSubtitleBytes = 8 * 1024 * 1024

/**
 * 将后端格式映射到 Media3；当前后端未给 format 时明确约定 WebVTT，不从 URL 猜格式。
 *
 * @param format 原始格式元数据
 * @returns 原生解析器支持的 MIME；未知格式不加载
 */
internal fun subtitleMimeType(format: String?): String? = when (format?.lowercase()) {
    null, "", "vtt", "webvtt", "text/vtt" -> MimeTypes.TEXT_VTT
    "srt", "subrip", "application/x-subrip" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa", "text/x-ssa" -> MimeTypes.TEXT_SSA
    "ttml", "application/ttml+xml" -> MimeTypes.APPLICATION_TTML
    else -> null
}

/**
 * 每个播放会话独立的外挂字幕源管理器。
 *
 * 首次建源即合并原生 SingleSampleMediaSource；后台下载、原生预解析后才把轨暴露给菜单。
 * 字幕 Loader 仅等待自己的内存缓存；失败抛出的 IOException 由原生源转为 EOS，不送空样本给解析器。
 * 晚到字幕不调用 setMediaSource/prepare，切换只走 Media3 TrackSelectionOverride。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ExternalSubtitleSession(private val context: android.content.Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private var jobs: List<Job> = emptyList()
    private var entries: List<SubtitleEntry> = emptyList()
    private var generation = 0L
    private val available = MutableStateFlow<Set<String>>(emptySet())
    val availableTrackIds = available.asStateFlow()

    /** 本集是否已自动选轨或由用户选择/关闭；跟随返回栈保存，仅在主线程访问。 */
    var subtitleChoiceMade = false

    /**
     * 创建本集视频与外挂字幕的原生合并源，只有切集或用户重试时调用。
     *
     * @param player 本会话播放器
     * @param url 视频地址
     * @param subtitles 完整字幕描述
     * @param mediaId 剧集标识
     */
    fun setSource(player: ExoPlayer, url: String, subtitles: List<EpisodeSubtitle>, mediaId: String) {
        cancelRequests()
        subtitleChoiceMade = false
        val token = generation
        entries = subtitles.mapIndexedNotNull { index, subtitle ->
            subtitleMimeType(subtitle.format)?.let { mime ->
                SubtitleEntry("neko-external:$mediaId:$index", subtitle, mime)
            }
        }
        val video = DefaultMediaSourceFactory(context).createMediaSource(
            MediaItem.Builder().setUri(url).setMediaId(mediaId).build(),
        )
        val sources = entries.map { entry ->
            val configuration = MediaItem.SubtitleConfiguration.Builder(Uri.parse(entry.subtitle.url))
                .setId(entry.trackId)
                .setMimeType(entry.mime)
                .setLanguage(entry.subtitle.language)
                .setLabel(entry.subtitle.label.ifBlank { entry.subtitle.language ?: "外挂字幕" } + "（外挂）")
                .setSelectionFlags(if (entry.subtitle.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
            SingleSampleMediaSource.Factory(DataSource.Factory { SubtitleDataSource(entry.bytes) })
                .setTreatLoadErrorsAsEndOfStream(true)
                .createMediaSource(configuration, C.TIME_UNSET)
        }
        player.setMediaSource(if (sources.isEmpty()) video else MergingMediaSource(video, *sources.toTypedArray()))
        jobs = entries.map { entry ->
            scope.launch {
                try {
                    val bytes = download(entry.subtitle.url)
                    coroutineContext.ensureActive()
                    val format = Format.Builder().setSampleMimeType(entry.mime).build()
                    DefaultSubtitleParserFactory().create(format).parse(
                        bytes, SubtitleParser.OutputOptions.allCues(),
                    ) { }
                    coroutineContext.ensureActive()
                    entry.bytes.complete(bytes)
                    synchronized(available) {
                        if (token == generation) available.value = available.value + entry.trackId
                    }
                } catch (cancelled: CancellationException) {
                    entry.bytes.complete(null)
                    throw cancelled
                } catch (_: Exception) {
                    // 不记录带签名的字幕 URL，也不让字幕错误进入播放器错误通道。
                    entry.bytes.complete(null)
                }
            }
        }
    }

    /** 取消上一集所有网络请求并解除字幕加载器等待，防止旧结果污染新集。 */
    private fun cancelRequests() {
        synchronized(available) {
            generation++
            available.value = emptySet()
        }
        jobs.forEach { it.cancel() }
        entries.forEach { it.bytes.complete(null) }
        jobs = emptyList()
        entries = emptyList()
    }

    /** 页面真正销毁时取消所有任务，播放器由 ViewModel 同时释放。 */
    fun close() {
        cancelRequests()
        scope.cancel()
    }

    /**
     * 异步下载独立字幕，不携带账号令牌；取消协程会立即取消 OkHttp 请求。
     *
     * @param url 后端提供的完整地址
     * @throws IOException 网络失败、空文件或超过大小限制
     */
    private suspend fun download(url: String): ByteArray {
        val call = http.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                /** 网络错误仅结束当前字幕任务。 */
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                /** 有界读取响应，所有路径都关闭连接，不暴露签名地址。 */
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw IOException("字幕请求失败")
                            val body = it.body ?: throw IOException("字幕为空")
                            if (body.contentLength() > MaxSubtitleBytes) throw IOException("字幕过大")
                            body.byteStream().use { stream ->
                                val result = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (result.size() + count > MaxSubtitleBytes) throw IOException("字幕过大")
                                    result.write(buffer, 0, count)
                                }
                                result.toByteArray().also { data ->
                                    if (data.isEmpty()) throw IOException("字幕为空")
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }
}

/** 单集内存缓存和原始描述；请求结束后缓存仅跟随本播放会话，不落磁盘。 */
private data class SubtitleEntry(
    val trackId: String,
    val subtitle: EpisodeSubtitle,
    val mime: String,
    val bytes: CompletableFuture<ByteArray?> = CompletableFuture(),
)

/**
 * 原生字幕媒体源的数据入口，不经过视频的网络加载链路。
 *
 * @param pending 下载并预解析完成的内存缓存
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class SubtitleDataSource(private val pending: CompletableFuture<ByteArray?>) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var data = byteArrayOf()
    private var position = 0
    private var opened = false

    /**
     * 在字幕专属 Loader 等待缓存，不阻塞主线程或视频 Loader。
     *
     * @throws IOException 缓存失败或等待取消；SingleSampleMediaSource 将其转成 EOS，不产生空字幕样本
     */
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        data = try {
            pending.get() ?: throw IOException("字幕不可用")
        } catch (failure: Exception) {
            throw IOException("字幕不可用", failure)
        }
        position = dataSpec.position.coerceAtMost(data.size.toLong()).toInt()
        transferStarted(dataSpec)
        opened = true
        return (data.size - position).toLong()
    }

    /** 顺序读取已校验的缓存样本。 */
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position == data.size) return C.RESULT_END_OF_INPUT
        val count = minOf(length, data.size - position)
        data.copyInto(buffer, offset, position, position + count)
        position += count
        bytesTransferred(count)
        return count
    }

    /** 返回当前字幕地址，仅供 Media3 内部使用。 */
    override fun getUri(): Uri? = uri

    /** 释放本次读取引用，缓存仍由会话管理。 */
    override fun close() {
        if (opened) transferEnded()
        opened = false
        uri = null
        data = byteArrayOf()
    }
}
