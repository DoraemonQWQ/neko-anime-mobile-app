package top.nekoanime.mobile.app.data.api

import top.nekoanime.mobile.app.model.dto.AvatarCommitBody
import top.nekoanime.mobile.app.model.dto.AvatarUploadUrlBody
import top.nekoanime.mobile.app.model.dto.AvatarUploadUrlDto
import top.nekoanime.mobile.app.model.dto.UserInfoDto
import top.nekoanime.mobile.app.model.dto.UpdateNicknameBody
import top.nekoanime.mobile.app.model.dto.BangumiBindingDto
import kotlinx.serialization.Serializable

/** 空请求体（解绑接口不需要参数，但客户端方法要一个序列化器） */
@Serializable
private object UnbindBody

/**
 * 观看进度上报请求体（与后端 `UserUpdateViewEpisodeProgressRequest` 对齐）。
 *
 * @param progress 已观看秒数
 * @param viewDate 本次观看时间（ISO 字符串）
 */
@Serializable
private data class ProgressBody(
    val progress: Int,
    @kotlinx.serialization.SerialName("view_date") val viewDate: String,
)

/**
 * 用户身份接口（需要登录）。
 *
 * @description 对应后端 `UserIdentityController`（路由前缀是 `/api/user/data`，跟观看历史同一个前缀）：
 * 取用户资料、头像直传（申请地址 → PUT 上传 → 提交）、Bangumi 绑定（后面再接）。
 */
object UserIdentityApi {
    /**
     * 取当前登录用户的资料（顶栏头像、用户页都用它）。
     *
     * @returns 用户资料
     */
    suspend fun info(): Result<UserInfoDto> = ApiClient.get(
        path = ApiRoutes.USER_INFO,
        serializer = UserInfoDto.serializer(),
    )

    /**
     * 头像直传第一步：申请直传地址。
     *
     * @param fileSize 文件大小（字节）
     * @param fileMd5 文件内容 MD5
     * @param extension 扩展名（含点）
     * @returns 对象 key + 直传地址
     */
    suspend fun requestAvatarUpload(
        fileSize: Long,
        fileMd5: String,
        extension: String,
    ): Result<AvatarUploadUrlDto> = ApiClient.post(
        path = ApiRoutes.USER_AVATAR_UPLOAD_URL,
        body = AvatarUploadUrlBody(fileSize = fileSize, fileMd5 = fileMd5, extension = extension),
        bodySerializer = AvatarUploadUrlBody.serializer(),
        serializer = AvatarUploadUrlDto.serializer(),
    )

    /**
     * 头像直传第三步：提交（直传成功后写库）。
     *
     * @param key 申请时返回的对象 key
     * @returns 只有成功/失败
     */
    suspend fun commitAvatar(key: String): Result<Unit> = ApiClient.postUnit(
        path = ApiRoutes.USER_AVATAR_COMMIT,
        body = AvatarCommitBody(key = key),
        bodySerializer = AvatarCommitBody.serializer(),
    )

    /**
     * 修改昵称（只改本地 user_profile.nickname，不同步 Bangumi）。
     *
     * @param nickname 新昵称
     * @returns 更新后的用户资料
     */
    suspend fun updateNickname(nickname: String): Result<UserInfoDto> = ApiClient.post(
        path = ApiRoutes.USER_NICKNAME,
        body = UpdateNicknameBody(nickname = nickname),
        bodySerializer = UpdateNicknameBody.serializer(),
        serializer = UserInfoDto.serializer(),
    )

    /**
     * 取 Bangumi 绑定状态。
     *
     * @returns 是否已绑定 + Bangumi 昵称头像
     */
    suspend fun bangumiBinding(): Result<BangumiBindingDto> = ApiClient.get(
        path = ApiRoutes.USER_BANGUMI_BINDING,
        serializer = BangumiBindingDto.serializer(),
    )

    /**
     * 取"发起绑定"用的 Bangumi 授权地址。
     *
     * @description 后端那个接口是 GET + 302（同时需要登录态），浏览器直接导航带不上 Authorization，
     * 所以这里由 App 带着 token 请求一次，只取回 Location（Bangumi 授权页地址），再交给 WebView 打开。
     *
     * @param redirectUrl 授权完成后的回跳地址
     * @returns Bangumi 授权页地址
     */
    suspend fun bangumiBindUrl(redirectUrl: String = "https://www.nekoanime.top/user"): Result<String> =
        ApiClient.getRedirectLocation(
            path = ApiRoutes.USER_BANGUMI_BIND,
            query = mapOf("redirect_url" to redirectUrl),
        )

    /**
     * 解绑 Bangumi。
     *
     * @returns 成功/失败
     */
    suspend fun unbindBangumi(): Result<Unit> = ApiClient.postUnit(
        path = ApiRoutes.USER_BANGUMI_UNBIND,
        body = UnbindBody,
        bodySerializer = UnbindBody.serializer(),
    )

    /**
     * 上报某一集的观看进度（服务端的「继续观看 / 观看历史」靠它更新）。
     *
     * @param episodeId 剧集ID
     * @param progressSeconds 已观看秒数
     * @returns 成功/失败
     */
    suspend fun reportProgress(episodeId: Int, progressSeconds: Int): Result<Unit> = ApiClient.postUnit(
        path = ApiRoutes.userEpisodeProgress(episodeId),
        body = ProgressBody(
            progress = progressSeconds,
            viewDate = java.time.OffsetDateTime.now().toString(),
        ),
        bodySerializer = ProgressBody.serializer(),
    )
}
