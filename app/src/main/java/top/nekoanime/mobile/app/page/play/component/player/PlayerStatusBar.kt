package top.nekoanime.mobile.app.page.play.component.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 充电时电池内部的填充色 */
private val ChargeGreen = Color(0xFF4CD964)

/** 电池外壳颜色 */
private val BatteryOutline = Color.White.copy(alpha = 0.85f)

/**
 * 电量状态。
 *
 * @param percent 电量百分比 0~100
 * @param charging 是否正在充电（含已充满）
 */
private data class BatteryState(val percent: Int, val charging: Boolean)

/**
 * 全屏播放器顶部的状态行。
 *
 * @description 屏幕正中间显示当前时间，右侧显示电量百分比。
 * 电量在充电时：填充色变绿，并在左侧多一个小闪电图标。
 * 竖屏（非全屏）不显示这条——B 站 也只有全屏才有。
 *
 * 状态只在这个组件内部读取，所以时间/电量的刷新只会重组这一行，不会带着整个控制栏一起重组。
 *
 * 安全区：整行只让开顶部；**时钟按整屏居中**（刘海在横屏时多半会压到左/右边缘，
 * 如果整行都让开左右安全区，时间就会明显偏向一侧），电量单独让开右侧。
 *
 * @param modifier 由控制栏给出的布局约束
 */
@Composable
fun PlayerStatusBar(modifier: Modifier = Modifier) {
    val clock = rememberClockText()
    val battery = rememberBatteryState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        Text(
            text = clock,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )

        BatteryIndicator(
            percent = battery.percent,
            charging = battery.charging,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
        )
    }
}

/**
 * 当前时间文本（HH:mm）。
 *
 * @description 每秒看一眼当前时间：值没变时 Compose 不会重组（State 写入相同值不触发失效），
 * 所以真正重组的时机就是"跳分钟"的那一刻。
 *
 * @returns 形如 20:53 的时间文本
 */
@Composable
private fun rememberClockText(): String {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var text by remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(formatter) {
        while (true) {
            text = formatter.format(Date())
            delay(1000)
        }
    }

    return text
}

/**
 * 监听电量变化。
 *
 * @description ACTION_BATTERY_CHANGED 是黏性广播：注册时立刻会收到一次当前值，
 * 之后电量每掉 1% 也会推一次，不需要自己轮询。
 *
 * @returns 当前电量状态
 */
@Composable
private fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readBatteryState(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                state = readBatteryState(context)
            }
        }

        // 系统广播：用 NOT_EXPORTED 注册（Android 14 起对注册接收器有显式导出要求）
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return state
}

/**
 * 读一次当前电量。
 *
 * @param context 上下文
 * @returns 电量百分比与充电状态；读不到时返回 0 / 未充电
 */
private fun readBatteryState(context: Context): BatteryState {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

    return BatteryState(percent = percent, charging = charging)
}

/**
 * 电量指示：左侧（充电时）一个闪电图标 + 一个画出来的电池，电池里显示百分比。
 *
 * @param percent 电量百分比
 * @param charging 是否充电中
 * @param modifier 布局约束
 */
@Composable
private fun BatteryIndicator(
    percent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (charging) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "充电中",
                tint = ChargeGreen,
                // 比电池本体略小一圈，看得清是"闪电"而不是一个小点
                modifier = Modifier.size(13.dp),
            )
        }

        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .width(30.dp)
                    .height(14.dp),
            ) {
                val strokeWidth = 1.2.dp.toPx()
                val nubWidth = 1.6.dp.toPx()
                val nubHeight = size.height * 0.36f
                val bodyWidth = size.width - nubWidth - 1.dp.toPx()
                val corner = 2.5.dp.toPx()

                // 外壳
                drawRoundRect(
                    color = BatteryOutline,
                    size = Size(bodyWidth, size.height),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = strokeWidth),
                )

                // 正极的小凸起
                drawRoundRect(
                    color = BatteryOutline,
                    topLeft = Offset(bodyWidth + 1.dp.toPx(), (size.height - nubHeight) / 2f),
                    size = Size(nubWidth, nubHeight),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )

                // 内部填充：充电时绿色，平时白色
                val inset = strokeWidth + 1.dp.toPx()
                val fillMaxWidth = (bodyWidth - inset * 2).coerceAtLeast(0f)
                val fillWidth = fillMaxWidth * (percent.coerceIn(0, 100) / 100f)

                if (fillWidth > 0f) {
                    drawRoundRect(
                        color = if (charging) ChargeGreen else Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(inset, inset),
                        size = Size(fillWidth, size.height - inset * 2),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    )
                }
            }

            // 百分比画在电池里面
            Text(
                text = percent.toString(),
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
