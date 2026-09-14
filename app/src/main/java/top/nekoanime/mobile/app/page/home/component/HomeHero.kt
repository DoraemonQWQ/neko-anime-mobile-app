package top.nekoanime.mobile.app.page.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.model.entity.AnimeBrief

/** 手动滑动之后暂停自动轮播的时间（避免刚滑过去又被自动翻走） */
private const val ManualPauseMillis = 10_000L

/**
 * 首页头图轮播。
 *
 * @description 跟 Flutter 版的 Carousel 一个口径：取后端热门番剧，**数量与切换间隔都读配置**
 * （Flutter 那边是取前 5 个、3 秒一张）。整幅横幅 + 标题遮罩，用户手动滑动后会暂停一会儿再继续。
 * 底下是当前页的小圆点指示器。
 *
 * @param slides 轮播内容（后端热门番剧）
 * @param onOpenAnime 点击某一张（进播放页）
 * @param modifier 布局约束
 */
@Composable
fun HomeHero(
    slides: List<AnimeBrief>,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeConfig = ConfigLoader.config.home
    val items = remember(slides, homeConfig.carouselMax) { slides.take(homeConfig.carouselMax) }

    if (items.isEmpty()) {
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })

    // 自动轮播：拖动过程中不翻页；刚滑完等一会儿再继续
    LaunchedEffect(pagerState, items.size) {
        while (true) {
            delay(homeConfig.carouselIntervalMillis)

            if (pagerState.isScrollInProgress) {
                delay(ManualPauseMillis)
                continue
            }

            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 圆角加在**容器**上（整块轮播窗口），不是加在每一张图片上：
        // 这样滑动过程中圆角是固定的，图片从圆角窗口里滑过去，不会两个圆角各滑各的。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                HeroSlide(anime = items[page], onClick = { onOpenAnime(items[page].id) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val active = index == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        ),
                )
            }
        }
    }
}

/**
 * 单张轮播图：横版封面铺满 + 底部渐变 + 番剧名（+ 简介一行）。
 *
 * @param anime 番剧
 * @param onClick 点击
 */
@Composable
private fun HeroSlide(anime: AnimeBrief, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // 轮播用横版封面（16:9）；后端万一没给横版图就退回竖版（会被裁切，但总比空白好）
        CoverImage(
            url = anime.horizontalCoverUrl.ifBlank { anime.verticalCoverUrl },
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = anime.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (anime.description.isNotBlank()) {
                Text(
                    text = anime.description,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
