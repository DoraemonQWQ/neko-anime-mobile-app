package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 弹幕层（**预留位**）。
 *
 * 目前不打算做弹幕，所以界面上不会显示任何东西；但播放器已经把这个位置留好了：
 * 视频画面之上、控制层与手势层之下（也就是弹幕该待的地方）。
 *
 * 将来要实现弹幕时，只要：
 * 1. 写一个实现类（自己管弹幕轨道、滚动动画、屏蔽词等）；
 * 2. 在 `NekoPlayer` 里把 `danmakuLayer` 参数换成那个实现。
 *
 * 播放器会把「当前播放位置」「画面尺寸」以及每一帧的绘制时机交给实现方，
 * 所以实现方不需要自己去读播放器状态。
 */
interface DanmakuLayer {
    /**
     * 播放位置变化时调用（毫秒）。
     *
     * @param positionMs 当前播放位置
     */
    fun onPositionChanged(positionMs: Long) = Unit

    /**
     * 画面尺寸变化时调用（像素）。
     *
     * @param width 画面宽度
     * @param height 画面高度
     */
    fun onViewportChanged(width: Int, height: Int) = Unit

    /**
     * 播放器开始播放/暂停时调用。
     *
     * @param playing 是否正在播放
     */
    fun onPlayStateChanged(playing: Boolean) = Unit

    /**
     * 弹幕层的 UI（由播放器放在画面之上）。
     *
     * @param modifier 由播放器给出的尺寸约束（铺满画面）
     */
    @Composable
    fun Content(modifier: Modifier)
}

/**
 * 空弹幕实现：什么都不画。
 *
 * @description 默认就用它，所以现在打开播放器界面上看不到弹幕相关的任何东西。
 */
object NoDanmakuLayer : DanmakuLayer {
    @Composable
    override fun Content(modifier: Modifier) {
        // 预留位：这里将来放弹幕画布
        Box(modifier)
    }
}
