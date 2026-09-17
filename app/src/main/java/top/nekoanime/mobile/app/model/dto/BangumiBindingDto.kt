package top.nekoanime.mobile.app.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bangumi 绑定状态（对应后端 `BangumiBindingResponse`）。
 *
 * @param bound 是否已绑定
 * @param bangumiUserId Bangumi 用户ID（未绑定时为 0）
 * @param nickname Bangumi 昵称（未绑定为空）
 * @param avatar Bangumi 头像地址（未绑定为空）
 * @param bindDate 绑定时间（未绑定为 null）
 */
@Serializable
data class BangumiBindingDto(
    @SerialName("bound") val bound: Boolean = false,
    @SerialName("bangumi_user_id") val bangumiUserId: Int = 0,
    @SerialName("nickname") val nickname: String = "",
    @SerialName("avatar") val avatar: String = "",
    @SerialName("bind_date") val bindDate: String? = null,
)
