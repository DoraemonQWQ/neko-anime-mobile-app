package top.nekoanime.mobile.app.page.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.AnimePosterCard
import top.nekoanime.mobile.app.component.appendDistinct
import top.nekoanime.mobile.app.component.NekoSearchBar
import top.nekoanime.mobile.app.component.rememberSaveableList
import top.nekoanime.mobile.app.component.AnimeGridSkeleton
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.model.entity.AnimeBrief

/**
 * 搜索页。
 *
 * @description 从首页那个"假搜索框"进来，这里才是真的输入框：输入关键字回车 → 调后端搜索接口，
 * 结果是三列网格，滚到底自动加载下一页。进入页面自动聚焦并弹出键盘（跟常见 App 一致）。
 * 关键字由外层保存：从结果里进播放页再返回时，这里能接着上次的关键字重新搜一遍，
 * 而不是回到一个空白搜索页。
 *
 * @param onOpenAnime 点搜索结果（进播放页）
 * @param onBack 返回首页
 * @param initialKeyword 进入本页时的关键字；空串表示"待搜索"（离开搜索页时外层会把它清空）
 * @param onKeywordChange 关键字变化时同步给外层（外层只在本页存活期间保留）
 */
@Composable
fun SearchScreen(
    onOpenAnime: (Int) -> Unit,
    onBack: () -> Unit,
    initialKeyword: String = "",
    onKeywordChange: (String) -> Unit = {},
) {
    val pageSize = ConfigLoader.config.home.listPageSize

    var keyword by rememberSaveable { mutableStateOf(initialKeyword) }
    var submittedKeyword by rememberSaveable { mutableStateOf(initialKeyword.trim()) }

    // 结果与分页状态在本页存活期间保留（转屏、被系统回收再恢复时不至于丢）；
    // 离开搜索页时外层会把整页重置，所以下次进来一定是"待搜索"（见 MainActivity.searchResetToken）
    val results = rememberSaveableList<AnimeBrief>("search_results")
    var page by rememberSaveable { mutableIntStateOf(0) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var hasMore by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // 带了关键字进来（外层没清空的那条路径）就直接算"搜过了"
    var searched by rememberSaveable { mutableStateOf(initialKeyword.isNotBlank()) }
    // 上一次已经搜过的关键字：返回本页时如果没变过就直接用已有结果
    var loadedKeyword by rememberSaveable { mutableStateOf("") }

    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    /** 按当前关键字加载下一页 */
    suspend fun loadNextPage() {
        if (loading || !hasMore || submittedKeyword.isBlank()) {
            return
        }

        loading = true
        val nextPage = page + 1

        AnimeRepository.search(submittedKeyword, page = nextPage, limit = pageSize)
            .onSuccess { paged ->
                // 同"全部动漫"页：搜索结果也是按评分分页，边界可能重复，去重后再入列
                results.appendDistinct(paged.items) { it.id }
                page = nextPage
                hasMore = paged.hasMore
                error = null
            }
            .onFailure { error = it.message ?: "搜索失败" }

        loading = false
    }

    /** 发起一次新搜索 */
    fun startSearch() {
        val trimmed = keyword.trim()

        if (trimmed.isBlank()) {
            return
        }

        keyboard?.hide()
        results.clear()
        page = 0
        hasMore = true
        error = null
        searched = true
        submittedKeyword = trimmed
    }

    LaunchedEffect(submittedKeyword) {
        if (submittedKeyword.isNotBlank()) {
            if (results.isNotEmpty() && submittedKeyword == loadedKeyword) {
                return@LaunchedEffect
            }

            hasMore = true
            loadNextPage()
            loadedKeyword = submittedKeyword
        }
    }

    // 滚到底附近自动加载下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (results.isNotEmpty() && lastVisible >= results.size - 4) {
                    loadNextPage()
                }
            }
    }

    // 进页面聚焦搜索框（"待搜索"状态要把键盘直接顶出来）；带了关键字时不弹键盘
    LaunchedEffect(Unit) {
        if (initialKeyword.isBlank()) {
            focusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏：对着 Flutter 版的 common_app_bar + neko_search_bar 写——
        // 内容高 56dp（状态栏留白在外）、左右内边距 16dp、返回按钮 40dp 宽、输入框占满剩余宽度
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))

            NekoSearchBar(
                placeholder = "请输入番剧名",
                value = keyword,
                onValueChange = {
                    keyword = it
                    onKeywordChange(it)
                },
                onSubmit = { startSearch() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        when {
            !searched -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "请搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            results.isEmpty() && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                // 搜索中：先摆卡片骨架，结果出来时是「填充」而不是「蹦出来」
                AnimeGridSkeleton()
            }

            results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "没有找到「$submittedKeyword」相关的番剧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.id }) { anime ->
                    AnimePosterCard(
                        anime = anime,
                        onClick = { onOpenAnime(anime.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (error != null && !loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { scope.launch { loadNextPage() } }) {
                                Text("加载失败，点这里重试")
                            }
                        }
                    }
                }
            }
        }
    }
}
