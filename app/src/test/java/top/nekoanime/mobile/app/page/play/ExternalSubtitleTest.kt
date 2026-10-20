package top.nekoanime.mobile.app.page.play

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.collect.ImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.nekoanime.mobile.app.model.dto.EpisodePlayDto
import top.nekoanime.mobile.app.model.entity.Episode
import top.nekoanime.mobile.app.model.entity.EpisodePlay
import top.nekoanime.mobile.app.model.entity.EpisodeSubtitle
import top.nekoanime.mobile.app.model.entity.parseEpisodeSubtitles
import top.nekoanime.mobile.app.page.play.component.player.collectSubtitleTracks
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 验证响应容错、原生单轨菜单及独立字幕会话，不访问线上接口或设备。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ExternalSubtitleTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val validVtt = "WEBVTT\n\n00:00.000 --> 00:02.000\n测试字幕\n"

    /** 当前后端四字段契约可以直接解析，未声明格式时使用 WebVTT。 */
    @Test
    fun parsesBackendContract() {
        val dto = json.decodeFromString<EpisodePlayDto>(
            """{"video":"https://example.test/video.mp4","subtitles":[{"label":"简体","language":"zh-Hans","default":true,"url":"https://example.test/a.vtt"}]}""",
        )
        val track = parseEpisodeSubtitles(dto.subtitles).single()
        assertEquals("简体", track.label)
        assertEquals("zh-Hans", track.language)
        assertTrue(track.isDefault)
        assertNull(track.format)
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType(track.format))
    }

    /** 保留数字或字符串 id、格式及任意嵌套元数据，保存恢复播放响应时也不丢字段。 */
    @Test
    fun preservesAllMetadataThroughSavedState() {
        val metadata = json.parseToJsonElement(
            """[{"id":42,"label":"字幕","language":"ja","format":"srt","url":"https://example.test/a","extra":{"offset":12,"tags":["a",true]}},{"id":"second","url":"https://example.test/b"}]""",
        )
        val subtitles = parseEpisodeSubtitles(metadata)
        val source = EpisodePlay(Episode(7, 1, "第一集"), "video", subtitles)
        val restored = json.decodeFromString<EpisodePlay>(json.encodeToString(EpisodePlay.serializer(), source))
        assertEquals(metadata.jsonArray.map { it.jsonObject }, restored.subtitles.map { it.metadata })
        assertEquals(metadata.jsonArray[0].jsonObject["id"], restored.subtitles[0].id)
        assertEquals("srt", restored.subtitles[0].format)
        assertEquals("https://example.test/a", restored.subtitles[0].url)
    }

    /** 字幕缺失、null、错误类型及畸形条目均不能阻断视频字段。 */
    @Test
    fun malformedSubtitlePayloadDoesNotBreakVideo() {
        listOf(null, "null", "{}", "true", "\"bad\"", "[null,{},7,{\"url\":\"\"}]").forEach { value ->
            val field = value?.let { ",\"subtitles\":$it" }.orEmpty()
            val dto = json.decodeFromString<EpisodePlayDto>("{\"video\":\"video.mp4\"$field}")
            assertEquals("video.mp4", dto.video)
            assertTrue(parseEpisodeSubtitles(dto.subtitles).isEmpty())
        }
        assertTrue(parseEpisodeSubtitles(JsonNull).isEmpty())
    }

    /** 非字符串 URL 不作为可下载地址，但原始元数据的合法扩展字段不受影响。 */
    @Test
    fun ignoresNonStringUrls() {
        assertTrue(parseEpisodeSubtitles(json.parseToJsonElement("[{\"url\":true},{\"url\":12}]")).isEmpty())
    }

    /** 只接受明确支持的格式，未知格式不进入字幕加载链路。 */
    @Test
    fun mapsNativeSubtitleFormats() {
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("WEBVTT"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("application/x-subrip"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("ASS"))
        assertEquals(MimeTypes.APPLICATION_TTML, subtitleMimeType("ttml"))
        assertNull(subtitleMimeType("pgs"))
    }

    /** 无可解码字幕时菜单为空；音频轨和不支持轨不应伪装成字幕。 */
    @Test
    fun hidesMenuOnlyWhenNoUsableTracks() {
        assertTrue(collectSubtitleTracks(Tracks.EMPTY, emptySet()).isEmpty())
        val audio = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        val text = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build()
        val tracks = Tracks(ImmutableList.of(group(audio), group(text, C.FORMAT_UNSUPPORTED_TYPE)))
        assertTrue(collectSubtitleTracks(tracks, emptySet()).isEmpty())
    }

    /** 下载未完成或失败的外挂不可选，内嵌字幕始终保留在同一菜单。 */
    @Test
    fun combinesEmbeddedAndReadyExternalTracks() {
        val embedded = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("zh").build()
        val external = embedded.buildUpon().setId("neko-external:7:0").setLabel("外挂").build()
        val tracks = Tracks(ImmutableList.of(group(embedded), group(external)))
        assertEquals(1, collectSubtitleTracks(tracks, emptySet()).size)
        val available = collectSubtitleTracks(tracks, setOf("neko-external:7:0"))
        assertEquals(2, available.size)
        assertEquals("外挂", available[1].label)
    }

    /** 切轨使用按类型覆盖，先前选中的字幕覆盖必须被替换而不是叠加。 */
    @Test
    fun nativeOverrideSelectsExactlyOneSubtitle() {
        val first = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setId("a").build()
        val second = first.buildUpon().setId("b").build()
        val tracks = collectSubtitleTracks(Tracks(ImmutableList.of(group(first), group(second))), emptySet())
        val parameters = TrackSelectionParameters.Builder(RuntimeEnvironment.getApplication())
            .setOverrideForType(tracks[0].override)
            .setOverrideForType(tracks[1].override)
            .build()
        assertEquals(1, parameters.overrides.size)
        assertEquals(tracks[1].override, parameters.overrides.values.single())
        assertEquals(listOf(0), parameters.overrides.values.single().trackIndices)
    }

    /** 不再按轨道顺序猜测简繁体，重复中文名称仅编号。 */
    @Test
    fun duplicateChineseTracksAreNotGuessedAsSimplifiedAndTraditional() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("chi").build()
        val tracks = collectSubtitleTracks(
            Tracks(ImmutableList.of(group(format), group(format.buildUpon().setId("second").build()))),
            emptySet(),
        )
        assertEquals(listOf("中文 1", "中文 2"), tracks.map { it.label })
    }

    /** 成功缓存按 DataSource 契约读取、到尾返回 EOS，关闭后释放读取引用。 */
    @Test
    fun readsValidatedCacheAsNativeSample() {
        val source = SubtitleDataSource(CompletableFuture.completedFuture("abcd".toByteArray()))
        assertEquals(3L, source.open(DataSpec.Builder().setUri("https://example.test/sub").setPosition(1).build()))
        val result = ByteArray(3)
        assertEquals(3, source.read(result, 0, 3))
        assertArrayEquals("bcd".toByteArray(), result)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(result, 0, 1))
        assertEquals(0, source.read(result, 0, 0))
        source.close()
        assertNull(source.uri)
    }

    /** 失败缓存必须抛加载异常供原生源转 EOS，不能提交空坏样本给渲染器。 */
    @Test
    fun failedCacheThrowsInsteadOfProducingEmptySample() {
        val source = SubtitleDataSource(CompletableFuture.completedFuture(null))
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("https://example.test/sub"))) }
        source.close()
    }

    /** 等待下载的字幕 Loader 可以被取消解除，不泄漏阻塞线程。 */
    @Test
    fun cancelledCacheUnblocksWaitingLoader() {
        val pending = CompletableFuture<ByteArray?>()
        val worker = CompletableFuture.supplyAsync {
            val source = SubtitleDataSource(pending)
            try {
                source.open(DataSpec(Uri.parse("https://example.test/sub")))
                false
            } catch (_: IOException) {
                true
            } finally {
                source.close()
            }
        }
        pending.complete(null)
        assertTrue(worker.get(2, TimeUnit.SECONDS))
    }

    /** 文件下载并经原生解析器校验后才暴露外挂轨，且不向字幕请求发送账号凭据。 */
    @Test
    fun downloadsAndValidatesIndependentlyWithoutAuthorization() {
        withSession { server, session, player ->
            server.enqueue(MockResponse().setBody(validVtt))
            session.setSource(player, "https://example.test/video.mp4", listOf(subtitle(server)), "7")
            await { session.availableTrackIds.value.isNotEmpty() }
            assertEquals(setOf("neko-external:7:0"), session.availableTrackIds.value)
            val request = server.takeRequest(2, TimeUnit.SECONDS)
            assertNotNull(request)
            assertNull(request!!.getHeader("Authorization"))
            assertEquals("7", player.currentMediaItem!!.mediaId)
            assertNull(player.playerError)
        }
    }

    /** 网络错误、坏格式、空文件及超限响应只让各自字幕不可用。 */
    @Test
    fun subtitleFailuresNeverPublishAvailableTracks() {
        withSession { server, session, player ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("not a WebVTT file"))
            server.enqueue(MockResponse().setBody(""))
            server.enqueue(MockResponse().setHeader("Content-Length", MaxSubtitleBytes + 1))
            val subtitles = (0..3).map { subtitle(server, "/$it.vtt") }
            session.setSource(player, "https://example.test/video.mp4", subtitles, "7")
            repeat(4) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            Thread.sleep(300)
            assertTrue(session.availableTrackIds.value.isEmpty())
            assertNull(player.playerError)
        }
    }

    /** 旧集的迟到结果不能发布到新集，即使旧请求先于新请求发出。 */
    @Test
    fun switchingEpisodeDiscardsLateResults() {
        withSession { server, session, player ->
            server.enqueue(MockResponse().setBody(validVtt).setBodyDelay(1, TimeUnit.SECONDS))
            session.setSource(player, "https://example.test/old.mp4", listOf(subtitle(server, "/old.vtt")), "7")
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody(validVtt))
            session.setSource(player, "https://example.test/new.mp4", listOf(subtitle(server, "/new.vtt")), "8")
            await { session.availableTrackIds.value == setOf("neko-external:8:0") }
            Thread.sleep(1200)
            assertEquals(setOf("neko-external:8:0"), session.availableTrackIds.value)
            assertEquals("8", player.currentMediaItem!!.mediaId)
        }
    }

    /** 会话关闭会取消未完成请求并清空可用轨，不接收销毁后的迟到回调。 */
    @Test
    fun closingSessionCancelsOutstandingSubtitles() {
        withSession { server, session, player ->
            server.enqueue(MockResponse().setBody(validVtt).setBodyDelay(1, TimeUnit.SECONDS))
            session.setSource(player, "https://example.test/video.mp4", listOf(subtitle(server)), "7")
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            session.close()
            Thread.sleep(1200)
            assertTrue(session.availableTrackIds.value.isEmpty())
            assertFalse(player.isPlaying)
        }
    }

    /** 未知字幕格式不会发网络请求，但不妨碍创建视频媒体项。 */
    @Test
    fun unsupportedFormatDoesNotRequestOrBlockVideoSource() {
        withSession { server, session, player ->
            val subtitle = EpisodeSubtitle(json.parseToJsonElement("""{"format":"unknown","url":"${server.url("/sub")}"}""").jsonObject)
            session.setSource(player, "https://example.test/video.mp4", listOf(subtitle), "7")
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
            assertEquals("7", player.currentMediaItem!!.mediaId)
            assertTrue(session.availableTrackIds.value.isEmpty())
        }
    }

    /** 构造单条原生轨，支持状态由各测试显式声明。 */
    private fun group(format: Format, support: Int = C.FORMAT_HANDLED): Tracks.Group = Tracks.Group(
        TrackGroup(format), false, intArrayOf(support), booleanArrayOf(false),
    )

    /** 构造指向本地测试服务器的真实契约字幕描述。 */
    private fun subtitle(server: MockWebServer, path: String = "/sub.vtt"): EpisodeSubtitle =
        EpisodeSubtitle(json.parseToJsonElement("""{"label":"字幕","language":"zh","url":"${server.url(path)}"}""").jsonObject)

    /** 为单测管理播放器、独立会话与本地 HTTP 服务的完整生命周期。 */
    private fun withSession(test: (MockWebServer, ExternalSubtitleSession, ExoPlayer) -> Unit) {
        val server = MockWebServer()
        server.start()
        val context = RuntimeEnvironment.getApplication()
        val session = ExternalSubtitleSession(context)
        val player = ExoPlayer.Builder(context).build()
        try {
            test(server, session, player)
        } finally {
            session.close()
            player.release()
            server.shutdown()
        }
    }

    /** 有界等待独立 IO 任务结束，超时作为测试失败而非无限阻塞。 */
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("字幕任务未在期限内完成", condition())
    }
}
