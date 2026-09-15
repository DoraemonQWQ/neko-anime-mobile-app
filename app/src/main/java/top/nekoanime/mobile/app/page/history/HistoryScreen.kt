package top.nekoanime.mobile.app.page.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.component.rememberSaveableList
import top.nekoanime.mobile.app.component.HistorySkeleton
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.model.entity.WatchHistory

/** 历史记录每页条数（跟首页继续观看一个量级） */
private const val HistoryPageSize = 20

/**
 * 单条历史的高度。
 *
 * @description 封面与这一条**等高**（16:9 的封面按这个高度反推宽度），
 * 所以行高写死、封面用 fillMaxHeight 撑满，上下左右都不留缝，视觉上就是「封面贴边 + 右边信息」。
 */
private val HistoryRowHeight = 90.dp

/** 封面宽度（与行高构成 16:9，跟后端抽帧的剧集封面比例一致） */
private val HistoryCoverWidth = 160.dp

/**
 * 观看历史页。
 *
 * @description 顶栏是「返回 + 标题」，下面是分页列表：每条记录显示封面（16:9 抽帧图）、
 * 番剧名、第几集、进度/时长与一条进度条；点某条直接进那一集的播放页并续到当时的位置。
 * 列表滚到底自动加载下一页；下拉可以刷新（回到第一页重新拉）；
 * 未登录或没有记录时给出提示。
 *
 * @param onOpenEpisode 点某条记录：带着番剧 id、剧集 id 与进度进播放页
 * @param onBack 返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenEpisode: (animeId: Int, episodeId: Int, progressSeconds: Long) -> Unit,
    onBack: () -> Unit,
) {
    // 列表与分页状态都保存：从播放页返回时记录还在，不会重新拉第一页
    val items = rememberSaveableList<WatchHistory>("history_items")
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var refreshing by rememberSaveable { mutableStateOf(false) }
    var hasMore by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    BackHandler { onBack() }

    /** 加载下一页 */
    suspend fun loadNextPage() {
        if (loading || !hasMore) {
            return
        }

        loading = true
        val nextPage = page + 1

        UserRepository.history(page = nextPage, limit = HistoryPageSize)
            .onSuccess { list ->
                items.addAll(list)
                page = nextPage
                hasMore = list.size >= HistoryPageSize
                error = null
            }
            .onFailure { error = it.message ?: "加载失败" }

        loading = false
    }

    LaunchedEffect(Unit) {
        // 返回本页时列表还在就复用，不再请求一次
        if (items.isEmpty()) {
            loadNextPage()
        }
    }

    // 滚到底部（最后一条进入视野）才自动加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (items.isNotEmpty() && lastVisible >= items.size - 1) {
                    loadNextPage()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：内容 56dp、状态栏留白在外（跟其他页面一致）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "观看历史",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 下拉刷新：清空后重新拉第一页（跟分页加载复用同一个 loadNextPage）
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    items.clear()
                    page = 0
                    hasMore = true
                    error = null
                    loadNextPage()
                    refreshing = false
                }
            },
            // weight 而不是 fillMaxSize：顶栏已经占了一行，占满整列会把列表顶出屏幕
            modifier = Modifier.weight(1f),
        ) {
        when {
            // 加载中：先摆历史条目骨架
            items.isEmpty() && loading -> HistorySkeleton()

            items.isEmpty() -> Box(
                // 套一层（空的）滚动容器：没有可滚动子节点时下拉手势根本传不到刷新组件
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        // 未登录时后端返回 401，这里就是"还没有观看记录"的提示
                        text = error ?: "还没有观看记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    if (error != null) {
                        TextButton(onClick = { error = null; hasMore = true }) { Text("重试") }
                    }
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { "${it.animeId}-${it.episodeId}" }) { history ->
                    HistoryRow(
                        history = history,
                        onClick = {
                            onOpenEpisode(history.animeId, history.episodeId, history.progressSeconds)
                        },
                    )
                }

                // 底部状态：加载中 / 已经没有下一页了
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loading -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "加载中…",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            !hasMore -> Text(
                                text = "已经到底啦",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * 一条观看历史。
 *
 * @param history 记录
 * @param onClick 点击进播放页
 */
@Composable
private fun HistoryRow(history: WatchHistory, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val fraction = if (history.durationSeconds > 0) {
        (history.progressSeconds.toFloat() / history.durationSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HistoryRowHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            // 整行不再留内边距：封面要顶到卡片的上、下、左三个边
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(HistoryCoverWidth)
                // 跟行等高（不是按宽度算比例）：这样封面上下都贴边，不会出现上下留白
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CoverImage(
                url = history.coverUrl,
                contentDescription = history.animeTitle,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                // 撑满行高：进度条要贴在这一行的最底部
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = history.animeTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                // 显式给行高：默认行高来自 bodyLarge（24sp），两行标题会直接把进度行挤出卡片
                lineHeight = 17.sp,
                // 番剧名最多两行，超出用省略号（不要让它把下面的进度行顶出去）
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "第 ${history.episodeNumber} 集 ${history.episodeTitle}".trim(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                // 集数 + 剧集名强制单行：softWrap=false 保证不换行，超出直接省略号
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )

            // 中间留白把进度行顶到底部：标题/集数在上，进度贴底
            Spacer(modifier = Modifier.weight(1f))

            // 进度条与进度文字单独一行放在信息列里（原来嵌在封面上会被卡片圆角切掉一截）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }

                Text(
                    text = "${formatSeconds(history.progressSeconds)} / ${formatSeconds(history.durationSeconds)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 秒 → m:ss。
 *
 * @param seconds 秒
 * @returns 可读时长
 */
private fun formatSeconds(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)

    return "%d:%02d".format(safe / 60, safe % 60)
}
