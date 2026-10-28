package top.nekoanime.mobile.app.page.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.nekoanime.mobile.app.model.entity.Episode

/** 只测连播决策，无 Android 运行时、网络或计时依赖；故意把接口集数顺序打乱。 */
class EpisodeAutoAdvanceTest {
    private val episodes = listOf(Episode(30, 3, "第三集"), Episode(10, 1, "第一集"), Episode(20, 2, "第二集"))
    private val first = EpisodeAutoAdvance.Source("10", "first.mp4", "window-1")
    private val gate = EpisodeAutoAdvance()

    /** 把播放器快照和页面选集意图分开传，便于直接构造换源、手动选集的竞态。 */
    private fun event(
        source: EpisodeAutoAdvance.Source? = first,
        ready: Boolean = false,
        ended: Boolean = false,
        selected: Int = 10,
        expected: Int? = 10,
        expectedUrl: String? = "first.mp4",
        list: List<Episode> = episodes,
    ): Int? = gate.nextEpisodeId(source, ready, ended, selected, expected, expectedUrl, list)

    /** 下一集按 number 找相邻项，不能按接口列表顺序或 ID 数值加一。 */
    @Test
    fun naturalEndAdvancesInEpisodeNumberOrder() {
        assertNull(event(ready = true))
        assertEquals(20, event(ended = true))
    }

    /** 每个实际源只消费一次；自动请求失败后重复 ENDED 也不能连发网络请求。 */
    @Test
    fun duplicateEndIsConsumedOnce() {
        event(ready = true)
        assertEquals(20, event(ended = true))
        assertNull(event(ended = true))
        event(ready = true)
        assertNull(event(ended = true))
    }

    /** 最后一集保留结束状态，不回到第一集。 */
    @Test
    fun finalEpisodeNeverWraps() {
        val last = EpisodeAutoAdvance.Source("30", "last.mp4", "window-3")
        event(last, ready = true, selected = 30, expected = 30, expectedUrl = last.url)
        assertFalse(gate.hasConsumedEnd)
        assertNull(event(last, ended = true, selected = 30, expected = 30, expectedUrl = last.url))
        assertTrue(gate.hasConsumedEnd)
        assertNull(event(last, ended = true, selected = 30, expected = 30, expectedUrl = last.url))
        assertTrue(gate.hasConsumedEnd)
    }

    /** 初始已结束或旧源排队事件不能越过新源 READY 许可。 */
    @Test
    fun endedWithoutReadyDoesNotAdvance() {
        assertNull(event(ended = true))
    }

    /** 暂停、缓冲、出错对应非 ENDED 快照，均不能触发自动选集。 */
    @Test
    fun nonEndedStatesDoNotAdvance() {
        event(ready = true)
        repeat(3) { assertNull(event()) }
    }

    /** API 已成功但播放器尚未换源时，旧源 ENDED 必须被拒绝。 */
    @Test
    fun oldMediaIdCannotEndNewEpisode() {
        event(ready = true)
        assertNull(event(ended = true, selected = 20, expected = 20, expectedUrl = "second.mp4"))
    }

    /** 同集更新了播放地址，也不能把旧地址的结束误认为新源结束。 */
    @Test
    fun oldUrlCannotEndNewSource() {
        event(ready = true)
        assertNull(event(ended = true, expectedUrl = "replacement.mp4"))
    }

    /** 同 ID、同 URL 重建也属于新媒体窗口，必须重新到达 READY。 */
    @Test
    fun replacedWindowNeedsItsOwnReady() {
        event(ready = true)
        val replacement = first.copy(windowUid = "replacement-window")
        assertNull(event(replacement, ended = true))
        event(replacement, ready = true)
        assertEquals(20, event(replacement, ended = true))
    }

    /** 手动目标尚在请求中时，即使旧源确实结束也不能覆盖手动选择。 */
    @Test
    fun manualSelectionWinsBeforeRecompositionOrSourceLoad() {
        event(ready = true)
        assertNull(event(ended = true, selected = 30))
    }

    /** A→B→A 连点后不能复用第一次 A 的 READY，把迟到的结束变成自动跳 B。 */
    @Test
    fun manualRoundTripRevokesOldReady() {
        event(ready = true)
        gate.onManualSelection()
        gate.onManualSelection()
        assertNull(event(ended = true))
        event(ready = true)
        assertEquals(20, event(ended = true))
    }

    /** 空媒体、未知 ID、格式相似但不完全一致的 ID 都不能按页面选集兜底。 */
    @Test
    fun missingOrNonEpisodeMediaIdIsRejected() {
        assertNull(event(null, ready = true))
        assertNull(event(null, ended = true))
        listOf("", "unknown", "010", "99").forEach { id ->
            val other = first.copy(mediaId = id)
            assertNull(event(other, ready = true))
            assertNull(event(other, ended = true))
        }
    }

    /** 列表不含当前真实集时必须停住；单集番和空列表也不产生回绕目标。 */
    @Test
    fun missingCurrentEpisodeOrNextEpisodeDoesNotAdvance() {
        listOf(emptyList(), episodes.filter { it.id != 10 }, episodes.filter { it.id == 10 }).forEach { list ->
            val fresh = EpisodeAutoAdvance()
            fresh.nextEpisodeId(first, true, false, 10, 10, first.url, list)
            assertNull(fresh.nextEpisodeId(first, false, true, 10, 10, first.url, list))
        }
    }

    /** 前集已消费不影响后集：窗口切换后可完成下一轮自然连播。 */
    @Test
    fun nextLoadedEpisodeCanAdvanceAgain() {
        event(ready = true)
        assertEquals(20, event(ended = true))
        val second = EpisodeAutoAdvance.Source("20", "second.mp4", "window-2")
        event(second, ready = true, selected = 20, expected = 20, expectedUrl = second.url)
        assertEquals(30, event(second, ended = true, selected = 20, expected = 20, expectedUrl = second.url))
    }
}
