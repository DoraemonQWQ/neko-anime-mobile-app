package top.nekoanime.mobile.app.data.api

/**
 * 后端接口路由表。
 *
 * @description 所有接口路径集中在这里，避免各处手写字符串拼接导致"少个斜杠 / 拼错 id"这种低级问题：
 * - **固定路径**用常量（例如 [ANIME_HOT]）；
 * - **带动态参数**的路径用函数（例如 [animeDetail] 接收 animeId），调用方不可能拼错。
 *
 * 改动后端路由时只需要动这一个文件；查询参数（page / limit / keyword…）不在这里，
 * 它们由各接口方法通过 `query` 传，避免把参数名也硬编码进来两遍。
 */
object ApiRoutes {
    // ==================== 番剧（公开） ====================

    /** 热门番剧（首页轮播） */
    const val ANIME_HOT = "/api/anime/hot"

    /** 番剧列表（分页 + 筛选） */
    const val ANIME_LIST = "/api/anime/list"

    /** 关键字搜索（分页） */
    const val ANIME_SEARCH = "/api/anime/search"

    /** 按题材推荐 */
    const val ANIME_RECOMMEND_TAG = "/api/anime/recommend/tag"

    /** 某个番剧的相关推荐 */
    const val ANIME_RECOMMEND_ANIME = "/api/anime/recommend/anime"

    /**
     * 番剧详情（含剧集列表）。
     *
     * @param animeId 番剧 id（Bangumi 番剧 ID）
     * @returns 接口路径
     */
    fun animeDetail(animeId: Int): String = "/api/anime/$animeId"

    /**
     * 某一集的播放地址。
     *
     * @param episodeId 剧集 id
     * @returns 接口路径
     */
    fun episodePlay(episodeId: Int): String = "/api/anime/episode/play/$episodeId"

    // ==================== 用户（需要登录） ====================

    /** 前台登录（返回 JWT 纯文本） */
    const val USER_LOGIN = "/api/user/auth/login"

    /** 修改密码（用户改自己的） */
    const val USER_CHANGE_PASSWORD = "/api/user/auth/password"

    /** 当前用户资料 */
    const val USER_INFO = "/api/user/data/info"

    /** 修改昵称 */
    const val USER_NICKNAME = "/api/user/data/profile/nickname"

    /** 观看历史（分页 / 搜索） */
    const val USER_HISTORY = "/api/user/data/history"

    /** 头像直传：申请地址 */
    const val USER_AVATAR_UPLOAD_URL = "/api/user/data/avatar/upload-url"

    /** 头像直传：提交写库 */
    const val USER_AVATAR_COMMIT = "/api/user/data/avatar/commit"

    // ==================== 客户端更新（公开） ====================

    /** 检查更新 */
    const val APP_UPDATE = "/api/app/update"
}
