package top.nekoanime.mobile.app.data.local

import android.content.Context
import androidx.core.content.edit
import top.nekoanime.mobile.app.data.api.ApiClient

/**
 * 登录态的本机存储。
 *
 * @description 只存一个 JWT：登录成功写进来，启动时读出来塞给 {@link ApiClient}。
 * token 失效（401）时由上层清掉重新登录——目前登录接口返回的 token 有效期不短，
 * 等后面接「jwt 刷新」时再在这里加过期时间与 refresh 逻辑。
 *
 * @param context 上下文（内部只拿 applicationContext）
 */
class UserSession(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /**
     * 读本机保存的 token，并同步给接口客户端。
     *
     * @returns token；没登录返回 null
     */
    fun restoreToken(): String? {
        val token = prefs.getString(KeyToken, null)
        ApiClient.authToken = token

        return token
    }

    /**
     * 保存登录态。
     *
     * @param token 登录接口返回的 JWT
     */
    fun saveToken(token: String) {
        prefs.edit { putString(KeyToken, token) }
        ApiClient.authToken = token
    }

    /**
     * 退出登录：清掉本机 token 与接口客户端的鉴权信息。
     */
    fun clear() {
        prefs.edit { remove(KeyToken) }
        ApiClient.authToken = null
    }

    private companion object {
        /** 配置文件名 */
        const val PrefsName = "neko_user_session"

        /** token 的 key */
        const val KeyToken = "jwt_token"
    }
}
