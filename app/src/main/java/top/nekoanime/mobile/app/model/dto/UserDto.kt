package top.nekoanime.mobile.app.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户资料。
 *
 * @param avatar 头像完整地址（后端拼好桶域名）；没设置头像时为空串
 * @param role 角色：后端给的是枚举序号（0 = 普通用户，1 = 管理员），
 * 界面实体里会转成 user / admin 这种可读字符串
 */
@Serializable
data class UserInfoDto(
    val id: Int = -1,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val role: Int = 0,
)

/**
 * 头像直传：申请地址。
 *
 * @param fileSize 文件大小（字节），后端按 10MB 上限校验
 * @param fileMd5 文件内容 MD5（小写 32 位），参与对象 key，重复上传只占一份存储
 * @param extension 扩展名（含点）
 */
@Serializable
data class AvatarUploadUrlBody(
    @SerialName("file_size") val fileSize: Long,
    @SerialName("file_md5") val fileMd5: String,
    val extension: String = ".jpg",
)

/**
 * 头像直传：地址响应。
 *
 * @param key 对象在桶内的相对路径（提交时回传）
 * @param uploadUrl 直传地址（PUT 整个文件到这里）
 */
@Serializable
data class AvatarUploadUrlDto(
    val key: String = "",
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 0,
)

/**
 * 头像直传：提交（直传完成后写库）。
 *
 * @param key 申请时返回的对象 key
 */
@Serializable
data class AvatarCommitBody(val key: String)

/**
 * 修改昵称请求体。
 *
 * @param nickname 新昵称（1 - 24 个字符）
 */
@Serializable
data class UpdateNicknameBody(val nickname: String)

/**
 * 追番状态（对应后端 `UserAnimeSubscriptionStatusResponse`）。
 *
 * @param isSubscribed 是否已追番
 */
@Serializable
data class AnimeSubscriptionDto(
    @SerialName("is_subscribed") val isSubscribed: Boolean = false,
)

/**
 * 修改追番状态的请求体（对应后端 `UserUpdateAnimeSubscriptionStatusRequest`）。
 *
 * @param status 目标状态：true = 追番，false = 取消追番
 */
@Serializable
data class AnimeSubscriptionBody(val status: Boolean)
