package top.nekoanime.mobile.app.page.play

import top.nekoanime.mobile.app.model.entity.Episode

/**
 * 页面持有的自动连播闸门；只判断结束事件，不操作播放器、网络或进度存储。
 *
 * 同一媒体窗口必须先实际到达 READY，再允许消费一次 ENDED；窗口标识还能区分
 * 同一集、同一地址的两次建源，避免重试或换源时把旧源的结束状态带到新源。
 */
internal class EpisodeAutoAdvance {
    /** 实际播放器快照；mediaId 保留原始字符串，禁止把空 ID 或非剧集 ID 猜成当前集。 */
    data class Source(val mediaId: String, val url: String, val windowUid: Any)

    private var observedSource: Source? = null
    private var reachedReady = false
    /** 页面用消费边沿记录一次结束日志，不能在每个播放器事件里重复打印。 */
    var hasConsumedEnd = false
        private set

    /** 手动选集立即撤销旧源的 READY 许可，覆盖 A→B→A 尚未来得及重组的点击竞态。 */
    fun onManualSelection() {
        reachedReady = false
    }

    /**
     * 返回按 number 排序后的下一集 ID；末集、过期源、请求中的手动选集均返回 null。
     *
     * selectedEpisodeId 是页面最新选集意图，expectedEpisodeId/expectedUrl 是已成功获取的
     * 播放响应；二者与实际媒体必须完全一致。调用方在主线程同步改选集状态，因此即使
     * 重组尚未发生，旧源结束也不能覆盖刚刚点击的目标集。
     */
    fun nextEpisodeId(
        source: Source?,
        isReady: Boolean,
        isEnded: Boolean,
        selectedEpisodeId: Int,
        expectedEpisodeId: Int?,
        expectedUrl: String?,
        episodes: List<Episode>,
    ): Int? {
        // 窗口切换后重新等 READY；仅有同集 ID 或同 URL 不足以继承旧源的许可。
        if (source != observedSource) {
            observedSource = source
            reachedReady = false
            hasConsumedEnd = false
        }
        if (source == null || expectedEpisodeId == null ||
            source.mediaId != expectedEpisodeId.toString() || source.url != expectedUrl ||
            selectedEpisodeId != expectedEpisodeId
        ) {
            return null
        }
        if (isReady) reachedReady = true
        if (!isEnded || !reachedReady || hasConsumedEnd) return null

        // 先消费再查下一集，末集也只处理一次；绝不回绕到第一集。
        hasConsumedEnd = true
        val sortedEpisodes = episodes.sortedBy { it.number }
        val index = sortedEpisodes.indexOfFirst { it.id.toString() == source.mediaId }
        return if (index >= 0) sortedEpisodes.getOrNull(index + 1)?.id else null
    }
}
