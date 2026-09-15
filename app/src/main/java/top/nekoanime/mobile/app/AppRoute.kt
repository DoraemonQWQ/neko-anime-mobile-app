package top.nekoanime.mobile.app

import kotlinx.serialization.Serializable

/*
 * 页面路由（官方 Navigation Compose 的类型安全路由）。
 *
 * 为什么从自研路由换过来：自研那套只有一个 `route` 状态，**没有返回栈也没有状态保存**——
 * 从二级页返回时上一个页面会被重新创建，列表、滚动位置、搜索关键字都得重新来一遍。
 * 官方的 NavHost 会给每个目的地保存状态（`rememberSaveable` / 滚动位置都会恢复），
 * 返回栈也天然支持「播放页里的推荐点进去 → 返回回到上一个播放页」这种层级返回。
 *
 * 路由用 `@Serializable` 数据类/数据对象描述，参数直接是强类型字段，
 * 不用拼字符串、也不会出现「少个斜杠」的问题。
 */

/** 启动页：初始化（拉资料 + 检查更新 + 清理更新残留），完成后自动进首页 */
@Serializable
data object SplashRoute

/** 首页（底部三个 Tab：主页 / 番剧 / 用户） */
@Serializable
data object HomeRoute

/** 搜索页 */
@Serializable
data object SearchRoute

/** 观看历史页 */
@Serializable
data object HistoryRoute

/** 设置页 */
@Serializable
data object SettingsRoute

/**
 * 播放页。
 *
 * @param animeId 番剧 id
 * @param startEpisodeId 指定从哪一集开始（历史记录 / 继续观看进来时用）；null = 按本地进度或第一集
 * @param startPositionMs 指定从哪一秒开始（与 startEpisodeId 配套）
 */
@Serializable
data class PlayRoute(
    val animeId: Int,
    val startEpisodeId: Int? = null,
    val startPositionMs: Long = 0L,
)
