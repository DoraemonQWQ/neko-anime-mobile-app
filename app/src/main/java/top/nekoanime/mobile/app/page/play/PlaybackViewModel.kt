package top.nekoanime.mobile.app.page.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer

/**
 * 播放会话（每个播放页一份）。
 *
 * @description 把 ExoPlayer 从「页面组合」挪到 ViewModel 里，是为了支持**播放页之间的层级返回**：
 * 从 A 点推荐进 B 时，A 的返回栈条目还在，它的 ViewModel 也就还在，
 * 于是 B 返回 A 时拿到的是同一个播放器实例——缓冲好的数据、播放进度都还在，**不会重新加载**。
 *
 * 生命周期：ViewModel 跟着返回栈条目走，A 被真正弹出（用户从 A 返回）时才 onCleared 释放播放器；
 * B 压在上面期间，NekoPlayer 离开组合时只暂停、不释放（见 NekoPlayer 的 onDispose）。
 *
 * 代价：一次播放链路里每深一层就多留一个暂停状态的播放器（各自保留已缓冲的数据），
 * 一般用户链路只有 1~2 层，属于可接受范围；如果哪天要限制，可以在 onCleared 之外
 * 再做「层数超过 N 就把最底层的 stop() 掉」的优化。
 */
class PlaybackViewModel : ViewModel() {
    /** 当前会话的播放器；null 表示还没建（懒加载，避免没有视频源的页面白建一个） */
    private var exoPlayer: ExoPlayer? = null

    /**
     * 取本会话的播放器，没有就新建一个。
     *
     * @param context 上下文（内部只用 applicationContext，避免泄漏 Activity）
     * @returns 播放器实例
     */
    fun player(context: Context): ExoPlayer = exoPlayer ?: ExoPlayer.Builder(context.applicationContext)
        .build()
        .apply {
            playWhenReady = true
        }
        .also { exoPlayer = it }

    /**
     * 整个播放页被弹出时释放播放器。
     */
    override fun onCleared() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
