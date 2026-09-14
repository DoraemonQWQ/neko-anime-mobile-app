package top.nekoanime.mobile.app.data.api

import top.nekoanime.mobile.app.model.dto.PageDataDto
import top.nekoanime.mobile.app.model.dto.UserHistoryDto

/**
 * 用户相关接口（需要登录，后端控制器上挂了 `[Authorize]`）。
 *
 * @description 登录还没接，这些接口现在调用会 401；等登录做完，
 * 把 JWT 交给 {@link ApiClient.authToken} 就能用。首页的「继续观看」走的就是 history()。
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
}
