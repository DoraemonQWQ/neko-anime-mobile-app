package top.nekoanime.mobile.app

/**
 * 应用内的页面路由。
 *
 * @description 目前只有两个页面（首页、播放页），先不引导航库，用一个 sealed interface 就够了；
 * 以后页面变多再换成 Navigation Compose 也只是把这里的跳转换成 route 字符串。
 *
 * 移动端没有独立详情页：点任意番剧卡片都直接进播放页，播放页自己去后端取详情。
 */
sealed interface AppRoute {
    /**
     * 启动页：做初始化（拉资料 + 检查更新），完成后自动进首页
     */
    data object Splash : AppRoute

    /** 首页（底部两个 Tab：主页 / 全部动漫） */
    data object Home : AppRoute

    /**
     * 搜索页（首页顶栏那个假搜索框点进来）
     */
    data object Search : AppRoute

    /**
     * 观看历史页（用户 Tab 的「历史记录」入口）
     */
    data object History : AppRoute

    /**
     * 设置页（用户 Tab 的「设置」入口）
     */
    data object Settings : AppRoute

    /**
     * 播放页。
     *
     * @param animeId 番剧 id
     * @param startEpisodeId 指定从哪一集开始（历史记录/继续观看进来时用）；null = 按本地进度或第一集
     * @param startPositionMs 指定从哪一秒开始（与 startEpisodeId 配套）
     */
    data class Play(
        val animeId: Int,
        val startEpisodeId: Int? = null,
        val startPositionMs: Long = 0L,
    ) : AppRoute
}
