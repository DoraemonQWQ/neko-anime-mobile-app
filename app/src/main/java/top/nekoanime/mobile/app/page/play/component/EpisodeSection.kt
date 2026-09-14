package top.nekoanime.mobile.app.page.play.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.model.entity.Episode

/** 展开成网格时每行放几集 */
private const val EpisodesPerRow = 6

/** 展开网格的最大高度：约 4 行，再多就在网格内部滚动（选集这层是固定的，不能无限长高） */
private val ExpandedMaxHeight = 208.dp

/**
 * 播放页的选集区（非全屏）。
 *
 * @description 行为对齐 B 站：
 * - 默认是一行**横向滚动**的集数按钮，当前集用主色高亮；
 * - 只有当这一行真的能滚动（集数多到一屏放不下）时，右侧才出现「展开」，
 *   点开后变成多行网格（展开后不再横向滚动，直接一屏看全），再点「收起」还原；
 * - **切集之后把当前集滑到最左边第一个显示位置**（B 站 也是这样：
 *   切到第 30 集，选集条会自己滚到第 30 集，用户一眼就知道现在在播哪一集）。
 *
 * 注意选集是按钮不是链接：切集由播放页做「页内换源」，不重建播放器，全屏状态也不会掉。
 *
 * @param episodes 全部剧集（内部会按集数升序再排一次）
 * @param currentEpisodeId 当前播放的剧集 id
 * @param onSelect 选中某一集
 * @param modifier 布局约束
 */
@Composable
fun EpisodeSection(
    episodes: List<Episode>,
    currentEpisodeId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(episodes.toList()) { episodes.sortedBy { it.number } }
    val currentIndex = sorted.indexOfFirst { it.id == currentEpisodeId }
    val listState = rememberLazyListState()

    // 展开状态：只有能滚动时才让用户展开
    var expanded by remember { mutableStateOf(false) }

    // 横向这一行是不是已经放不下了（放不下才给「展开」）
    val rowScrollable by remember {
        derivedStateOf { !expanded && (listState.canScrollForward || listState.canScrollBackward) }
    }

    // 切集 / 首次进入：把当前集滑到最左边第一个位置
    LaunchedEffect(currentEpisodeId, expanded) {
        if (!expanded && currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
    ) {
        // 标题栏：选集 · 共 N 集 ·（右侧）展开/收起
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )

            Text(
                text = "选集",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )

            Text(
                text = "共 ${sorted.size} 集",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )

            Box(modifier = Modifier.weight(1f))

            if (expanded || rowScrollable) {
                ExpandToggle(
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }
        }

        if (expanded) {
            // 展开：多行网格（每行按钮等宽，空位用等宽占位补满）。
            // 选集这层是固定的，所以展开高度要封顶——集数特别多时在网格内部滚动，
            // 不然会把下面的卡片顶到屏幕外。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ExpandedMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sorted.chunked(EpisodesPerRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { episode ->
                            EpisodeButton(
                                episode = episode,
                                active = episode.id == currentEpisodeId,
                                onClick = { onSelect(episode.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                            )
                        }

                        repeat(EpisodesPerRow - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            // 收起：一行横向滚动
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.id }) { episode ->
                    EpisodeButton(
                        episode = episode,
                        active = episode.id == currentEpisodeId,
                        onClick = { onSelect(episode.id) },
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}

/**
 * 选集条右侧的「展开 / 收起」。
 *
 * @param expanded 当前是否展开
 * @param onClick 点击回调
 */
@Composable
private fun ExpandToggle(expanded: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "收起" else "展开",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 集数按钮。
 *
 * @param episode 剧集
 * @param active 是否是当前播放的这一集
 * @param onClick 点击回调
 * @param modifier 尺寸约束
 */
@Composable
private fun EpisodeButton(
    episode: Episode,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = episode.number.toString(),
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
