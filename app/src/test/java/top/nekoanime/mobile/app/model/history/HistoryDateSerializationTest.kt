package top.nekoanime.mobile.app.model.history

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.nekoanime.mobile.app.model.dto.UserHistoryDto
import top.nekoanime.mobile.app.model.entity.WatchHistory

/** 核对线上真实字段与旧页面保存状态，不访问网络或 Android 框架。 */
class HistoryDateSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** 实测响应使用 update_date，并且该字段由后端 LastViewDate 映射而来。 */
    @Test
    fun realHistoryResponseReadsUpdateDate() {
        val dto = json.decodeFromString<UserHistoryDto>(
            """{"anime_id":487630,"episode_id":1440322,"update_date":"2026-10-25 18:36:51","create_date":"2026-10-21 21:30:24"}""",
        )
        assertEquals("2026-10-25 18:36:51", dto.updateDate)
        assertEquals(parseHistoryTimestamp("2026-10-25T10:36:51Z"), parseHistoryTimestamp(dto.updateDate))
    }

    /** 创建日期不是观看日期；字段缺失或 null 时，不能拿创建日期或当天兜底。 */
    @Test
    fun absentAndNullWatchDatesRemainUnknown() {
        assertNull(json.decodeFromString<UserHistoryDto>("""{"create_date":"2026-10-21 21:30:24"}""").updateDate)
        assertNull(json.decodeFromString<UserHistoryDto>("""{"update_date":null}""").updateDate)
    }

    /** 旧记忆状态没有 viewedAtMillis，新增字段必须保留默认值，避免恢复失败。 */
    @Test
    fun oldSavedHistoryRestoresWithUnknownDate() {
        val old = """{"animeId":1,"animeTitle":"测试","episodeId":2,"episodeNumber":1,"episodeTitle":"第一集","progressSeconds":10,"durationSeconds":100,"finished":false,"coverUrl":""}"""
        val restored = json.decodeFromString<WatchHistory>(old)
        assertNull(restored.viewedAtMillis)
        assertEquals("日期未知", historyDayTitle(null, historyDateContext(0L)))
        val dated = restored.copy(viewedAtMillis = 123L)
        assertEquals(dated, json.decodeFromString<WatchHistory>(json.encodeToString(WatchHistory.serializer(), dated)))
    }
}
