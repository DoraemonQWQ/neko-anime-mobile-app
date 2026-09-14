package top.nekoanime.mobile.app.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * 本机播放进度。
 *
 * @description 只干两件事，且**不依赖登录**：
 * 1. 这个番本机看到第几集了（换设备/重装就没了，这正是"本地优先"该有的语义）；
 * 2. 某一集本机看到哪一秒了。
 *
 * 用 SharedPreferences 就够了：量小、读写频繁、丢了也不致命。
 * 正式版如果要做"跨端同步"，线上那份由后端接口负责，这里的本地那份依旧保留（本地优先）。
 *
 * @param context 上下文（内部只拿 applicationContext）
 */
class LocalPlayProgress(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /**
     * 读某个番最后一次看的剧集 id。
     *
     * @param animeId 番剧 id
     * @returns 剧集 id；没记录返回 null
     */
    fun readLastEpisodeId(animeId: Int): Int? {
        val value = prefs.getInt("anime_$animeId" + KeyLastEpisodeSuffix, NotSet)

        return if (value == NotSet) null else value
    }

    /**
     * 读某一集的播放位置。
     *
     * @param episodeId 剧集 id
     * @returns 毫秒；没记录返回 0
     */
    fun readPositionMs(episodeId: Int): Long = prefs.getLong("episode_${episodeId}_position", 0L)

    /**
     * 记一次进度。
     *
     * @description 同时更新「这个番看到第几集」和「这一集看到哪」——两者必须一起写，
     * 否则会出现"上次看到第 3 集"但第 3 集的位置还是旧值这种对不上的情况。
     *
     * @param animeId 番剧 id
     * @param episodeId 剧集 id
     * @param positionMs 当前播放位置（毫秒）
     */
    fun save(animeId: Int, episodeId: Int, positionMs: Long) {
        prefs.edit {
            putInt("anime_$animeId" + KeyLastEpisodeSuffix, episodeId)
            putLong("episode_${episodeId}_position", positionMs.coerceAtLeast(0L))
        }
    }

    /**
     * 清掉某一集的本地进度（「重头观看」用）。
     *
     * @param episodeId 剧集 id
     */
    fun clearEpisode(episodeId: Int) {
        prefs.edit { remove("episode_${episodeId}_position") }
    }

    private companion object {
        /** 配置文件名 */
        const val PrefsName = "neko_play_progress"

        /** 「这个番看到第几集」的键后缀 */
        const val KeyLastEpisodeSuffix = "_last_episode"

        /** getInt 的哨兵值：区分"没记录"和"记录的是 0" */
        const val NotSet = -1
    }
}
