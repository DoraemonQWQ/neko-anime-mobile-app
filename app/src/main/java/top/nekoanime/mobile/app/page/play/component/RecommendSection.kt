package top.nekoanime.mobile.app.page.play.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.nekoanime.mobile.app.component.AnimePosterCard
import top.nekoanime.mobile.app.component.PosterCardWidth
import top.nekoanime.mobile.app.component.SectionHeader
import top.nekoanime.mobile.app.model.entity.AnimeBrief

/**
 * 播放页底部的推荐列表。
 *
 * @description 推荐数据由后端按当前番剧的题材随机挑（自动排除自己），
 * 这里只负责横滑展示；卡片样式跟首页完全一致（同一个 {@link AnimePosterCard}）。
 *
 * @param items 推荐结果
 * @param onSelect 点击某个推荐（参数是番剧 id）
 * @param modifier 布局约束
 */
@Composable
fun RecommendSection(
    items: List<AnimeBrief>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
    ) {
        SectionHeader(
            title = "推荐",
            subtitle = "按题材",
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { anime ->
                AnimePosterCard(
                    anime = anime,
                    onClick = { onSelect(anime.id) },
                    modifier = Modifier.width(PosterCardWidth),
                )
            }
        }
    }
}
