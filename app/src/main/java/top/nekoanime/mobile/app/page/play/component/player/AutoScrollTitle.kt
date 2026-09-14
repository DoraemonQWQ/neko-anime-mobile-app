package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 每段开头（以及回到起点后）停留多久 */
private const val DefaultPauseMillis = 10_000L

/** 滚动速度：每秒多少像素（按屏幕密度换算成 px/s 由调用方给） */
private const val DefaultPixelsPerSecond = 60f

/** 一次滚动最少用时，避免文字只超出一点点时"唰"一下 */
private const val MinScrollMillis = 800L

/**
 * 过长的标题自动滚动。
 *
 * @description 节奏与 B 站 一致：**先停 10 秒**，再匀速滚到末尾，停下再等 10 秒，
 * 然后瞬回起点重新开始。文字放得下就完全不动。
 *
 * 为什么不用 `Modifier.basicMarquee`：它内部是常驻的无限动画，UI 永远不会进入 idle，
 * 用 uiautomator / Espresso 做自动化时会一直等不到空闲（跑马灯也确实没必要一直占着帧）。
 * 这里的实现只有真正在滚的那几秒会请求帧，停留期间完全是静止的。
 *
 * @param text 要显示的文本（单行、不换行）
 * @param modifier 布局约束
 * @param color 文字颜色
 * @param fontSize 字号（用 Sp 传进来，内部按密度换算）
 * @param pauseMillis 每段停留时长
 * @param pixelsPerSecond 滚动速度（px/s）
 */
@Composable
fun AutoScrollTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit,
    pauseMillis: Long = DefaultPauseMillis,
    pixelsPerSecond: Float = DefaultPixelsPerSecond,
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }

    // 需要滚动的距离：文字比容器宽多少
    val distance = (textWidth - containerWidth).coerceAtLeast(0)

    // 滚动动画：offsetPx 每帧更新（放在 offset{} 里读，不会因此触发重组）
    val scroll = remember { Animatable(0f) }

    LaunchedEffect(text, distance, containerWidth, pauseMillis, pixelsPerSecond) {
        scroll.snapTo(0f)

        if (distance <= 0) {
            return@LaunchedEffect
        }

        val durationMillis = (distance / pixelsPerSecond * 1000f).toLong().coerceAtLeast(MinScrollMillis)

        while (true) {
            // 先停 10 秒：进来的时候也先停，别一上来就动
            delay(pauseMillis)

            scroll.animateTo(
                targetValue = distance.toFloat(),
                animationSpec = tween(durationMillis = durationMillis.toInt(), easing = LinearEasing),
            )

            // 滚到底再停 10 秒，然后瞬间回到起点
            delay(pauseMillis)
            scroll.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            onTextLayout = { textWidth = it.size.width },
            modifier = Modifier.offset {
                IntOffset(-scroll.value.roundToInt(), 0)
            },
        )
    }
}
