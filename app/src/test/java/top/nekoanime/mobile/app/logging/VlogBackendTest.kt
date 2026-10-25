package top.nekoanime.mobile.app.logging

import android.app.Application
import com.v.log.VLog
import com.v.log.config.ConfigCenter
import com.v.log.inspector.LogInspectorStore
import com.v.log.io.LightLog
import com.v.log.util.DateUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** 使用真实 VLog 文件后端验证落盘、清除和系统时区变化，不替换打印器或磁盘实现。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VlogBackendTest {
    private lateinit var directory: File
    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalDateFormat: Any
    private lateinit var originalConfig: Map<Field, Any?>
    private lateinit var backend: VlogBackend

    /** 用普通 Application 避免应用启动自动初始化 AppLog，且清空第三方静态状态后才创建真实后端。 */
    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        originalDateFormat = dateFormatField().get(null)
        val config = ConfigCenter.getInstance()
        originalConfig = ConfigCenter::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .associateWith { field -> field.isAccessible = true; field.get(config) }
        directory = File(RuntimeEnvironment.getApplication().filesDir, "logs")

        // 先完成旧库队列，再移除打印器并关闭单例句柄；不依赖测试顺序或上个用例的容量缓存。
        resetLibraryState()
        assertTrue("测试日志目录无法清空", !directory.exists() || directory.deleteRecursively())
        assertTrue(directory.mkdirs() || directory.isDirectory)
        resetDateFormatterToDefaultZone()
        backend = VlogBackend(RuntimeEnvironment.getApplication())
    }

    /** 所有异步写入收尾后释放文件，恢复默认时区、静态日期格式器及配置，避免污染其他测试。 */
    @After
    fun tearDown() {
        try {
            resetLibraryState()
            if (::directory.isInitialized) {
                assertTrue("测试日志目录清理失败", !directory.exists() || directory.deleteRecursively())
            }
        } finally {
            if (::originalTimeZone.isInitialized) TimeZone.setDefault(originalTimeZone)
            if (::originalDateFormat.isInitialized) dateFormatField().set(null, originalDateFormat)
            if (::originalConfig.isInitialized) {
                val config = ConfigCenter.getInstance()
                originalConfig.forEach { (field, value) -> field.set(config, value) }
            }
        }
    }

    /** 覆盖四个真实级别入口，flush 返回后当天日志文件必须已经包含每个唯一标记。 */
    @Test
    fun writeThenFlushPersistsMarkersInTodayFile() {
        val markers = LogLevel.entries.associateWith { marker("flush-${it.name}") }
        markers.forEach { (level, value) -> backend.write(level, "VlogBackendTest", value) }

        // 只使用后端公开 flush 等待库的串行队列与文件强制写入，不用 sleep 或读取内存快照。
        backend.flush()
        val todayFile = File(directory, "${LocalDate.now()}.log")
        assertTrue("flush 后应存在当天日志文件", todayFile.isFile)
        val diskText = todayFile.readText()
        markers.values.forEach { value -> assertTrue("磁盘日志缺少 $value", diskText.contains(value)) }
        assertTrue("落盘后日志大小应大于零", backend.sizeBytes() > 0)
    }

    /** 清除同时覆盖尚在库队列中的日志；后续 flush 不得恢复旧标记，且文件可重新创建并写入。 */
    @Test
    fun clearDiscardsOldRecordsAndAllowsNewWrites() {
        val oldMarker = marker("before-clear")
        val newMarker = marker("after-clear")
        backend.write(LogLevel.INFO, "VlogBackendTest", oldMarker)
        backend.flush()
        assertTrue(logFiles().any { it.readText().contains(oldMarker) })

        // 不预先 flush 这一批记录，让 clear 自己处理队列屏障和打开中的文件句柄。
        repeat(12) { backend.write(LogLevel.DEBUG, "VlogBackendTest", "$oldMarker-queued-$it") }
        backend.clear()
        backend.flush()
        assertEquals("clear 后 flush 不应复活旧文件", 0L, backend.sizeBytes())
        assertTrue("clear 后不应残留日志文件", logFiles().isEmpty())

        // 清除后重新写入并多次维护，检查旧缓存不会随下一次写入或保留期检查重新落盘。
        backend.write(LogLevel.WARN, "VlogBackendTest", newMarker)
        backend.flush()
        backend.prune()
        backend.flush()
        val text = logFiles().joinToString("\n") { it.readText() }
        assertTrue("清除后应能继续写入新日志", text.contains(newMarker))
        assertFalse("清除前的日志不应重新出现", text.contains(oldMarker))
    }

    /** 初始化时的库静态时区保持不变，系统默认时区跨日后必须仍写入新的本地日期文件。 */
    @Test
    fun timezoneDayChangeUsesExplicitCurrentFilenameWithinSevenDayWindow() {
        val earlierZone = TimeZone.getTimeZone("GMT-12:00")
        val laterZone = TimeZone.getTimeZone("GMT+14:00")
        TimeZone.setDefault(earlierZone)
        resetDateFormatterToDefaultZone()
        val earlierDay = LocalDate.now()
        val earlierMarker = marker("earlier-zone")
        backend.write(LogLevel.INFO, "VlogBackendTest", earlierMarker)
        backend.flush()
        assertTrue(File(directory, "$earlierDay.log").readText().contains(earlierMarker))

        // 两个固定偏移相差 26 小时，无需依赖测试运行时刻也一定跨日；刻意不重建库日期格式器。
        TimeZone.setDefault(laterZone)
        val today = LocalDate.now()
        assertNotEquals("时区切换应跨越本地日期", earlierDay, today)
        assertEquals("库静态日期缓存应仍停留在旧时区", earlierDay.toString(), DateUtil.getCurrentDate())
        val marker = marker("later-zone")
        backend.write(LogLevel.ERROR, "VlogBackendTest", marker)
        backend.flush()
        val currentFile = File(directory, "$today.log")
        assertTrue("必须显式写入当前本地日期文件", currentFile.isFile)
        assertTrue("新时区日志不能误写到库缓存的旧日期", currentFile.readText().contains(marker))
        assertFalse(File(directory, "$earlierDay.log").readText().contains(marker))

        // 在真实日志目录放入第七/第八天及未来样本，调用真实后端维护后逐个核对七日窗口。
        val firstDay = today.minusDays(6)
        File(directory, "$firstDay.log").writeText("keep-boundary")
        File(directory, "${today.minusDays(7)}.log").writeText("expired")
        File(directory, "${today.plusDays(1)}.log").writeText("future")
        backend.prune()
        backend.flush()
        assertTrue(File(directory, "$firstDay.log").exists())
        assertFalse(File(directory, "${today.minusDays(7)}.log").exists())
        assertFalse(File(directory, "${today.plusDays(1)}.log").exists())
        logFiles().forEach { file ->
            val day = LocalDate.parse(file.name.removeSuffix(".log"))
            assertTrue("文件 ${file.name} 超出当前七日窗口", !day.isBefore(firstDay) && !day.isAfter(today))
        }
        assertTrue("保留期维护不能删除当前日期的新日志", currentFile.readText().contains(marker))
    }

    /** 只读取应用私有目录中的实际日志文件，拒绝用查看器的内存快照代替持久化验证。 */
    private fun logFiles(): List<File> = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "log" }

    /** 为每个断言生成独立文本，排除前一个用例或系统启动日志误满足条件。 */
    private fun marker(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    /** 反射仅用于隔离库的静态资源：先 flush 再关闭句柄，随后移除单例以清掉写入容量与日期缓存。 */
    private fun resetLibraryState() {
        VLog.flush()
        VLog.clearLogPrinters()
        LogInspectorStore.clear()
        LogInspectorStore.setEnabled(false)
        val field = LightLog::class.java.getDeclaredField("sLightLog").apply { isAccessible = true }
        val instance = field.get(null)
        if (instance != null) {
            synchronized(instance) {
                LightLog::class.java.getDeclaredMethod("closeLogWriter").apply { isAccessible = true }.invoke(instance)
                field.set(null, null)
            }
        }
    }

    /** 给库建立初始化时区缓存；跨时区写入测试在切换默认时区后不会再次调用此函数。 */
    private fun resetDateFormatterToDefaultZone() {
        dateFormatField().set(null, SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        })
    }

    /** 定位库内部的静态格式器，仅在 setup/teardown 和跨时区前置条件中使用。 */
    private fun dateFormatField(): Field = DateUtil::class.java.getDeclaredField("sDateFormat").apply {
        isAccessible = true
    }
}
