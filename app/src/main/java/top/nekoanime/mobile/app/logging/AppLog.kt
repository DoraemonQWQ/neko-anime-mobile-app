package top.nekoanime.mobile.app.logging

import android.content.Context
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors

/** 日志级别由项目定义，业务代码不引用任何第三方日志类型。 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** 日志后端的最小契约；替换日志库时只需替换实现，业务调用保持不变。 */
interface LogBackend {
    fun write(level: LogLevel, tag: String, message: String)
    fun flush()
    fun clear()
    fun prune()
    fun sizeBytes(): Long
    fun openViewer(context: Context)
}

/** 全站日志入口，写入、保留期清理和清缓存共用串行队列，避免旧队列在清除后复活文件。 */
object AppLog {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "neko-log").apply { isDaemon = true } }
    @Volatile private var backend: LogBackend? = null
    private var initialized = false
    private val viewers = mutableListOf<WeakReference<Activity>>()

    /** 在应用进程启动时排队初始化，早期配置与登录日志会在初始化之后顺序写入。 */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val application = context.applicationContext
        // 跟踪查看页以便清缓存时销毁其旧内容快照，防止从最近任务恢复后再次查看/分享已删日志。
        (application as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is LogFilesActivity || activity.javaClass.name.startsWith("com.v.log.inspector.")) {
                    synchronized(viewers) { viewers.add(WeakReference(activity)) }
                }
            }
            override fun onActivityDestroyed(activity: Activity) {
                synchronized(viewers) { viewers.removeAll { it.get() == null || it.get() === activity } }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
        worker.execute { runCatching { backend = VlogBackend(application) } }
    }

    /** 后端替换也经过同一队列，先刷完旧后端，确保切换边界之前的记录不丢失。 */
    fun replaceBackend(value: LogBackend) {
        worker.execute {
            runCatching { backend?.flush() }
            backend = value
        }
    }

    fun d(tag: String, message: String) = record(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = record(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = record(LogLevel.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = record(LogLevel.ERROR, tag, message, error)

    /** 统一脱敏后再进入日志库；异常仅记录类型，避免底层异常携带令牌、上传地址或用户输入。 */
    private fun record(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        val safe = sanitizeLogMessage(message + (error?.let { " [${it.javaClass.simpleName}]" } ?: ""))
        worker.execute { runCatching { backend?.write(level, tag, safe) } }
    }

    /** 生命周期补刷不阻塞主线程；库内部待写任务会在本队列后端同步完成。 */
    fun flushAsync() { worker.execute { runCatching { backend?.flush() } } }

    /** 前台恢复时补做保留期检查，覆盖长时间后台、系统改时钟及跨午夜场景。 */
    fun pruneAsync() { worker.execute { runCatching { backend?.prune() } } }

    /** 等待清除之前的写入落盘，再关闭文件并清空磁盘与查看器内存快照。 */
    suspend fun clear() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            synchronized(viewers) {
                viewers.mapNotNull { it.get() }.forEach { it.finish() }
                viewers.clear()
            }
        }
        onWorker { backend?.clear() }
    }
    suspend fun sizeBytes(): Long = onWorker { backend?.sizeBytes() ?: 0L }

    /** 打开入口先刷盘和清理过期文件，查看页由适配器提供，设置页无需了解 VLog。 */
    suspend fun openViewer(context: Context) = onWorker {
        backend?.let { it.flush(); it.prune(); it.openViewer(context) }
    }

    /** 把维护操作排到写日志队列后面，异常传回调用方显示失败提示而不是假报清理成功。 */
    private suspend fun <T> onWorker(block: () -> T): T {
        val result = CompletableDeferred<T>()
        worker.execute {
            runCatching(block).onSuccess { result.complete(it) }.onFailure { result.completeExceptionally(it) }
        }
        return result.await()
    }
}

/** 去掉完整网络地址和常见凭据字段；日志保留接口路径、状态码、对象 ID 等排障信息。 */
internal fun sanitizeLogMessage(value: String): String = value
    .replace(Regex("https?://[^\\s\\\"<>]+"), "[地址已省略]")
    .replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [已隐藏]")
    .replace(Regex("(?i)(password|jwt_token|authToken|access_token|refresh_token|temp_id|token|authorization)([\\\"'\\s:=]+)[^\\s,;}]+"), "$1$2[已隐藏]")
    // 相对接口路径也可能携带 OAuth 临时凭据；查询串和片段整体移除，避免遗漏新的参数名。
    .replace(Regex("(/[A-Za-z0-9_./-]+)[?#][^\\s]+"), "$1?[参数已隐藏]")
    .take(4000)
