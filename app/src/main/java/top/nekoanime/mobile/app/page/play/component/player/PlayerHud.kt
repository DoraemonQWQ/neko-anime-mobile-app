package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** HUD 状态：同一时刻只显示一种 */
sealed interface PlayerHudState {
    /** 拖动进度（手势横滑 / 直接拖进度条）：只显示"跳到哪" */
    data class Seek(val targetMs: Long) : PlayerHudState

    /** 亮度 / 音量：0f~1f */
    data class Vertical(val kind: Kind, val value: Float) : PlayerHudState {
        enum class Kind { Brightness, Volume }
    }

    /** 倍速中 */
    data class Speed(val rate: Float) : PlayerHudState

    /** 双击快进 / 快退 */
    data class Skip(val seconds: Int) : PlayerHudState
}

/**
 * 指示框固定尺寸：小长方形。所有提示共用一套尺寸，切类型时不会忽大忽小。
 * 尺寸按"最长的那一行"（音量/亮度：名称 + 方块 + 百分比）定，保证文字都放得下。
 */
private val HudWidth = 136.dp
private val HudHeight = 44.dp

/** 指示框里的字号：所有提示统一用这一号 */
private val HudValueSize = 13.sp

/** 亮度/音量里那个小长方块 */
private val LevelBlockWidth = 46.dp
private val LevelBlockHeight = 8.dp

/**
 * 亮度/音量百分比文本的固定占位宽度。
 *
 * @description 9% 和 100% 的字符数不一样，不给固定宽度的话整行会左右抖；
 * 34dp 足够放下「100%」（13sp）。
 * 注意只固定**宽度**，不要把高度也写死——文字比 8dp 的方块高，限高会被挤出来。
 */
private val LevelPercentWidth = 34.dp

/**
 * 播放器中央 HUD。
 *
 * @description 只在操作过程中短暂出现，全部装在同一个**固定尺寸的小长方形**里：
 * - 拖进度（手势横滑或拖控制栏进度条）：只显示目标时间（不再有 ±xxs 那一行）；
 * - 亮度 / 音量：一个小长方块按比例填充 + 百分比；
 * - 倍速、双击跳转：一行文字。
 * 平时 state 为 null，什么都不画。
 *
 * @param state 当前要展示的内容
 * @param modifier 由播放器给出的布局约束
 */
@Composable
fun PlayerHud(state: PlayerHudState?, modifier: Modifier = Modifier) {
    if (state == null) {
        return
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (state) {
            is PlayerHudState.Seek -> HudBox {
                Text(
                    text = formatDuration(state.targetMs),
                    color = Color.White,
                    fontSize = HudValueSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            is PlayerHudState.Vertical -> VerticalHud(state)

            is PlayerHudState.Speed -> HudBox {
                Text(
                    text = "${state.rate}× 快进中",
                    color = Color.White,
                    fontSize = HudValueSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            is PlayerHudState.Skip -> HudBox {
                Text(
                    text = if (state.seconds >= 0) "快进 ${state.seconds}s" else "快退 ${-state.seconds}s",
                    color = Color.White,
                    fontSize = HudValueSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * HUD 底板：固定尺寸的小长方形。
 *
 * @param content 底板里的内容
 */
@Composable
private fun HudBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = HudWidth, height = HudHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 亮度 / 音量 HUD：小长方块（按比例填充）+ 百分比。
 *
 * @param state 状态
 */
@Composable
private fun VerticalHud(state: PlayerHudState.Vertical) {
    val value = state.value.coerceIn(0f, 1f)

    HudBox {
        // 一行放完：名称 + 长方块 + 百分比。
        // 整行在框里水平居中、每个元素垂直居中对齐，不再出现"上面居中、下面一行偏"的情况。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.kind == PlayerHudState.Vertical.Kind.Brightness) "亮度" else "音量",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = HudValueSize,
            )

            // 小长方块：底色是轨道，里面按当前值填白
            Box(
                modifier = Modifier
                    .width(LevelBlockWidth)
                    .height(LevelBlockHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(value)
                        .background(Color.White),
                )
            }

            Box(
                modifier = Modifier
                    .width(LevelPercentWidth),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${(value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = HudValueSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }

        }
    }
}

/**
 * 毫秒 → `H:mm:ss` / `m:ss`。
 *
 * @param milliseconds 毫秒
 * @returns 可读时长
 */
fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
