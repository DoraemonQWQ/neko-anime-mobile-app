package top.nekoanime.mobile.app.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * 番剧列表项。
 *
 * @description 对应后端 `AnimeBasicResponse`：`/api/anime/hot`、`/list`、`/search`、
 * `/recommend/tag` 返回的都是这个结构。字段名跟接口的 snake_case 一一对应，
 * 转换到界面实体在 repository 里做。
 */
@Serializable
data class AnimeBasicDto(
    val id: Int = -1,
    val title: String = "",
    @SerialName("vertical_cover") val verticalCover: String = "",
    @SerialName("horizontal_cover") val horizontalCover: String = "",
    val rating: Double = 0.0,
    val tags: List<String> = emptyList(),
    val description: String = "",
    @SerialName("episode_total_count") val episodeTotalCount: Int = 0,
    @SerialName("episode_update_count") val episodeUpdateCount: Int = 0,
)

/**
 * 剧集。
 *
 * @description 对应后端 `EpisodeInfoResponse`。注意这里**没有播放地址**——
 * 播放地址要单独问 `/api/anime/episode/play/{episodeId}`（后端按存储桶拼好的完整 URL）。
 */
@Serializable
data class EpisodeDto(
    val id: Int = -1,
    @SerialName("anime_id") val animeId: Int = -1,
    val duration: Double = 0.0,
    val title: String = "",
    @SerialName("anime_title") val animeTitle: String = "",
    @SerialName("episode_number") val episodeNumber: Double = 0.0,
    val cover: String = "",
)

/**
 * 剧集播放信息。
 *
 * @param video 后端拼好的完整播放地址（桶域名 + 对象路径）
 * @param subtitles 原始字幕字段，独立解析以隔离畸形字幕，并完整保留扩展元数据
 */
@Serializable
data class EpisodePlayDto(
    val id: Int = -1,
    @SerialName("anime_id") val animeId: Int = -1,
    val duration: Double = 0.0,
    val title: String = "",
    @SerialName("anime_title") val animeTitle: String = "",
    @SerialName("episode_number") val episodeNumber: Double = 0.0,
    val cover: String = "",
    val video: String = "",
    val subtitles: JsonElement = JsonNull,
)

/**
 * 番剧详情。
 *
 * @description 对应后端 `AnimeDetailResponse`（C# 那边继承自 AnimeBasicResponse）。
 * Kotlin 的 @Serializable 不方便继承 data class，所以这里把基础字段平铺一份——
 * DTO 允许这样重复，界面实体（model 层）不重复。
 */
@Serializable
data class AnimeDetailDto(
    val id: Int = -1,
    val title: String = "",
    @SerialName("vertical_cover") val verticalCover: String = "",
    @SerialName("horizontal_cover") val horizontalCover: String = "",
    val rating: Double = 0.0,
    val tags: List<String> = emptyList(),
    val description: String = "",
    @SerialName("episode_total_count") val episodeTotalCount: Int = 0,
    @SerialName("episode_update_count") val episodeUpdateCount: Int = 0,
    @SerialName("over_status") val overStatus: Boolean = false,
    @SerialName("update_day") val updateDay: String = "",
    val episodes: List<EpisodeDto> = emptyList(),
)

/**
 * 分页数据。
 *
 * @param total 总条数
 * @param limit 每页条数
 * @param page 当前页（从 1 开始）
 * @param data 本页数据
 */
@Serializable
data class PageDataDto<T>(
    val total: Long = 0,
    val limit: Int = 10,
    val page: Int = 1,
    val data: List<T> = emptyList(),
)

/**
 * 观看历史（继续观看用）。
 *
 * @param progress 已看到多少秒
 * @param duration 这一集多长（秒）
 * @param viewStatus 0 未看完 / 1 看完（后端定义）
 */
@Serializable
data class UserHistoryDto(
    val id: Long = 0,
    @SerialName("anime_id") val animeId: Int = -1,
    @SerialName("anime_title") val animeTitle: String = "",
    @SerialName("episode_id") val episodeId: Int = -1,
    @SerialName("episode_number") val episodeNumber: Double = 0.0,
    @SerialName("episode_title") val episodeTitle: String = "",
    @SerialName("view_status") val viewStatus: Int = 0,
    val progress: Double = 0.0,
    val duration: Double = 0.0,
    /** 这一集的封面（后端抽帧的 16:9 图，继续观看卡片用） */
    val cover: String = "",
)
