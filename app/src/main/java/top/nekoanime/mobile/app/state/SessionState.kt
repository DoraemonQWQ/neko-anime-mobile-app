package top.nekoanime.mobile.app.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.nekoanime.mobile.app.data.local.UserSession
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.model.entity.UserProfile
import top.nekoanime.mobile.app.logging.AppLog as Log

/**
 * 全局登录态。
 *
 * @description 跨页面共享的运行时状态放这里：首页顶栏要看它决定显示「登录按钮」还是「头像」，
 * 播放页要看它决定追番按钮能不能点。数据本身由 {@link UserSession} 负责落盘。
 *
 * 用法：Activity 启动时调一次 {@link init}，之后各页面直接读 {@link loggedIn}。
 */
object SessionState {
    private var store: UserSession? = null

    /** 是否已登录（Compose 状态，登录/退出后界面会自动刷新） */
    var loggedIn by mutableStateOf(false)
        private set

    /** 当前登录用户的资料（顶栏头像、用户页展示用）；未登录为 null */
    var profile by mutableStateOf<UserProfile?>(null)
        private set

    /**
     * 拉一次用户资料：登录成功后、应用启动时（本机有 token）各调一次。
     *
     * @description 失败只打日志、不改变登录状态（顶栏退化成默认头像）。
     */
    suspend fun refreshProfile() {
        if (!loggedIn) {
            profile = null
            return
        }

        UserRepository.info().onSuccess { profile = it }
    }

    /**
     * 用接口返回的资料直接刷新本地状态。
     *
     * @description 改昵称 / 换头像的接口都会把更新后的资料一起返回，
     * 直接拿来更新即可，不用再多请求一次 `/api/user/data/info`。
     *
     * @param value 最新的用户资料
     */
    fun applyProfile(value: UserProfile) {
        profile = value
    }

    /**
     * 初始化：读本机 token。
     *
     * @param context 上下文
     */
    fun init(context: Context) {
        val session = UserSession(context)
        store = session
        loggedIn = session.restoreToken() != null
        Log.i("NekoSession", if (loggedIn) "已恢复本地登录态" else "当前未登录")
        // 新进程或重新初始化会话时丢弃旧历史，首页恢复可见后再按当前账号读取。
        ContinueWatchingState.clear()
    }

    /**
     * 登录成功后保存。
     *
     * @param token 登录接口返回的 JWT
     */
    suspend fun onLoggedIn(token: String) {
        // 仅记录会话事件，不记录 JWT 或用户资料字段。
        Log.i("NekoSession", "登录成功，建立新会话")
        store?.saveToken(token)
        // 登录建立新会话，先隔离旧请求，再立即加载该账号的继续观看，不等待头像资料接口。
        ContinueWatchingState.clear()
        loggedIn = true
        ContinueWatchingState.refresh()
        refreshProfile()
    }

    /**
     * 退出登录。
     */
    fun logout() {
        // 退出与继续观看清理作为同一业务边界记录，不输出用户身份信息。
        Log.i("NekoSession", "退出登录，清除会话数据")
        store?.clear()
        loggedIn = false
        // 与登录态同步清空历史，即使首页 Tab 已离开组合，也不会保留可恢复的账号数据。
        ContinueWatchingState.clear()
        profile = null
    }
}
