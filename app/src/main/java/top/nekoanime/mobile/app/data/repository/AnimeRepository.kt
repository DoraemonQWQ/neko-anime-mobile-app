package top.nekoanime.mobile.app.data.repository

import top.nekoanime.mobile.app.data.api.AnimeApi
import top.nekoanime.mobile.app.model.dto.AnimeBasicDto
import top.nekoanime.mobile.app.model.dto.AnimeDetailDto
import top.nekoanime.mobile.app.model.entity.AnimeBrief
import top.nekoanime.mobile.app.model.entity.AnimeDetail
import top.nekoanime.mobile.app.model.entity.Episode
import top.nekoanime.mobile.app.model.entity.EpisodePlay
import top.nekoanime.mobile.app.model.entity.parseEpisodeSubtitles

/**
 * 分页结果（列表页用）。
 *
 * @param items 本页数据
 * @param page 本页页码
 * @param total 总条数
 * @param hasMore 还有没有下一页
 */
data class PagedAnime(
    val items: List<AnimeBrief>,
    val page: Int,
    val total: Long,
    val hasMore: Boolean,
)

/**
 * 番剧仓库。
 *
 * @description 页面只跟这一层打交道：它负责调接口（api 层）并把 DTO 转成界面实体（model 层）。
 * 以后要加内存/磁盘缓存、或者把多个接口拼成一份数据，也都在这一层做，页面不用改。
 */
object AnimeRepository {
    /**
     * 热门番剧（首页轮播）。
     *
     * @returns 番剧简介列表
     */
    suspend fun hot(): Result<List<AnimeBrief>> =
        AnimeApi.hot().map { list -> list.map { it.toBrief() } }

    /**
     * 按题材推荐（首页题材行）。
     *
     * @param tag 题材
     * @param filterAnimeId 要排除的番剧 id（默认不排除）
     * @returns 番剧简介列表
     */
    suspend fun recommendByTag(tag: String, filterAnimeId: Int = -1): Result<List<AnimeBrief>> =
        AnimeApi.recommendByTag(tag, filterAnimeId).map { list -> list.map { it.toBrief() } }

    /**
     * 番剧列表（分页，"全部动漫"页用）。
     *
     * @param page 第几页
     * @param limit 每页几条
     * @param year 年份筛选
     * @param month 月份筛选
     * @param type 类型（题材）筛选
     * @param source 来源筛选
     * @param category 分类筛选
     * @returns 分页结果
     */
    suspend fun list(
        page: Int,
        limit: Int,
        year: String = "all",
        month: String = "all",
        type: String = "all",
        source: String = "all",
        category: String = "all",
        sort: String = "date",
        order: String = "desc",
    ): Result<PagedAnime> =
        AnimeApi.list(page, limit, year, month, type, source, category, sort, order).map { dto ->
            PagedAnime(
                items = dto.data.map { it.toBrief() },
                page = dto.page,
                total = dto.total,
                hasMore = dto.page * dto.limit < dto.total,
            )
        }

    /**
     * 关键字搜索（分页，搜索页用）。
     *
     * @param keyword 关键字
     * @param page 第几页
     * @param limit 每页几条
     * @returns 分页结果
     */
    suspend fun search(keyword: String, page: Int, limit: Int): Result<PagedAnime> =
        AnimeApi.search(keyword, page, limit).map { dto ->
            PagedAnime(
                items = dto.data.map { it.toBrief() },
                page = dto.page,
                total = dto.total,
                hasMore = dto.page * dto.limit < dto.total,
            )
        }

    /**
     * 某个番剧的推荐（播放页底部）。
     *
     * @param animeId 番剧 id
     * @param count 要几个
     * @returns 番剧简介列表
     */
    suspend fun recommendForAnime(animeId: Int, count: Int = 10): Result<List<AnimeBrief>> =
        AnimeApi.recommendForAnime(animeId, count).map { list -> list.map { it.toBrief() } }

    /**
     * 番剧详情（含剧集列表）。
     *
     * @param animeId 番剧 id
     * @returns 详情
     */
    suspend fun detail(animeId: Int): Result<AnimeDetail> =
        AnimeApi.detail(animeId).map { it.toDetail() }

    /**
     * 某一集的播放信息；字幕独立解析，畸形字幕不影响主视频。
     *
     * @param episodeId 剧集 id
     * @param episode 对应的剧集
     * @returns 视频地址与原始字幕描述
     */
    suspend fun episodePlay(episodeId: Int, episode: Episode): Result<EpisodePlay> =
        AnimeApi.episodePlay(episodeId).map { dto ->
            EpisodePlay(episode, dto.video, parseEpisodeSubtitles(dto.subtitles))
        }
}

/**
 * 列表项 DTO → 界面实体。
 *
 * @returns 番剧简介
 */
private fun AnimeBasicDto.toBrief(): AnimeBrief = AnimeBrief(
    id = id,
    title = title,
    verticalCoverUrl = verticalCover,
    horizontalCoverUrl = horizontalCover,
    tags = tags,
    score = rating.takeIf { it > 0 },
    description = description,
)

/**
 * 详情 DTO → 界面实体。
 *
 * @returns 番剧详情
 */
private fun AnimeDetailDto.toDetail(): AnimeDetail = AnimeDetail(
    id = id,
    title = title,
    verticalCoverUrl = verticalCover,
    horizontalCoverUrl = horizontalCover.ifBlank { null },
    score = rating.takeIf { it > 0 },
    updateDay = updateDay.ifBlank { null },
    tags = tags,
    summary = description,
    episodes = episodes.map { episode ->
        Episode(
            id = episode.id,
            // 集数在后端是 double（有 12.5 话这种），界面上按整数显示
            number = episode.episodeNumber.toInt(),
            title = episode.title,
        )
    },
)
