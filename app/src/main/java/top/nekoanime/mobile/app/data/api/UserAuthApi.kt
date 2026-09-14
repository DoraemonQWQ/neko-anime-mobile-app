package top.nekoanime.mobile.app.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

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
