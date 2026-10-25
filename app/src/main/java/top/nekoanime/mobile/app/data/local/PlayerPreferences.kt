package top.nekoanime.mobile.app.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import top.nekoanime.mobile.app.logging.AppLog

/** 全局播放器偏好：字幕字号既持久化，也作为 Compose 状态让所有存活播放页即时同步。 */
object PlayerPreferences {
    const val DefaultSubtitleSize = 16
    const val MinSubtitleSize = 12
    const val MaxSubtitleSize = 32
    private var preferences: android.content.SharedPreferences? = null

    /** 外挂字幕的实际渲染字号，业务层只读；修改统一经 setSubtitleSize 保存。 */
    var subtitleSizeSp by mutableIntStateOf(DefaultSubtitleSize)
        private set

    /** 每个进程只绑定一次 applicationContext，不持有播放页 Activity，也不按番剧分别存储。 */
    fun init(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)
        subtitleSizeSp = preferences!!.getInt("subtitle_size_sp", DefaultSubtitleSize)
            .coerceIn(MinSubtitleSize, MaxSubtitleSize)
    }

    /** 先更新共享状态立即刷新字幕，再异步落盘；清图片/日志缓存不影响用户的字体偏好。 */
    fun setSubtitleSize(sizeSp: Int) {
        val next = sizeSp.coerceIn(MinSubtitleSize, MaxSubtitleSize)
        if (next == subtitleSizeSp) return
        subtitleSizeSp = next
        preferences?.edit()?.putInt("subtitle_size_sp", next)?.apply()
        AppLog.i("PlayerSettings", "字幕字号已更新 sizeSp=$next")
    }
}
