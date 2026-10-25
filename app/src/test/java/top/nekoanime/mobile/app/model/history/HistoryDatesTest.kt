package top.nekoanime.mobile.app.model.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.nekoanime.mobile.app.model.entity.WatchHistory
import java.util.TimeZone

/** 使用固定时间点与明确时区验证历史日期，不依赖测试机当天日期或默认时区。 */
class HistoryDatesTest {
    private val shanghai = TimeZone.getTimeZone("Asia/Shanghai")
    private val utc = TimeZone.getTimeZone("UTC")

    /** 真实 update_date 是上海本地时间，必须先转换为绝对时间点。 */
    @Test
    fun serverLocalTimestampMatchesExplicitOffsets() {
        val expected = timestamp("2026-10-25T10:36:51Z")
        assertEquals(expected, parseHistoryTimestamp("2026-10-25 18:36:51"))
        assertEquals(expected, parseHistoryTimestamp("2026-10-25T18:36:51+08:00"))
        assertEquals(expected, parseHistoryTimestamp("2026-10-25T18:36:51+0800"))
        assertEquals(expected, parseHistoryTimestamp(" 2026-10-25T18:36:51+08:00 "))
    }

    /** ISO 小数秒最多保留毫秒，纳秒输入也不能改变秒位。 */
    @Test
    fun fractionalSecondsKeepMillisecondPrecision() {
        val base = timestamp("2026-10-25T10:36:51Z")
        assertEquals(base + 100L, parseHistoryTimestamp("2026-10-25T10:36:51.1Z"))
        assertEquals(base + 123L, parseHistoryTimestamp("2026-10-25T18:36:51.1234567+08:00"))
        assertEquals(base + 123L, parseHistoryTimestamp("2026-10-25 18:36:51.123"))
    }

    /** 缺失、空白、非法日历日期和尾随垃圾统一保持未知，不能回退到当前时间。 */
    @Test
    fun missingAndMalformedDatesStayUnknown() {
        listOf(null, "", " ", "bad", "2026-02-30 12:00:00", "2026-10-25", "2026-10-25T10:00:00Zjunk", "2026-10-25 25:00:00")
            .forEach { assertNull("应拒绝日期：$it", parseHistoryTimestamp(it)) }
    }

    /** 手机时区转换可能跨越年份，不能直接截取后端字符串的日期部分。 */
    @Test
    fun timezoneConversionCrossesYearBoundary() {
        val instant = timestamp("2026-01-01 00:30:00")
        assertEquals(HistoryLocalDay(2026, 1, 1), historyLocalDay(instant, shanghai))
        assertEquals(HistoryLocalDay(2025, 12, 31), historyLocalDay(instant, utc))
    }

    /** 一月一日的昨天属于上一年，旧年份相同月日也不能被误认为今天。 */
    @Test
    fun relativeTitlesCrossYearAndKeepOldYear() {
        val context = historyDateContext(timestamp("2026-01-01 12:00:00"), shanghai)
        assertEquals("今天", historyDayTitle(HistoryLocalDay(2026, 1, 1), context))
        assertEquals("昨天", historyDayTitle(HistoryLocalDay(2025, 12, 31), context))
        assertEquals("2025年1月1日", historyDayTitle(HistoryLocalDay(2025, 1, 1), context))
        assertEquals("2025年12月30日", historyDayTitle(HistoryLocalDay(2025, 12, 30), context))
        assertEquals("日期未知", historyDayTitle(null, context))
    }

    /** 页面不退出而跨午夜时，同一条日期标题应从今天变为昨天。 */
    @Test
    fun midnightRecalculationChangesRelativeTitle() {
        val day = HistoryLocalDay(2026, 10, 25)
        assertEquals("今天", historyDayTitle(day, historyDateContext(timestamp("2026-10-25 23:59:59"), shanghai)))
        assertEquals("昨天", historyDayTitle(day, historyDateContext(timestamp("2026-10-26 00:00:00"), shanghai)))
    }

    /** 系统改时区或手动调整日期后，以新的上下文重新归类，不保留旧的本地日期。 */
    @Test
    fun changedTimezoneAndClockRecomputeGroupingAndTitles() {
        val instant = timestamp("2026-01-01 00:30:00")
        val record = history(1, instant)
        val local = historyDateContext(instant, shanghai)
        val changed = historyDateContext(instant, utc)
        assertEquals(HistoryLocalDay(2026, 1, 1), groupHistoryByDay(listOf(record), local).single().day)
        assertEquals(HistoryLocalDay(2025, 12, 31), groupHistoryByDay(listOf(record), changed).single().day)
        val advanced = historyDateContext(timestamp("2026-01-03 00:30:00"), shanghai)
        assertEquals("2026年1月1日", historyDayTitle(local.today, advanced))
    }

    /** 夏令时切换日用日历减一天，不能用固定 24 小时而跳到前天。 */
    @Test
    fun yesterdayFollowsCalendarAcrossDaylightSaving() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val spring = historyDateContext(timestamp("2026-03-09T00:30:00-04:00"), newYork)
        assertEquals(HistoryLocalDay(2026, 3, 8), spring.yesterday)
        val autumn = historyDateContext(timestamp("2026-11-01T23:30:00-05:00"), newYork)
        assertEquals(HistoryLocalDay(2026, 10, 31), autumn.yesterday)
    }

    /** 同一本地日只生成一个标题；跨页传入的同日数据会自然合并，未知组排最后。 */
    @Test
    fun groupsMergeSameDayAndPutUnknownLast() {
        val records = listOf(
            history(1, timestamp("2025-12-31 23:30:00")),
            history(2, null),
            history(3, timestamp("2026-01-01 12:00:00")),
            history(4, timestamp("2026-01-01 11:00:00")),
            history(5, null),
        )
        val groups = groupHistoryByDay(records, historyDateContext(timestamp("2026-01-01 13:00:00"), shanghai))
        assertEquals(listOf(HistoryLocalDay(2026, 1, 1), HistoryLocalDay(2025, 12, 31), null), groups.map { it.day })
        assertEquals(listOf(3, 4), groups.first().records.map { it.episodeId })
        assertEquals(listOf(2, 5), groups.last().records.map { it.episodeId })
    }

    /** 显式时区不同但时间点相同的记录应落在手机的同一个日期组。 */
    @Test
    fun differentOffsetsCanBelongToSameLocalDay() {
        val records = listOf(
            history(1, timestamp("2026-01-01T01:00:00+08:00")),
            history(2, timestamp("2025-12-31T17:00:00Z")),
        )
        assertEquals(1, groupHistoryByDay(records, historyDateContext(records.first().viewedAtMillis!!, shanghai)).size)
    }

    /** 多个标题即使把可见索引推到原始记录数之外，也不能提前触发分页。 */
    @Test
    fun headersNeverTriggerEndDetection() {
        val key = historyRecordKey(history(20, null))
        assertFalse(isHistoryEndVisible(listOf("history:day:2026-01-01", "history:1:18", "history:day:2025-12-31"), key))
        assertTrue(isHistoryEndVisible(listOf("history:day:unknown", key), key))
        assertTrue(isHistoryEndVisible(listOf(HistoryFooterKey), key))
        assertFalse(isHistoryEndVisible(listOf(HistoryFooterKey), null))
    }

    /** 分组重新排序后以真实最后一条的 key 检测，空历史也不产生虚假的触底。 */
    @Test
    fun endDetectionUsesLastRenderedRecord() {
        val records = listOf(history(1, null), history(2, timestamp("2026-01-01 12:00:00")))
        val groups = groupHistoryByDay(records, historyDateContext(timestamp("2026-01-01 13:00:00"), shanghai))
        val key = historyRecordKey(groups.last().records.last())
        assertFalse(isHistoryEndVisible(listOf(historyRecordKey(records.last())), key))
        assertTrue(isHistoryEndVisible(listOf(key), key))
        assertTrue(groupHistoryByDay(emptyList(), historyDateContext(0L, utc)).isEmpty())
    }

    private fun timestamp(raw: String): Long = requireNotNull(parseHistoryTimestamp(raw))

    private fun history(id: Int, viewedAt: Long?): WatchHistory = WatchHistory(
        animeId = 100 + id, animeTitle = "测试番剧", episodeId = id, episodeNumber = 1,
        episodeTitle = "测试剧集", progressSeconds = 10, durationSeconds = 100,
        finished = false, coverUrl = "", viewedAtMillis = viewedAt,
    )
}
