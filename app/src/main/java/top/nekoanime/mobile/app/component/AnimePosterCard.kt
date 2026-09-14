package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.model.entity.AnimeBrief

/** 海报卡片在横滑行里的标准宽度（网格里由调用方给等分宽度） */
val PosterCardWidth = 120.dp

/** 竖版封面比例（Bangumi 封面约 2:3） */
private const val PosterAspectRatio = 0.72f

/** 标题固定两行高度：一行还是两行都不会让整排卡片高低不齐 */
private val PosterTitleHeight = 50.dp

/**
 * 评分文字（跟 Flutter 版 anime_card 一样：右下角、粗体斜体、白色）。
 *
 * @param score 评分（Bangumi 百分制转十分制的小数）
 * @returns 形如 7.5 的文本
 */
private fun formatScore(score: Double): String = "%.1f".format(score)

/**
 * 番剧海报卡片（竖版封面 + 番剧名）。
 *
 * @description 首页的题材推荐行、"全部动漫"网格、播放页底部的推荐都复用这一张卡片，
 * 保证全站卡片长得一样。
 * 封面底部压一条渐变遮罩显示评分（对齐 Flutter 版 anime_card 的做法）：
 * 遮罩只覆盖评分那一小块，不能太高，否则会把封面吃没了。
 *
 * @param anime 番剧简介
 * @param onClick 点击（进播放页）
 * @param modifier 布局约束
 * （宽度由调用方给：横滑行用 {@link PosterCardWidth}，网格用等分宽度）
 */
@Composable
fun AnimePosterCard(
    anime: AnimeBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            // 卡片底色：跟 web 的番剧卡片一样给一块 surface 底。
            // 没有底色时卡片和页面背景（light 下都是浅色）糊在一起，标题文字看着像浮在空白上。
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PosterAspectRatio)
                // 图片不做圆角：圆角是卡片的事，图片通铺到卡片边缘（跟 web 的卡片规则一致）
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(
                url = anime.verticalCoverUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
            )

            // 没有评分（后端给 0）就不画遮罩：一条黑边压着封面反而更难看
            anime.score?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.6f),
                                ),
                                // 停在 0 / 0.5 / 1，跟 Flutter 版的 stops 一一对应
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            ),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Text(
                        text = formatScore(score),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                    )
                }
            }
        }

        // 文字区高度固定（保证整排卡片一样高），但文字本身**垂直居中**、水平仍然靠左：
        // 只有一行时不至于下面留一大截空白，两行时也居中更好看。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosterTitleHeight)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // 最多两行，多的省略
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
