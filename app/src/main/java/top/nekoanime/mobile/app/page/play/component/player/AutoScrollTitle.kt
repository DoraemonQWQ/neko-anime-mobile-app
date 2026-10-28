package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 首次显示、滚到尾部和回到头部时，都停留 3 秒。 */
private const val DefaultPauseMillis = 3_000L

/** 默认每秒移动 30dp，换算成像素后，高低密度屏幕的视觉速度保持一致。 */
private const val DefaultDpPerSecond = 30f

/**
 * 单行标题只有真实宽度超过可用宽度时，才在头尾之间匀速往返。
 *
 * 显示后从头部等待 3 秒，滚到尾部等待 3 秒，再反向滚回头部，重复此过程。
 * 停留使用挂起等待，只在实际移动时请求动画帧；离开组合或隐藏控制器会取消动画。
 *
 * @param text 要显示的标题，始终按单行测量和绘制。
 * @param modifier 标题容器的可用宽度及其他布局约束。
 * @param color 文字颜色。
 * @param fontSize 文字字号，通常使用 sp。
 * @param pauseMillis 首次显示及每次抵达头尾后的停留时长，默认 3 秒。
 * @param pixelsPerSecond 滚动速度，单位 px/s；默认按当前密度换算为 30dp/s。
 * @param active 传入控制器实际可见状态；false 时取消并归零，再次变为 true 时重新等待。
 */
@Composable
fun AutoScrollTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit,
    pauseMillis: Long = DefaultPauseMillis,
    pixelsPerSecond: Float = DefaultDpPerSecond * LocalDensity.current.density,
    active: Boolean = true,
) {
    val density = LocalDensity.current
    var containerWidth by remember { mutableIntStateOf(0) }
    // 文本或字号、密度变化时，先丢弃旧测量，避免用上一条标题的宽度启动新动画。
    var textWidth by remember(text, fontSize, density.density, density.fontScale) {
        mutableIntStateOf(0)
    }
    val distance = (textWidth - containerWidth).coerceAtLeast(0)

    // 这些输入变化就直接使用新的零位动画状态，首帧也不会残留上一轮的偏移。
    // 宽度本身作为 key，即便新的溢出距离恰好相同，也会重新等待完整停留时间。
    val scroll = remember(
        text, fontSize, density.density, density.fontScale,
        containerWidth, textWidth, active,
    ) { Animatable(0f) }

    LaunchedEffect(scroll, pauseMillis, pixelsPerSecond) {
        scroll.snapTo(0f)
        // 隐藏、尚未完成布局、文本放得下或速度无效时，不创建循环等待任务。
        if (!active || containerWidth <= 0 || distance <= 0 ||
            !pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f
        ) {
            return@LaunchedEffect
        }

        // 距离和速度都使用 px，保持正反两个方向同速；仅限制 tween 能接受的时长范围。
        // 不设固定最短滚动时长，否则只溢出几像素时会比其他标题明显更慢。
        val durationMillis = (distance.toDouble() / pixelsPerSecond * 1_000)
            .coerceIn(1.0, Int.MAX_VALUE.toDouble())
            .roundToInt()
        val animationSpec = tween<Float>(durationMillis = durationMillis, easing = LinearEasing)
        val pause = pauseMillis.coerceAtLeast(0L)

        // 首次显示先停头部；每一轮结束后再停头部，因此各端点都只等待一次。
        delay(pause)
        while (true) {
            scroll.animateTo(targetValue = distance.toFloat(), animationSpec = animationSpec)
            delay(pause)
            scroll.animateTo(targetValue = 0f, animationSpec = animationSpec)
            delay(pause)
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
            // 无界测量后这里得到完整单行宽度，而不是被容器截断后的宽度。
            onTextLayout = { textWidth = it.size.width },
            modifier = Modifier
                // 在布局阶段读取动画值，滚动的每一帧无需触发文字重组或重新测量。
                .offset { IntOffset(-scroll.value.roundToInt(), 0) }
                // 只放开文字自身的测量宽度；外层容器仍遵守调用方约束并裁剪溢出内容。
                // 显式从头部对齐，防止超长文字按默认居中对齐而在初始帧丢失开头。
                .wrapContentWidth(align = Alignment.Start, unbounded = true),
        )
    }
}
