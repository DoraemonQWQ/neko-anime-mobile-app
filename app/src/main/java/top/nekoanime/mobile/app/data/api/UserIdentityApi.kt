package top.nekoanime.mobile.app.data.api

import top.nekoanime.mobile.app.model.dto.AvatarCommitBody
import top.nekoanime.mobile.app.model.dto.AvatarUploadUrlBody
import top.nekoanime.mobile.app.model.dto.AvatarUploadUrlDto
import top.nekoanime.mobile.app.model.dto.UserInfoDto
import top.nekoanime.mobile.app.model.dto.UpdateNicknameBody

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
}
