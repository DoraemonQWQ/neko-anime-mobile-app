package top.nekoanime.mobile.app.model.history

import top.nekoanime.mobile.app.model.entity.WatchHistory
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 后端历史接口的 update_date 来自 LastViewDate；部署容器已核实 TZ=Asia/Shanghai。
 * 无偏移字符串必须按来源时区解析，不能跟随手机时区，否则换时区后会改变真实观看时间。
 * 使用 java.util 日期 API 以兼容 minSdk 24，无需额外开启 java.time 脱糖。
 */
internal fun parseHistoryTimestamp(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:?\\d{2})?$")
        .matchEntire(text) ?: return null
    val fraction = match.groupValues[3]
    val offset = match.groupValues[4]
    // 精度统一到毫秒；显式 Z/偏移优先，旧接口缺偏移才使用已核实的服务端时区。
    val normalized = buildString {
        append(match.groupValues[1]).append('T').append(match.groupValues[2])
        if (fraction.isNotEmpty()) append('.').append(fraction.padEnd(3, '0').take(3))
        append(if (offset.length == 5) offset.take(3) + ":" + offset.takeLast(2) else offset)
    }
    val pattern = "yyyy-MM-dd'T'HH:mm:ss" +
        (if (fraction.isNotEmpty()) ".SSS" else "") +
        (if (offset.isNotEmpty()) "XXX" else "")
    val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    val position = ParsePosition(0)
    // 日期解析失败保持 null；统一日志入口接入后，可在调用处补充脱敏诊断。
    return formatter.parse(normalized, position)
        ?.takeIf { position.index == normalized.length }
        ?.time
}

/** 用年月日做分组键，避免把不同时区、不同年份的同月同日混成一组。 */
internal data class HistoryLocalDay(val year: Int, val month: Int, val day: Int) : Comparable<HistoryLocalDay> {
    override fun compareTo(other: HistoryLocalDay): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })
}

/** 同一次分组共用今天、昨天与时区，跨午夜时不会出现一部分标题已经更新的情况。 */
internal data class HistoryDateContext(
    val timeZone: TimeZone,
    val today: HistoryLocalDay,
    val yesterday: HistoryLocalDay,
)

internal fun historyLocalDay(timestamp: Long, timeZone: TimeZone): HistoryLocalDay =
    Calendar.getInstance(timeZone, Locale.ROOT).apply { timeInMillis = timestamp }.let {
        HistoryLocalDay(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

internal fun historyDateContext(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): HistoryDateContext {
    // 日历减一天遵循本地日界线与夏令时，不能简单减去固定 24 小时。
    val yesterday = Calendar.getInstance(timeZone, Locale.ROOT).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
    return HistoryDateContext(timeZone, historyLocalDay(nowMillis, timeZone), historyLocalDay(yesterday, timeZone))
}

internal fun historyDayTitle(day: HistoryLocalDay?, context: HistoryDateContext): String = when (day) {
    null -> "日期未知"
    context.today -> "今天"
    context.yesterday -> "昨天"
    else -> "${day.year}年${day.month}月${day.day}日"
}

internal data class HistoryDayGroup(val day: HistoryLocalDay?, val records: List<WatchHistory>)

/** 已知日期倒序排列，未知日期固定放最后；同一天内保持接口的观看时间顺序。 */
internal fun groupHistoryByDay(records: List<WatchHistory>, context: HistoryDateContext): List<HistoryDayGroup> =
    records.groupBy { it.viewedAtMillis?.let { timestamp -> historyLocalDay(timestamp, context.timeZone) } }
        .entries.sortedWith { left, right ->
            val leftDay = left.key
            val rightDay = right.key
            when {
                leftDay == null && rightDay == null -> 0
                leftDay == null -> 1
                rightDay == null -> -1
                else -> rightDay.compareTo(leftDay)
            }
        }.map { HistoryDayGroup(it.key, it.value) }

/** 行 key 与标题、页脚使用不同前缀，分页只认真正的最后一条记录。 */
internal fun historyRecordKey(history: WatchHistory): String = "history:${history.animeId}:${history.episodeId}"

internal const val HistoryFooterKey = "history:footer"

/** 标题也占 LazyColumn 索引，因此禁止拿可见索引与原始记录数比较。 */
internal fun isHistoryEndVisible(visibleKeys: List<Any>, lastRecordKey: String?): Boolean =
    lastRecordKey != null && visibleKeys.any { it == lastRecordKey || it == HistoryFooterKey }
