package top.nekoanime.mobile.app.model.entity

import kotlinx.serialization.Serializable

/**
 * 一集数据。
 *
 * @description 正式项目里这些字段都来自后端：`id` 是 Bangumi 剧集 id、`url` 是后端拼好桶域名的播放地址、
 * `number` 是集数（选集抽屉与上下集按钮都按它排序与展示）。
 * 测试工程里先用几条真实 R2 视频代替，字段含义保持一致，方便以后直接替换成接口数据。
 *
 * 集数允许不连续（比如只有第 1、3、4 集），上下集按钮按列表顺序走，不按数字加减。
 *
 * @param id 剧集 id（唯一，用来标记"当前正在播放的是哪一集"）
 * @param number 集数（展示用）
 * @param title 剧集标题（为空时界面用「第 N 集」兜底）
 */
@Serializable
data class Episode(
    val id: Int,
    val number: Int,
    val title: String,
)

/**
 * 一集的播放信息。
 *
 * @description 播放地址不在剧集列表里，要单独问后端拿（`/api/anime/episode/play/{episodeId}`），
 * 拿到的是后端拼好桶域名的完整地址。
 *
 * @param episode 对应的剧集
 * @param videoUrl 可直接播放的地址
 */
data class EpisodePlay(
    val episode: Episode,
    val videoUrl: String,
)
