package top.nekoanime.mobile.app.service

import top.nekoanime.mobile.app.data.local.LocalPlayProgress
import top.nekoanime.mobile.app.model.entity.AnimeDetail

/**
 * 播放起点。
 *
 * @param episodeId 从哪一集开始
 * @param positionMs 从这一集的哪一秒开始
 */
data class PlaybackStart(val episodeId: Int, val positionMs: Long)

/**
 * 决定进播放页时从哪一集、哪一秒开始（业务规则）。
 *
 * @description 优先级从高到低：
 * 1. **历史记录 / 继续观看**传进来的集数与进度（这时本地与线上都不看）；
 * 2. **本地**：本机看过这个番就续上本机记录的集数与位置（不管有没有登录）；
 * 3. **线上**：登录后问后端（移动端还没做登录，位置留在下面）；
 * 4. 都没有：第一集从头开始。
 *
 * @param anime 番剧详情
 * @param startEpisodeId 外部指定的剧集 id（历史记录/继续观看）；null 表示没指定
 * @param startPositionMs 外部指定的位置
 * @param progressStore 本机进度
 * @returns 播放起点
 */
fun resolvePlaybackStart(
    anime: AnimeDetail,
    startEpisodeId: Int?,
    startPositionMs: Long,
    progressStore: LocalPlayProgress,
): PlaybackStart {
    // 1) 历史记录 / 继续观看：直接用外面给的
    if (startEpisodeId != null && anime.episodes.any { it.id == startEpisodeId }) {
        return PlaybackStart(startEpisodeId, startPositionMs)
    }

    // 2) 本地优先：本机看过这个番就续上
    val localEpisodeId = progressStore.readLastEpisodeId(anime.id)

    if (localEpisodeId != null && anime.episodes.any { it.id == localEpisodeId }) {
        return PlaybackStart(localEpisodeId, progressStore.readPositionMs(localEpisodeId))
    }

    // 3) 线上：登录做完之后在这里问后端「这个用户看到哪一集、这集看到哪」，
    //    拿到结果就 return PlaybackStart(...)；本地有数据时上面的分支已经先返回了，天然满足"本地优先"。

    // 4) 都没有：第一集从头开始
    return PlaybackStart(anime.episodes.first().id, 0L)
}
