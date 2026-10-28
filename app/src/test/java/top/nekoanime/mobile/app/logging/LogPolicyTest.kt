package top.nekoanime.mobile.app.logging

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 日志保留期和脱敏属于持久化边界，用固定时钟验证，避免依赖执行当天。 */
class LogPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    /** 七日窗口包含今天和六天前，第八天及未来文件应删除，近期日志内容不变。 */
    @Test
    fun retainsOnlySevenCalendarDaysAcrossYearBoundary() {
        val folder = temporary.newFolder()
        val dates = listOf("2025-12-26", "2025-12-27", "2026-01-01", "2026-01-02", "2026-01-03")
        dates.forEach { java.io.File(folder, "$it.log").writeText("keep-$it") }
        val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))
        pruneLogFiles(folder, clock)
        assertEquals(setOf("2025-12-27.log", "2026-01-01.log", "2026-01-02.log"), folder.list()!!.toSet())
        assertEquals("keep-2025-12-27", java.io.File(folder, "2025-12-27.log").readText())
    }

    /** 保留边界以系统本地日期计算，不能固定按 UTC 日期删除合法日志。 */
    @Test
    fun usesLocalDayForRetention() {
        val folder = temporary.newFolder()
        java.io.File(folder, "2026-01-01.log").writeText("old")
        java.io.File(folder, "2026-01-02.log").writeText("recent")
        val clock = Clock.fixed(Instant.parse("2026-01-08T01:00:00Z"), ZoneId.of("America/Los_Angeles"))
        pruneLogFiles(folder, clock)
        assertTrue(java.io.File(folder, "2026-01-01.log").exists())
        assertTrue(java.io.File(folder, "2026-01-02.log").exists())
    }

    /** 日志不落完整网络地址、Bearer 或密码值，但保留调用点和 HTTP 状态码。 */
    @Test
    fun stripsSensitiveValuesBeforePersisting() {
        val safe = sanitizeLogMessage("HTTP 401 https://host.test/a?token=secret Bearer secret-token password=private")
        assertTrue(safe.contains("HTTP 401"))
        assertFalse(safe.contains("secret"))
        assertFalse(safe.contains("private"))
        assertFalse(safe.contains("host.test"))
        // 相对 OAuth 路径也可能携带一次性凭据，不能仅处理完整 URL。
        assertFalse(sanitizeLogMessage("POST /auth/status?temp_id=oauth-secret").contains("oauth-secret"))
    }
}
