package top.nekoanime.mobile.app.data.api

import kotlinx.serialization.builtins.ListSerializer
import top.nekoanime.mobile.app.model.dto.AnimeBasicDto
import top.nekoanime.mobile.app.model.dto.AnimeDetailDto
import top.nekoanime.mobile.app.model.dto.EpisodePlayDto
import top.nekoanime.mobile.app.model.dto.PageDataDto

/**
 * 番剧接口（都是公开接口，不需要登录）。
 *
 * @description 对应后端 `AnimeController`，路径与参数名都跟 web 端一套。
 */
object AnimeApi {
    /**
     * 热门番剧：首页轮播用。
     *
     * @returns 番剧列表（后端已经按热度排好）
     */
    suspend fun hot(): Result<List<AnimeBasicDto>> = ApiClient.get(
        path = ApiRoutes.ANIME_HOT,
        serializer = ListSerializer(AnimeBasicDto.serializer()),
    )

    /**
     * 番剧列表（分页）。
     *
     * @param page 第几页（从 1 开始）
     * @param limit 每页几条
     * @param year 年份筛选，默认 all
     * @param month 月份筛选，默认 all
     * @param type 类型（题材）筛选，默认 all
     * @param source 来源筛选，默认 all
     * @param category 分类筛选，默认 all
     * @returns 分页数据
     */
    suspend fun list(page: Int, limit: Int, type: String = "all"): Result<PageDataDto<AnimeBasicDto>> =
        list(
            page = page,
            limit = limit,
            year = "all",
            month = "all",
            type = type,
            source = "all",
            category = "all",
        )

    /**
     * 番剧列表（带全部筛选项）。
     *
     * @param page 第几页
     * @param limit 每页几条
     * @param year 年份
     * @param month 月份
     * @param type 类型（题材）
     * @param source 来源
     * @param category 分类
     * @returns 分页数据
     */
    suspend fun list(
        page: Int,
        limit: Int,
        year: String,
        month: String,
        type: String,
        source: String,
        category: String,
    ): Result<PageDataDto<AnimeBasicDto>> = ApiClient.get(
        path = ApiRoutes.ANIME_LIST,
        query = mapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
            "year" to year,
            "month" to month,
            "type" to type,
            "source" to source,
            "category" to category,
        ),
        serializer = PageDataDto.serializer(AnimeBasicDto.serializer()),
    )

    /**
     * 关键字搜索（分页）。
     *
     * @param keyword 关键字
     * @param page 第几页
     * @param limit 每页几条
     * @returns 分页数据
     */
    suspend fun search(keyword: String, page: Int, limit: Int): Result<PageDataDto<AnimeBasicDto>> =
        ApiClient.get(
            path = ApiRoutes.ANIME_SEARCH,
            query = mapOf("keyword" to keyword, "page" to page.toString(), "limit" to limit.toString()),
            serializer = PageDataDto.serializer(AnimeBasicDto.serializer()),
        )

    /**
     * 按题材推荐：首页的题材行用它。
     *
     * @param tag 题材（单个）
     * @param filterAnimeId 需要排除的番剧 id（一般是当前正在看的番剧），不排除传 -1
     * @returns 推荐列表
     */
    suspend fun recommendByTag(tag: String, filterAnimeId: Int = -1): Result<List<AnimeBasicDto>> =
        ApiClient.get(
            path = ApiRoutes.ANIME_RECOMMEND_TAG,
            query = mapOf("tags" to tag, "filter_anime_id" to filterAnimeId.toString()),
            serializer = ListSerializer(AnimeBasicDto.serializer()),
        )

    /**
     * 某个番剧的推荐（播放页底部）：后端按这个番剧的题材随机挑，自动排除它自己。
     *
     * @param animeId 番剧 id
     * @param count 要几个（后端限 1~30）
     * @returns 推荐列表
     */
    suspend fun recommendForAnime(animeId: Int, count: Int = 10): Result<List<AnimeBasicDto>> =
        ApiClient.get(
            path = ApiRoutes.ANIME_RECOMMEND_ANIME,
            query = mapOf("anime_id" to animeId.toString(), "count" to count.toString()),
            serializer = ListSerializer(AnimeBasicDto.serializer()),
        )

    /**
     * 番剧详情（含剧集列表）。
     *
     * @param animeId 番剧 id
     * @returns 详情
     */
    suspend fun detail(animeId: Int): Result<AnimeDetailDto> = ApiClient.get(
        path = ApiRoutes.animeDetail(animeId),
        serializer = AnimeDetailDto.serializer(),
    )

    /**
     * 某一集的播放地址。
     *
     * @param episodeId 剧集 id
     * @returns 播放信息（`video` 是后端拼好的完整地址）
     */
    suspend fun episodePlay(episodeId: Int): Result<EpisodePlayDto> = ApiClient.get(
        path = ApiRoutes.episodePlay(episodeId),
        serializer = EpisodePlayDto.serializer(),
    )
}
