package top.nekoanime.mobile.app.model.entity

import kotlinx.serialization.Serializable

/**
 * 番剧详情（播放页要用的那部分）。
 *
 * @description 正式版这些字段都由后端给（番剧详情接口 + 剧集播放地址接口），
 * 测试阶段先用真实资源拼一份，见 {@link SamplePlayData}。
 *
 * @param id 番剧 id（Bangumi subject id）
 * @param title 番剧名
 * @param coverUrl 横版封面（后端拼好桶域名后的完整地址）
 * @param score 评分；没有就传 null
 * @param updateDay 更新日文本（后端 update_day）；没有就传 null
 * @param tags 题材标签
 * @param summary 简介
 * @param episodes 全部剧集（按集数升序）
 */
@Serializable
data class AnimeDetail(
    val id: Int,
    val title: String,
    /** 竖版封面（Bangumi 封面；后端给的就是完整 URL） */
    val verticalCoverUrl: String,
    /** 横版封面（TMDB 抓的，存在 R2 的 meta/{id}/tmdb.jpg）；没有就传 null */
    val horizontalCoverUrl: String?,
    val score: Double?,
    val updateDay: String?,
    val tags: List<String>,
    val summary: String,
    val episodes: List<Episode>,
)

/**
 * 番剧简介（推荐列表用的最小信息）。
 *
 * @param id 番剧 id
 * @param title 番剧名
 * @param verticalCoverUrl 竖版封面
 * @param horizontalCoverUrl 横版封面（16:9，轮播图用）
 * @param tags 题材标签（推荐就按它算）
 * @param score 评分
 * @param description 简介（轮播图上会用到）
 */
@Serializable
data class AnimeBrief(
    val id: Int,
    val title: String,
    val verticalCoverUrl: String,
    val horizontalCoverUrl: String,
    val tags: List<String>,
    val score: Double?,
    val description: String = "",
)
