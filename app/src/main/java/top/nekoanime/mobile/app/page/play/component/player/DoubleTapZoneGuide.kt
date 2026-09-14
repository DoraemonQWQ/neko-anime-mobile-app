package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 分区底色（跟播放器主色同一个粉，压到很淡） */
private val ZoneTint = Color(0xFFE91E63).copy(alpha = 0.12f)

/**
 * 双击分区调试线。
 *
 * @description 只在调试开关打开时绘制：把画面按双击判定切成三段，
 * 左右两段（后退 / 前进 10 秒）铺一层淡粉色，中间那段留白，分界处画一条竖线，并标上文字。
 *
 * 这一层没有任何 pointerInput，所以不会抢手势、也不会挡控制栏。
 *
 * @param sideFraction 左右两侧各占的比例（与双击判定用的是同一个值，保证"看到的=生效的"）
 * @param modifier 由播放器给出的布局约束
 */
@Composable
fun DoubleTapZoneGuide(
    sideFraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftEdge = size.width * sideFraction
            val rightEdge = size.width * (1f - sideFraction)
            val strokeWidth = 2.dp.toPx()

            // 左右两段铺淡色，中间不铺：一眼就能看出"哪块是快进快退、哪块是暂停"
            drawRect(
                color = ZoneTint,
                topLeft = Offset.Zero,
                size = Size(leftEdge, size.height),
            )
            drawRect(
                color = ZoneTint,
                topLeft = Offset(rightEdge, 0f),
                size = Size(size.width - rightEdge, size.height),
            )

            // 两条分界线
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(leftEdge, 0f),
                end = Offset(leftEdge, size.height),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(rightEdge, 0f),
                end = Offset(rightEdge, size.height),
                strokeWidth = strokeWidth,
            )
        }

        // 三段各自居中放一行说明；用 weight 按同一比例分，标出来的中心就是实际判定区的中心
        Row(modifier = Modifier.fillMaxSize()) {
            ZoneLabel(text = "后退 10s", weight = sideFraction)
            ZoneLabel(text = "播放 / 暂停", weight = 1f - sideFraction * 2f)
            ZoneLabel(text = "前进 10s", weight = sideFraction)
        }
    }
}

/**
 * 分区里的一行说明文字。
 *
 * @param text 文案
 * @param weight 该段占的宽度比例
 */
@Composable
private fun RowScope.ZoneLabel(text: String, weight: Float) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
