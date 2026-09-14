package top.nekoanime.mobile.app.page.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.nekoanime.mobile.app.component.AnimePosterCard
import top.nekoanime.mobile.app.component.PosterCardWidth
import top.nekoanime.mobile.app.component.SectionHeader
import top.nekoanime.mobile.app.model.entity.AnimeBrief

/**
 * 一行横滑的番剧卡片（首页的题材推荐、继续观看都用它）。
 *
 * @param title 行标题（题材名 / 继续观看）
 * @param animes 数据
 * @param onOpenAnime 点击某张卡片
 * @param modifier 布局约束
 * @param actionText 右侧按钮文案（例如「更多」）
 * @param onAction 右侧按钮点击
 * @param headerContent 自定义头部内容（继续观看用，普通题材行不用传）
 */
@Composable
fun AnimeRow(
    title: String,
    animes: List<AnimeBrief>,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    if (animes.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (headerContent != null) {
            headerContent()
        } else {
            SectionHeader(
                title = title,
                actionText = actionText,
                onAction = onAction,
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(animes, key = { it.id }) { anime ->
                AnimePosterCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    modifier = Modifier.width(PosterCardWidth),
                )
            }
        }
    }
}
