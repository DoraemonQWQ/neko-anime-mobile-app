package top.nekoanime.mobile.app.page.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.component.SectionHeader
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.model.entity.WatchHistory

/**
 * 继续观看卡片宽度。
 *
 * @description 比番剧海报卡（120dp）宽一些但仍然小巧：竖屏一屏能露出两张半到三张，
 * 卡片太大时首页一列都放不下，滚动成本反而更高。
 */
private val ContinueCardWidth = 150.dp

/** 卡片内边距 */
private val ContinueCardPadding = 6.dp

/** 最多展示几条 */
private const val MaxItems = 10

/**
 * 首页「继续观看」。
 *
 * @description 与 Flutter 版一致：只挑**没看完**的记录（进度 < 配置的百分比，默认 90%），
 * 数量上限也读配置；一条都没有时整块不渲染（未登录时后端返回 401，这里就是空的）。
 * 卡片点击直接进这一集的播放页。
 *
 * @param items 观看历史
 * @param onOpenAnime 点击某条记录（参数是番剧 id，剧集与进度通过 startEpisode 传入）
 * @param onContinue 点击某条记录：直接带着"第几集 + 看到哪"进播放页
 * @param modifier 布局约束
 */
@Composable
fun ContinueWatchingRow(
    items: List<WatchHistory>,
    onContinue: (WatchHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val finishedPercent = ConfigLoader.config.home.continueWatchingFinishedPercent

    val watching = remember(items, finishedPercent) {
        items
            .filter { item ->
                item.durationSeconds > 0 &&
                    item.progressSeconds * 100 < item.durationSeconds * finishedPercent
            }
            .take(MaxItems)
    }

    if (watching.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "继续观看")

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(watching, key = { it.id() }) { item ->
                ContinueCard(item = item, onClick = { onContinue(item) })
            }
        }
    }
}

/**
 * 继续观看卡片：16:9 封面 + 底部进度条 + 番剧名 + 集数。
 *
 * @param item 历史记录
 * @param onClick 点击
 */
@Composable
private fun ContinueCard(item: WatchHistory, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val fraction = if (item.durationSeconds > 0) {
        (item.progressSeconds.toFloat() / item.durationSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .width(ContinueCardWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(
                url = item.coverUrl,
                contentDescription = item.animeTitle,
                modifier = Modifier.fillMaxWidth(),
            )

            // 进度条贴在封面底部：半透明底 + 主色进度
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Column(modifier = Modifier.padding(ContinueCardPadding)) {
            Text(
                text = item.animeTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${item.episodeNumber} 集",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )

                Text(
                    text = "  ${formatSeconds(item.progressSeconds)} / ${formatSeconds(item.durationSeconds)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 卡片变窄后进度文本可能放不下：让它吃掉剩余宽度并省略，别把整行撑破
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/**
 * 历史记录的唯一键。
 *
 * @returns 记录 id
 */
private fun WatchHistory.id(): Long = (animeId.toLong() shl 32) or (episodeId.toLong() and 0xFFFFFFFFL)

/**
 * 秒 → mm:ss。
 *
 * @param seconds 秒
 * @returns 形如 12:34 的文本
 */
private fun formatSeconds(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60

    return "%d:%02d".format(minutes, rest)
}
