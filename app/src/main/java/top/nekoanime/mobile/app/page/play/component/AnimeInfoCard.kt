package top.nekoanime.mobile.app.page.play.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.model.entity.AnimeDetail

/** 简介最多显示几行（太长会把页面拉得很长） */
private const val SummaryMaxLines = 8

/** 竖版封面宽度 */
private val CoverWidth = 96.dp

/** 竖版封面比例（Bangumi 封面约 2:3，取 0.72） */
private const val CoverAspectRatio = 0.72f

/** 封面高度：右边那层信息用它来对齐，保证追番按钮的底边跟封面底边严丝合缝 */
private val CoverHeight = CoverWidth / CoverAspectRatio

/**
 * 番剧介绍卡片（播放页最下面那块）。
 *
 * @description 布局：左边竖版封面，右边番剧名 + 评分/放送时间；下面是题材标签、简介，
 * 右下角是追番按钮。封面用 Coil 异步加载（后端已经把完整 URL 给过来了），
 * 加载中/失败都会留一块底色，不会跳版。
 *
 * @param anime 番剧详情
 * @param subscribed 是否已追番
 * @param loggedIn 是否已登录：**没登录时追番按钮是禁用状态，文字就是「追番」**
 * @param onToggleSubscribe 点追番 / 取消追番
 * @param modifier 布局约束
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeInfoCard(
    anime: AnimeDetail,
    subscribed: Boolean,
    loggedIn: Boolean,
    onToggleSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row {
            CoverImage(
                url = anime.verticalCoverUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .width(CoverWidth)
                    .height(CoverHeight)
                    .clip(RoundedCornerShape(6.dp))
                    // 先铺一层底色：图片还没下来时也不会突然塌一块
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            // 右层再竖向分两层：上层文字信息，下层追番（追番自己靠右下）。
            // 这一层的高度锁成封面高度，所以下层永远贴齐封面底部，不靠"看起来差不多"。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(CoverHeight)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 上层：番剧名 + 评分 + 放送时间
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    anime.score?.let { score ->
                        Text(
                            text = "评分 $score",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    anime.updateDay?.let { date ->
                        Text(
                            text = "更新日 $date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 下层：追番，靠右下
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SubscribeButton(
                        subscribed = subscribed,
                        enabled = loggedIn,
                        onClick = onToggleSubscribe,
                    )
                }
            }
        }

        if (anime.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                anime.tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Text(
            text = anime.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = SummaryMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )

    }
}

/**
 * 追番按钮。
 *
 * @description 三种样子：
 * - 没登录：禁用（点不动），灰底灰字，文字固定是「追番」；
 * - 已登录未追番：主色实心，文字「追番」；
 * - 已登录已追番：淡灰底，文字「已追番」，再点一下就是取消追番。
 *
 * @param subscribed 是否已追番
 * @param enabled 能不能点（登录之后才可点）
 * @param onClick 点击回调
 * @param modifier 布局约束（由卡片决定放在哪、跟谁对齐）
 */
@Composable
private fun SubscribeButton(
    subscribed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    val containerColor = if (enabled && !subscribed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        subscribed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            // 没登录时就是「追番」两个字，不做成"登录后可追"之类的文案
            text = if (subscribed) "已追番" else "追番",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
