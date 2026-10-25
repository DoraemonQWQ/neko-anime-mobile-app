package top.nekoanime.mobile.app.data.repository

import top.nekoanime.mobile.app.data.api.ApiClient
import top.nekoanime.mobile.app.data.api.UserApi
import top.nekoanime.mobile.app.data.api.UserAuthApi
import top.nekoanime.mobile.app.data.api.UserIdentityApi
import top.nekoanime.mobile.app.model.dto.UserHistoryDto
import top.nekoanime.mobile.app.model.dto.UserInfoDto
import top.nekoanime.mobile.app.model.entity.UserProfile
import top.nekoanime.mobile.app.model.entity.WatchHistory
import java.security.MessageDigest

/**
 * 用户数据仓库（历史、进度、追番…）。
 *
 * @description 这些接口都需要登录；未登录时后端会返回 401，这里原样把失败交给调用方，
 * 页面据此把「继续观看」这类模块隐藏掉即可（跟 web 的处理一致）。
 */
object UserRepository {
    /**
     * 观看历史（首页「继续观看」）。
     *
     * @param page 第几页
     * @param limit 每页几条
     * @returns 历史记录列表
     */
    suspend fun history(page: Int = 1, limit: Int = 12): Result<List<WatchHistory>> =
        UserApi.history(page = page, limit = limit).map { dto -> dto.data.map { it.toHistory() } }

    /**
     * 取某个番剧的追番状态（播放页的追番按钮）。
     *
     * @param animeId 番剧 id
     * @returns 是否已追番
     */
    suspend fun subscribed(animeId: Int): Result<Boolean> = UserApi.subscribed(animeId)

    /**
     * 修改某个番剧的追番状态。
     *
     * @param animeId 番剧 id
     * @param status true = 追番，false = 取消追番
     * @returns 后端返回的最终状态
     */
    suspend fun updateSubscribed(animeId: Int, status: Boolean): Result<Boolean> =
        UserApi.updateSubscribed(animeId, status)

    /**
     * 取当前登录用户的资料（顶栏头像、用户页）。
     *
     * @returns 用户资料
     */
    suspend fun info(): Result<UserProfile> = UserIdentityApi.info().map { it.toProfile() }

    /**
     * 修改密码（登录用户改自己的）。
     *
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @returns 只有成功/失败
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
        UserAuthApi.changePassword(oldPassword, newPassword)

    /**
     * 修改昵称（user_profile.nickname）。
     *
     * @param nickname 新昵称
     * @returns 更新后的用户资料
     */
    suspend fun updateNickname(nickname: String): Result<UserProfile> =
        UserIdentityApi.updateNickname(nickname).map { it.toProfile() }

    /**
     * 更换头像：直传三段式（申请地址 → PUT 上传 → 提交写库）。
     *
     * @param bytes 裁剪后的图片字节
     * @param extension 扩展名（含点）
     * @returns 只有成功/失败
     */
    suspend fun uploadAvatar(bytes: ByteArray, extension: String = ".jpg"): Result<Unit> = runCatching {
        val md5 = md5Hex(bytes)
        val target = UserIdentityApi
            .requestAvatarUpload(fileSize = bytes.size.toLong(), fileMd5 = md5, extension = extension)
            .getOrThrow()

        // 圆形裁剪导出的是带透明角的 PNG，内容类型要跟着扩展名走，否则部分 CDN 会按 jpeg 处理
        val contentType = if (extension.equals(".png", ignoreCase = true)) "image/png" else "image/jpeg"

        ApiClient.putBytes(target.uploadUrl, bytes, contentType).getOrThrow()
        UserIdentityApi.commitAvatar(target.key).getOrThrow()
    }
}

/**
 * 文件内容 MD5（小写 32 位）。头像对象 key 用它，同一张图重复上传只占一份存储。
 *
 * @param bytes 文件内容
 * @returns 小写十六进制 MD5
 */
private fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * 用户资料 DTO → 界面实体。
 *
 * @returns 用户资料
 */
private fun UserInfoDto.toProfile(): UserProfile = UserProfile(
    id = id,
    username = username,
    nickname = nickname,
    avatarUrl = avatar.ifBlank { null },
    // 后端 role 是枚举序号：0 = 普通用户，1 = 管理员
    role = if (role == 1) "admin" else "user",
)

/**
 * 历史 DTO → 界面模型。
 *
 * @returns 观看历史
 */
private fun UserHistoryDto.toHistory(): WatchHistory = WatchHistory(
    animeId = animeId,
    animeTitle = animeTitle,
    episodeId = episodeId,
    episodeNumber = episodeNumber.toInt(),
    episodeTitle = episodeTitle,
    progressSeconds = progress.toLong(),
    durationSeconds = duration.toLong(),
    // 后端 view_status：1 = 看完
    finished = viewStatus == 1,
    coverUrl = cover,
    // 保留绝对时间点，页面才能在手机切换时区后重新分组；无效日期保持未知。
    viewedAtMillis = top.nekoanime.mobile.app.model.history.parseHistoryTimestamp(updateDate),
)
