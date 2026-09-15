package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** 拖动方向（第一次明显位移后锁定，避免一上手就来回抖动） */
private enum class DragAxis { Unknown, Horizontal, Vertical }

/**
 * 判定为「拖动」的最小位移（dp）。
 *
 * @description 判断是不是滑动，只看"滑了多远"。
 *
 * 双击时手指几乎不可能完全不动，但也不会滑很多（真实的抖动在十几像素以内）。
 * 而 Compose 自带的点击识别器认点击的容差是系统 touchSlop（本机约 30px），
 * 所以拖动阈值必须**明显大于**这个容差：一旦两者接近，
 * 双击时那点抖动就会被判定成滑动，滑动层 consume 掉移动事件，
 * 点击识别器立刻取消这次点击 → 双击失效（真机表现就是"只有中间能双击暂停"）。
 *
 * 24dp 差不多是一根手指"想滑动"时的最小动作，双击抖动到不了这个量级。
 */
private val DragStartDistance = 24.dp

/**
 * 开始拖动前至少要按住的时长（毫秒）。
 *
 * @description 距离阈值之外再补一层时间门槛：双击的每一下都很短（几十毫秒），
 * 按住时间不够就不进入拖动，进一步降低"两下点击之间被误判成滑动"的概率。
 * 锁定方向之前累计的位移会在锁定时一次性补发，所以既不会丢距离，也不会让快速滑动变迟钝。
 */
private const val DragStartHoldMs = 80L

/**
 * 播放器手势。
 *
 * @description 对齐 B 站移动端那套：
 * - 单击：显示 / 隐藏控制栏
 * - 双击左三分之一：后退 10 秒；双击右三分之一：前进 10 秒；双击中间：播放 / 暂停
 * - 水平拖动：快进 / 快退（松手才真正 seek，拖动时只预览）
 * - 竖直拖动：左半屏 = 亮度，右半屏 = 音量
 * - 长按：2 倍速快进（松手恢复）
 * - 锁定后：除「解锁」之外的手势全部忽略（避免躺着看时误触）
 *
 * 实现上分了两个 `pointerInput`：一个管点击类（tap / double tap / long press），
 * 一个管拖动（按位移方向判定是调进度还是调亮度音量）。
 *
 * @param enabled 是否启用手势（未加载出视频时可以关掉）
 * @param locked 是否处于锁定状态
 * @param advancedGesturesEnabled 是否允许「双击快进快退 / 双击播放暂停 / 水平拖动调进度 / 长按倍速」。
 * 首次加载（画面还没就绪）传 false：这时候拖动进度没有意义，长按加速也只会和加载打架。
 * 竖直拖动（左半屏亮度、右半屏音量）**不受它影响**，加载期间照样能调。
 * @param onTap 单击
 * @param onDoubleTap 双击，参数是点击位置在整屏宽度里的比例（0f~1f）
 * @param onSeekDrag 水平拖动，参数是本次位移占宽度的比例（正数向右）
 * @param onSeekEnd 水平拖动结束（这时才真正 seek）
 * @param onVerticalDrag 竖直拖动，参数是（起始 x 比例, 本次位移占高度的比例，向上为正）
 * @param onVerticalDragEnd 竖直拖动结束
 * @param onLongPressStart 长按开始（进入倍速）
 * @param onLongPressEnd 长按结束（恢复正常速度）
 * @param onInteractionStart 一次触摸开始（控制栏靠它暂停并重置自动隐藏计时）
 * @param onInteractionEnd 这次触摸结束（结束之后自动隐藏才继续计时）
 * @param topSafeZonePx 顶部安全区高度（px）：这一条里的触摸不算播放器手势，
 * 留给系统做"下滑唤出状态栏"，否则往下滑会被识别成调亮度/音量
 * @returns 叠加了手势的 Modifier
 */
fun Modifier.playerGestures(
    enabled: Boolean,
    locked: Boolean,
    advancedGesturesEnabled: Boolean = true,
    onTap: () -> Unit,
    onDoubleTap: (positionFraction: Float) -> Unit,
    onSeekDrag: (deltaFraction: Float) -> Unit,
    onSeekEnd: () -> Unit,
    onVerticalDrag: (startXFraction: Float, deltaFraction: Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    topSafeZonePx: Float = 0f,
): Modifier = this
    .pointerInput(enabled, locked, advancedGesturesEnabled, topSafeZonePx) {
        // 注意：锁定后**仍然要保留单击**，否则锁屏状态下没法再唤出/收起控制栏（解锁键也就找不到了）。
        // 双击、长按、滑动在锁定状态下全部忽略。
        if (!enabled) {
            return@pointerInput
        }

        // 长按抬手后要被忽略掉的 tap 截止时间（见下方 onTap 与 onPress 的说明）
        var ignoreTapUntil = 0L

        detectTapGestures(
            onTap = { offset ->
                // 顶部安全区：这段留给系统（下滑唤出状态栏），不当作播放器的单击
                if (offset.y < topSafeZonePx) {
                    return@detectTapGestures
                }

                // 长按（2 倍速）抬手后，detectTapGestures 有时还会补一个 onTap，
                // 结果就是"长按加速顺便把控制栏也切了一下"。这里在长按结束后的一小段时间内丢弃 tap。
                if (SystemClock.uptimeMillis() < ignoreTapUntil) {
                    return@detectTapGestures
                }

                Log.d("NekoGesture", "tap")
                onTap()
            },
            onDoubleTap = { offset ->
                if (offset.y < topSafeZonePx) {
                    return@detectTapGestures
                }

                if (!locked && advancedGesturesEnabled) {
                    Log.d("NekoGesture", "doubleTap x=${offset.x}")
                    onDoubleTap((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
            onPress = { offset ->
                if (offset.y < topSafeZonePx) {
                    return@detectTapGestures
                }

                // 锁定状态、以及首次加载期间（倍速会和加载打架）都不进入长按倍速
                if (locked || !advancedGesturesEnabled) {
                    return@detectTapGestures
                }

                // 长按：在系统长按时长内等到抬手就是普通点击，超时说明还按着 → 进倍速，松手恢复。
                // 这里不能用 launch 计时（PressGestureScope 不是 CoroutineScope，拿不到作用域），
                // 用 withTimeoutOrNull 原地等即可。
                val releasedInTime = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    tryAwaitRelease()
                }

                if (releasedInTime == null) {
                    Log.d("NekoGesture", "longPressStart")
                    onLongPressStart()
                    tryAwaitRelease()
                    Log.d("NekoGesture", "longPressEnd")
                    onLongPressEnd()

                    // 500ms 内不再把 tap 当单击（双击仍然正常）
                    ignoreTapUntil = SystemClock.uptimeMillis() + 500L
                }
            },
        )
    }
    .pointerInput(enabled, locked, advancedGesturesEnabled, topSafeZonePx) {
        if (!enabled || locked) {
            return@pointerInput
        }

        // 拖动阈值：既要明显大于点击识别的容差（系统 touchSlop），又不能大到拖动变得迟钝。
        // 取 "2 倍 touchSlop" 与 "24dp" 里更大的那个，兼顾不同屏幕与不同厂商的 touchSlop 差异。
        val dragThreshold = maxOf(viewConfiguration.touchSlop * 2f, DragStartDistance.toPx())

        // 自己实现拖动而不是用 detectDragGestures：
        // 后者内部是 awaitFirstDown(requireUnconsumed = true)，而上面的点击识别器会把 DOWN 事件 consume 掉，
        // 结果就是「单击有反应、滑动完全没反应」（真机上踩过）。这里允许已消费的 DOWN，
        // 拖动过程中再自己 consume，两边互不干扰。
        awaitEachGesture {
            // 走 Initial 通道：它在 Main 之前派发，点击识别器还没机会消费移动事件
            val down = awaitFirstDown(requireUnconsumed = false)

            // 顶部安全区：往下滑是"唤出系统状态栏"，不该同时改亮度/音量/进度
            if (down.position.y < topSafeZonePx) {
                return@awaitEachGesture
            }

            // 手指按下：控制栏的自动隐藏暂停 + 重新计时（松手后才继续计时）
            onInteractionStart()

            val startX = down.position.x
            val downTime = down.uptimeMillis
            var axis = DragAxis.Unknown
            var moved = Offset.Zero

            // 调试统计：本次拖动到底收了多少个移动事件、水平累计位移多少像素。
            // 用来核对「手指移动距离」与「实际生效的位移」是否一致（排查拖动幅度偏小）。
            var moveEvents = 0
            var totalDx = 0f

            /** 把一次位移交给当前锁定的方向（水平=进度，竖直=亮度/音量） */
            fun dispatch(motion: Offset) {
                when (axis) {
                    // 首次加载期间水平拖动（调进度）直接忽略：方向已经锁定为水平，
                    // 就不该再转去当亮度/音量，所以是"什么都不做"而不是改走竖直分支
                    DragAxis.Horizontal -> if (advancedGesturesEnabled) {
                        onSeekDrag(motion.x / size.width)
                    }

                    DragAxis.Vertical -> onVerticalDrag(
                        (startX / size.width).coerceIn(0f, 1f),
                        -motion.y / size.height,
                    )

                    DragAxis.Unknown -> Unit
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    break
                }

                // 不用 positionChange()：已消费的变更它会直接返回 0，
                // 这里按上一次位置自己算，保证拖动幅度总是准的
                val delta = change.position - change.previousPosition

                if (delta == Offset.Zero) {
                    continue
                }

                moveEvents++
                totalDx += delta.x

                if (axis == DragAxis.Unknown) {
                    moved += delta

                    val heldMs = change.uptimeMillis - downTime

                    // 滑得够远 + 按住够久，才认定这是拖动（横向管进度、纵向管亮度/音量）。
                    // 只抖动了几像素的双击不会被算成滑动，事件也就不会从点击识别器手里被抢走。
                    if (moved.getDistance() > dragThreshold && heldMs >= DragStartHoldMs) {
                        axis = if (abs(moved.x) >= abs(moved.y)) DragAxis.Horizontal else DragAxis.Vertical
                        Log.d(
                            "NekoGesture",
                            "dragAxis=$axis moved=$moved held=${heldMs}ms threshold=$dragThreshold",
                        )

                        // 锁定前手指已经走掉的那段距离要补发，否则快速滑动会丢掉开头一段
                        change.consume()
                        dispatch(moved)
                    }

                    continue
                }

                change.consume()
                dispatch(delta)
            }

            when (axis) {
                // 同上：加载期间没有下发过拖动预览，松手自然也不该真的去 seek
                DragAxis.Horizontal -> if (advancedGesturesEnabled) onSeekEnd()
                DragAxis.Vertical -> onVerticalDragEnd()
                DragAxis.Unknown -> Unit
            }

            Log.d(
                "NekoGesture",
                "gestureEnd axis=$axis events=$moveEvents totalDx=$totalDx width=${size.width}",
            )

            // 手指抬起：这次操作结束了，自动隐藏重新开始计时
            onInteractionEnd()
        }
    }
