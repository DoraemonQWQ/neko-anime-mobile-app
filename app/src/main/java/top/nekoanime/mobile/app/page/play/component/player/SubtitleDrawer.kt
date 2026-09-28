package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字幕抽屉。
 *
 * @description 样式与 [EpisodeDrawer] 完全一致（同一个面板底色、同一条主色竖条、同样的按钮圆角与高亮），
 * 只是列的是**文件自带的内嵌字幕轨**：第一项固定是「关闭字幕」，后面每条轨道一项，正在用的那条主色高亮。
 * 竖屏下也能用（选集抽屉只在全屏出现，字幕没有这个限制）。
 *
 * 只负责画内容与上报点击，开关动画、遮罩与层级由 [NekoPlayer] 统一处理。
 *
 * @param tracks 内嵌字幕轨（顺序与文件里一致）
 * @param onDisable 关闭字幕
 * @param onSelect 选中某条字幕轨
 * @param width 面板宽度（竖屏传窄的那档，见 PlayerDrawerNarrowWidth）
 * @param modifier 由播放器给出的尺寸约束（宽度在这里定）
 */
@Composable
internal fun SubtitleDrawer(
    tracks: List<SubtitleTrack>,
    onDisable: () -> Unit,
    onSelect: (SubtitleTrack) -> Unit,
    width: Dp = PlayerDrawerWidth,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(PlayerDrawerPanelColor)
            // 面板整体吃掉触摸：点标题、点按钮之间的空隙都算"点在抽屉里"，
            // 既不会把抽屉关掉，也不会穿透到底下的播放/进度条/全屏按钮上。
            .pointerInput(Unit) {
                detectTapGestures { /* 抽屉内容容器上的点击：什么都不做，只挡住穿透 */ }
            }
            .padding(16.dp),
    ) {
        // 标题：主色竖条 + 字幕 + 共 N 条
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlayerDrawerActiveColor),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "字幕",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "共 ${tracks.size} 条",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 竖屏时播放器本身就不高，字幕轨多的情况下自己滚；正常两三条约等于刚好排满
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleOptionButton(
                label = "关闭字幕",
                // 一条都没选中才算"关闭中"，否则高亮的是正在用的那条轨道
                active = tracks.none { it.selected },
                onClick = onDisable,
            )

            tracks.forEach { track ->
                SubtitleOptionButton(
                    label = track.label,
                    active = track.selected,
                    onClick = { onSelect(track) },
                )
            }
        }
    }
}

/**
 * 抽屉里的单个字幕选项。
 *
 * @param label 显示的名字
 * @param active 是不是当前使用的那个
 * @param onClick 点击回调
 */
@Composable
private fun SubtitleOptionButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    // 播放器控件一样不要涟漪：抽屉是浮层，涟漪会被遮罩挡住反而显脏
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) PlayerDrawerActiveColor else Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = shortenLabel(label),
            color = if (active) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 名字太长的截断（超过 [PlayerDrawerLabelMaxLength] 个字就砍掉加省略号）。
 *
 * @param label 原始名字
 * @returns 可直接显示的名字
 */
private fun shortenLabel(label: String): String =
    if (label.length > PlayerDrawerLabelMaxLength) {
        label.take(PlayerDrawerLabelMaxLength) + "…"
    } else {
        label
    }
