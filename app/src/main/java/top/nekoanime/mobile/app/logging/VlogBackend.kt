package top.nekoanime.mobile.app.logging

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.v.log.VLog
import com.v.log.VLogConfig
import com.v.log.inspector.LogInspectorStore
import com.v.log.io.LightLog
import java.io.File
import java.time.Clock
import java.time.LocalDate

/** 唯一了解 VLog API 的适配器；文件放应用私有目录，避免随系统缓存回收而意外丢失。 */
internal class VlogBackend(context: Context) : LogBackend {
    private val application = context.applicationContext
    private val directory = File(application.filesDir, "logs").apply { mkdirs() }
    private var lastCleanupDay: LocalDate? = null

    init {
        // 使用库自带持久化与查看页；不启用通知栏入口，避免为了查看日志额外申请通知权限。
        // 保留期由本适配器精确控制；关闭库的容量熔断，避免清缓存后其旧容量状态继续阻止写入。
        // 库内部仅作宽松兜底，实际可见和保留窗口始终由下方七日规则清理。
        val config = VLogConfig(application).setLogPath(directory.absolutePath).maxKeepDaily(30)
            .maxLogSizeMb(Double.MAX_VALUE).setEnableLogInspector(false)
        config.setSaveLog(true)
        config.setShowLog(true)
        config.setShowDetailedLog(false)
        config.setBeautifyLog(false)
        VLog.init(config)
        prune()
    }

    /** 显式开启文件保存；在每个本地日期第一次写入前补清理，避免仅靠库的日期清理边界。 */
    override fun write(level: LogLevel, tag: String, message: String) {
        val today = LocalDate.now()
        if (today != lastCleanupDay) prune()
        // 显式提供按当前系统时区生成的文件名，避免 VLog 静态日期格式缓存旧时区。
        val file = "$today.log"
        when (level) {
            LogLevel.DEBUG -> VLog.d(tag, true, true, false, false, message, file)
            LogLevel.INFO -> VLog.i(tag, true, true, false, false, message, file)
            LogLevel.WARN -> VLog.w(tag, true, true, false, false, message, file)
            LogLevel.ERROR -> VLog.e(tag, true, true, false, false, message, file)
        }
    }

    override fun flush() = VLog.flush()

    /** 串行队列内先完成库的写入，再关闭活动文件句柄并删除，下一条日志会正常新建文件。 */
    override fun clear() {
        flush()
        synchronized(LightLog.newInstance()) {
            LightLog.newInstance().init(directory.absolutePath, Double.MAX_VALUE, 30)
            directory.listFiles()?.forEach { file -> check(file.deleteRecursively()) { "日志文件清理失败" } }
        }
        // 查看器的实时快照与分享临时文件也属于日志数据，一起清除，避免仍能看到旧内容。
        LogInspectorStore.clear()
        File(application.cacheDir, "vlog_share").takeIf { it.exists() }?.let {
            check(it.deleteRecursively()) { "日志分享缓存清理失败" }
        }
        lastCleanupDay = LocalDate.now()
    }

    /** 保留今天及之前六个本地日期；未来日期和非日志文件也不作为有效历史保留。 */
    override fun prune() {
        flush()
        pruneLogFiles(directory, Clock.systemDefaultZone())
        lastCleanupDay = LocalDate.now()
    }

    override fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    /** 从设置进入磁盘日志列表，重启应用后仍能查看；Activity 启动始终切回主线程。 */
    override fun openViewer(context: Context) {
        Handler(Looper.getMainLooper()).post {
            context.startActivity(Intent(context, LogFilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** 独立保留期函数便于验证第七/第八天、跨年及系统时间变化，清理仅限已配置日志目录。 */
internal fun pruneLogFiles(directory: File, clock: Clock) {
    val today = LocalDate.now(clock)
    val firstDay = today.minusDays(6)
    directory.listFiles()?.filter { it.isFile }?.forEach { file ->
        val date = runCatching { LocalDate.parse(file.name.removeSuffix(".log")) }.getOrNull()
        if (date == null || date.isBefore(firstDay) || date.isAfter(today)) {
            check(file.delete()) { "过期日志清理失败" }
        }
    }
}
