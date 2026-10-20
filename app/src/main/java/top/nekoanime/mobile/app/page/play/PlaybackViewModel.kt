package top.nekoanime.mobile.app.page.play

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

/**
 * 每个播放页的播放会话，返回栈保留播放器、字幕缓存与请求。
 * 页面离开组合只暂停，真正弹出时取消字幕请求并释放播放器。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel : ViewModel() {
    private var exoPlayer: ExoPlayer? = null
    private var subtitles: ExternalSubtitleSession? = null

    /**
     * 取本会话的播放器，没有就新建一个。
     *
     * @param context 内部只持有 applicationContext，避免泄漏 Activity
     */
    fun player(context: Context): ExoPlayer = exoPlayer ?: ExoPlayer.Builder(
        context.applicationContext,
        object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>,
            ) {
                out += TextRenderer(output, outputLooper).apply {
                    experimentalSetLegacyDecodingEnabled(true)
                }
            }
        },
    )
        .build()
        .apply { playWhenReady = true }
        .also { exoPlayer = it }

    /** 获取跟随返回栈存活的独立外挂字幕会话。 */
    fun subtitles(context: Context): ExternalSubtitleSession = subtitles
        ?: ExternalSubtitleSession(context.applicationContext).also { subtitles = it }

    /** 整个播放页被弹出时先取消请求，再释放播放器。 */
    override fun onCleared() {
        subtitles?.close()
        subtitles = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
