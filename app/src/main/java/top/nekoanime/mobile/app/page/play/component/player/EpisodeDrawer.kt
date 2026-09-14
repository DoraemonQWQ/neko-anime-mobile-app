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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.model.entity.Episode

/** 选集选中色：与前台主色一致（白 + 淡灰 + 粉里的那个粉） */
private val EpisodeActiveColor = Color(0xFFE91E63)

/** 抽屉面板底色：播放器控制栏是深色，抽屉也保持同一套深色，避免半黑半白 */
private val EpisodePanelColor = Color(0xF21B1C20)

/**
 * 抽屉面板宽度。
 *
 * @description 播放器那边的遮罩要按这个宽度给抽屉让出右侧（只让阴影接"点击关闭"），
 * 所以宽度放在这里共用，改一处两边都对。
 */
val EpisodeDrawerWidth = 320.dp

/**
 * 全屏选集抽屉。
 *
 * @description 与 web 的选集面板一致：标题栏带一条主色竖条 + 「共 N 集」，
 * 下面是按集数升序排列的数字按钮，**当前正在播放的那一集用主色高亮**。
 *
 * 只负责画内容与上报点击，不关心开关动画、遮罩与层级——
 * 那三件事由 [NekoPlayer] 负责（抽屉层级要高于控制栏，点抽屉外面才关闭）。
 *
 * @param episodes 全部剧集（已按集数升序）
 * @param currentEpisodeId 当前正在播放的剧集 id，用于高亮
 * @param onSelect 选中某一集（参数是剧集 id）
 * @param modifier 由播放器给出的尺寸约束（宽高在这里定）
 */
@Composable
fun EpisodeDrawer(
    episodes: List<Episode>,
    currentEpisodeId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }

    // 打开抽屉（以及切集）时，把当前集所在的那一行滑到顶部：
    // 上下滚动的列表这样才对得上"当前在播哪一集"，集数少到不用滚时自然没效果。
    LaunchedEffect(currentEpisodeId) {
        if (currentIndex >= 0) {
            gridState.animateScrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(EpisodeDrawerWidth)
            .background(EpisodePanelColor)
            // 面板整体吃掉触摸：点标题、点按钮之间的空隙都算"点在抽屉里"，
            // 既不会把抽屉关掉，也不会穿透到底下的播放/进度条/全屏按钮上。
            // 位置放在 background 之后、内容之前，不影响抽屉里列表的滚动（子节点先拿到 Main 事件）。
            .pointerInput(Unit) {
                detectTapGestures { /* 抽屉内容容器上的点击：什么都不做，只挡住穿透 */ }
            }
            .padding(16.dp),
    ) {
        // 标题：主色竖条 + 选集 + 共 N 集
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EpisodeActiveColor),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "选集",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "共 ${episodes.size} 集",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 用 LazyVerticalGrid：剧集多了自己会滚动，不用额外套 ScrollView
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { it.id }) { episode ->
                EpisodeButton(
                    episode = episode,
                    active = episode.id == currentEpisodeId,
                    onClick = { onSelect(episode.id) },
                )
            }
        }
    }
}

/**
 * 抽屉里的单个剧集按钮。
 *
 * @param episode 剧集
 * @param active 是否是当前正在播放的那一集
 * @param onClick 点击回调
 */
@Composable
private fun EpisodeButton(
    episode: Episode,
    active: Boolean,
    onClick: () -> Unit,
) {
    // 播放器控件一样不要涟漪：抽屉是浮层，涟漪会被遮罩挡住反而显脏
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) EpisodeActiveColor else Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = episode.number.toString(),
            color = if (active) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}
