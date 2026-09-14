package top.nekoanime.mobile.app.model.entity
/**
 * 一条观看历史（首页「继续观看」用）。
 *
 * @param animeId 番剧 id
 * @param animeTitle 番剧名
 * @param episodeId 剧集 id
 * @param episodeNumber 集数
 * @param episodeTitle 剧集标题
 * @param progressSeconds 已看到多少秒
 * @param durationSeconds 这一集多长（秒）；0 表示后端没给
 * @param finished 是否已看完
 * @param coverUrl 这一集的封面（后端抽帧的 16:9 图）
 */
data class WatchHistory(
    val animeId: Int,
    val animeTitle: String,
    val episodeId: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val progressSeconds: Long,
    val durationSeconds: Long,
    val finished: Boolean,
    val coverUrl: String,
)
