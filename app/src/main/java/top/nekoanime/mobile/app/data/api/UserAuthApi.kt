package top.nekoanime.mobile.app.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import top.nekoanime.mobile.app.config.ConfigLoader

/** 空请求体：状态接口只要查询参数，但客户端方法需要一个序列化器 */
@Serializable
private object EmptyBody

/**
 * Bangumi 注册请求体。
 *
 * @param loginName 登录名（只允许字母、数字、下划线）
 * @param password 密码
 * @param tempId Bangumi 授权回调带回来的临时凭证
 */
@Serializable
private data class BangumiRegisterBody(
    @kotlinx.serialization.SerialName("login_name") val loginName: String,
    val password: String,
    @kotlinx.serialization.SerialName("temp_id") val tempId: String,
)

/**
 * 登录请求体。
 *
 * @param username 用户名
 * @param password 密码
 */
@Serializable
private data class LoginBody(val username: String, val password: String)

/**
 * 修改密码请求体。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 */
@Serializable
private data class ChangePasswordBody(
    @kotlinx.serialization.SerialName("old_password") val oldPassword: String,
    @kotlinx.serialization.SerialName("new_password") val newPassword: String,
)

/**
 * 账号认证接口。
 *
 * @description 后端 `UserAuthController`：登录成功返回 **JWT 字符串**（同时也会写 HttpOnly Cookie）。
 * 移动端把 token 存本地，之后每个请求带 `Authorization: Bearer <token>`。
 */
object UserAuthApi {
    /**
     * 拼 Bangumi 授权地址（交给内置 WebView 打开）。
     *
     * @description 回跳地址用站点自身的 `/oauth/callback`：后端授权完成后会带 `temp_id`
     * 跳回这个地址，App 侧在 WebView 里拦下这个跳转、取出 temp_id 去换 JWT。
     *
     * @param redirectUrl 回调地址（默认站点回调页）
     * @returns 完整的授权入口地址
     */
    fun bangumiLoginUrl(redirectUrl: String = "https://www.nekoanime.top/oauth/callback"): String {
        val base = ConfigLoader.config.api.baseUrl.trimEnd('/')
        val encoded = java.net.URLEncoder.encode(redirectUrl, "UTF-8")

        return "$base${ApiRoutes.BANGUMI_LOGIN}?redirect_url=$encoded"
    }

    /**
     * 用 temp_id 换 JWT（Bangumi 授权完成后调用）。
     *
     * @param tempId WebView 拦下来的临时凭证
     * @returns 成功是 JWT token；账号未绑定/授权过期等会失败
     */
    suspend fun bangumiLoginStatus(tempId: String): Result<String> = ApiClient.postText(
        path = "${ApiRoutes.BANGUMI_LOGIN_STATUS}?temp_id=$tempId",
        body = EmptyBody,
        bodySerializer = EmptyBody.serializer(),
    )

    /**
     * 用 Bangumi 临时凭证注册并绑定。
     *
     * @description 服务端一次做完三件事：建号 → 绑定该 Bangumi 账号 →（新账号）用 Bangumi 的昵称头像初始化。
     * 成功后调用方还需要用登录名+密码登录一次拿 JWT。
     *
     * @param username 登录名（只允许字母、数字、下划线）
     * @param password 密码
     * @param tempId Bangumi 授权回调带回来的临时凭证
     * @returns 成功/失败（失败时 message 是后端的原因，例如"该 Bangumi 账号已绑定了"）
     */
    suspend fun bangumiRegister(username: String, password: String, tempId: String): Result<Unit> = ApiClient.postUnit(
        path = ApiRoutes.BANGUMI_REGISTER,
        body = BangumiRegisterBody(loginName = username, password = password, tempId = tempId),
        bodySerializer = BangumiRegisterBody.serializer(),
    )

    /**
     * 登录。
     *
     * @param username 用户名
     * @param password 密码
     * @returns 成功是 JWT token
     */
    suspend fun login(username: String, password: String): Result<String> = ApiClient.postText(
        path = ApiRoutes.USER_LOGIN,
        body = LoginBody(username = username, password = password),
        bodySerializer = LoginBody.serializer(),
    )

    /**
     * 修改密码（登录用户自己改；管理员改别人的走后台接口）。
     *
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @returns 成功/失败
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> = ApiClient.postUnit(
        path = ApiRoutes.USER_CHANGE_PASSWORD,
        body = ChangePasswordBody(oldPassword = oldPassword, newPassword = newPassword),
        bodySerializer = ChangePasswordBody.serializer(),
    )
}
