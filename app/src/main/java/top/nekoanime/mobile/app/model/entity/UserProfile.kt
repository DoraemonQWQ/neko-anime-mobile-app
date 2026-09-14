package top.nekoanime.mobile.app.model.entity

/**
 * 用户资料（个人信息页展示用）。
 *
 * @param id 用户 id
 * @param username 用户名
 * @param nickname 昵称（可为空，展示用 displayName）
 * @param avatarUrl 头像地址；没有就传 null
 * @param role 角色（user / admin）
 */
data class UserProfile(
    val id: Int,
    val username: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val role: String = "user",
) {
    /** 展示名：优先昵称，没昵称就用用户名 */
    val displayName: String get() = nickname.ifBlank { username }
}
