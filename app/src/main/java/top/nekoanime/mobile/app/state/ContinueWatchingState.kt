package top.nekoanime.mobile.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.api.UserIdentityApi
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.model.entity.WatchHistory
import top.nekoanime.mobile.app.logging.AppLog as Log

/**
 * 当前登录会话的继续观看数据，不写入页面的可保存状态，避免切换 Tab 后恢复已退出账号的历史。
 * 请求与进度上报使用应用级作用域，离开播放页后仍能完成最后一次上报和历史刷新。
 */
object ContinueWatchingState {
    // 所有状态写入都在主线程执行；单次网络失败不会取消后续的历史刷新或进度上报。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // 按调用顺序保存进度，避免慢的定时上报晚于退出补报写入服务端、把最新位置覆盖回旧值。
    private val progressMutex = Mutex()

    /** 首页读取的完整历史快照；只允许本对象整体替换，避免界面看到清空再逐条追加的中间状态。 */
    var items by mutableStateOf<List<WatchHistory>>(emptyList())
        private set

    // 会话代数用于隔离退出前的请求；请求序号保证并发刷新时只有最后发起的一次可以回填。
    private var sessionGeneration = 0L
    private var refreshGeneration = 0L

    /** 退出登录时同步清空，并让所有仍在途中的历史响应和进度上报结果失效。 */
    fun clear() {
        // 会话清理次数与代数可帮助确认迟到请求是否被隔离，不关联具体账号。
        Log.i("NekoContinue", "清空继续观看并失效旧请求")
        sessionGeneration += 1
        refreshGeneration += 1
        items = emptyList()
    }

    /** 首页恢复可见、登录成功或手动下拉时重新读取历史；未登录时直接清空且不请求接口。 */
    fun refresh() {
        if (!SessionState.loggedIn) {
            clear()
            return
        }

        val session = sessionGeneration
        val request = ++refreshGeneration
        Log.d("NekoContinue", "刷新继续观看 request=$request")
        scope.launch {
            val result = UserRepository.history(limit = ConfigLoader.config.home.continueWatchingMax)
            val latest = result.getOrDefault(emptyList())
            result.exceptionOrNull()?.let { Log.w("NekoContinue", "刷新继续观看失败 request=$request", it) }
            // 退出后再登录也不能接收上个会话的结果；被更新请求取代的旧响应同样丢弃。
            if (SessionState.loggedIn && session == sessionGeneration && request == refreshGeneration) {
                items = latest
                Log.d("NekoContinue", "继续观看已刷新 count=${latest.size} request=$request")
            } else {
                // 明确记录丢弃原因上下文，避免误以为旧响应覆盖了当前会话数据。
                Log.d("NekoContinue", "忽略过期继续观看响应 request=$request")
            }
        }
    }

    /** 上报不依赖播放页存活；服务端保存成功后再拉历史，避免返回首页时读到上报前的进度。 */
    fun reportProgress(episodeId: Int, seconds: Int) {
        if (!SessionState.loggedIn || seconds <= 0) return

        val session = sessionGeneration
        scope.launch {
            progressMutex.withLock {
                // 排队期间可能已退出或切换账号，发请求之前再次校验，避免使用新账号令牌上报旧进度。
                if (!SessionState.loggedIn || session != sessionGeneration) return@withLock
                Log.d("NekoContinue", "保存播放进度 episode=$episodeId seconds=$seconds")
                val result = UserIdentityApi.reportProgress(episodeId, seconds)
                result.exceptionOrNull()?.let { Log.w("NekoContinue", "保存播放进度失败 episode=$episodeId", it) }
                // 上报成功才更新历史，且退出登录后的旧上报不能触发新账号的刷新。
                if (result.isSuccess && SessionState.loggedIn && session == sessionGeneration) {
                    refresh()
                }
            }
        }
    }
}
