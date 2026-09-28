package top.nekoanime.mobile.app.data.api

import top.nekoanime.mobile.app.model.dto.PageDataDto
import top.nekoanime.mobile.app.model.dto.AnimeSubscriptionBody
import top.nekoanime.mobile.app.model.dto.AnimeSubscriptionDto
import top.nekoanime.mobile.app.model.dto.UserHistoryDto

/**
 * 用户相关接口（需要登录，后端控制器上挂了 `[Authorize]`）。
 *
 * @description 调用前 JWT 已经由 state 层交给 {@link ApiClient.authToken}，未登录时会 401。
 * 首页的「继续观看」走 history()，播放页的追番按钮走 subscribed() / updateSubscribed()。
 */
object UserApi {
    /**
     * 观看历史（分页）。
     *
     * @param keyword 关键字，空串表示不过滤
     * @param page 第几页
     * @param limit 每页几条
     * @returns 分页历史记录
     */
    suspend fun history(
        keyword: String = "",
        page: Int = 1,
        limit: Int = 12,
    ): Result<PageDataDto<UserHistoryDto>> = ApiClient.get(
        path = ApiRoutes.USER_HISTORY,
        query = mapOf("keyword" to keyword, "page" to page.toString(), "limit" to limit.toString()),
        serializer = PageDataDto.serializer(UserHistoryDto.serializer()),
    )

    /**
     * 取某个番剧的追番状态。
     *
     * @param animeId 番剧 id
     * @returns 是否已追番（未登录会失败）
     */
    suspend fun subscribed(animeId: Int): Result<Boolean> = ApiClient.get(
        path = ApiRoutes.userAnimeSubscribe(animeId),
        serializer = AnimeSubscriptionDto.serializer(),
    ).map { it.isSubscribed }

    /**
     * 修改某个番剧的追番状态。
     *
     * @param animeId 番剧 id
     * @param status true = 追番，false = 取消追番
     * @returns 后端返回的最终状态（以服务端为准，不拿本地状态猜）
     */
    suspend fun updateSubscribed(animeId: Int, status: Boolean): Result<Boolean> = ApiClient.post(
        path = ApiRoutes.userAnimeSubscribe(animeId),
        body = AnimeSubscriptionBody(status = status),
        bodySerializer = AnimeSubscriptionBody.serializer(),
        serializer = AnimeSubscriptionDto.serializer(),
    ).map { it.isSubscribed }
}
