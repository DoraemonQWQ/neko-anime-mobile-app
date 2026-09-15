package top.nekoanime.mobile.app.page.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.R
import top.nekoanime.mobile.app.component.AnimePosterCard
import top.nekoanime.mobile.app.component.FilterSelector
import top.nekoanime.mobile.app.component.LoginDialog
import top.nekoanime.mobile.app.component.rememberSaveableList
import top.nekoanime.mobile.app.config.AnimeFilterOptions
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.model.entity.AnimeBrief
import top.nekoanime.mobile.app.model.entity.WatchHistory
import top.nekoanime.mobile.app.page.home.component.AnimeRow
import top.nekoanime.mobile.app.page.home.component.ContinueWatchingRow
import top.nekoanime.mobile.app.page.home.component.HomeHero
import top.nekoanime.mobile.app.page.home.component.HomeTopBar
import top.nekoanime.mobile.app.page.user.UserTab
import top.nekoanime.mobile.app.state.SessionState
import kotlinx.serialization.Serializable

/** 首页内容区左右/上下内边距（跟 Flutter 版 UiConst.kPadding 一致：8dp） */
private val PagePadding = 8.dp

/**
 * 首页的一组题材推荐（标题 + 番剧列表）。
 *
 * @description 抽成可序列化的数据类，页面状态才能存进 rememberSaveable（返回时不用重新请求）。
 *
 * @param title 板块标题（前缀 + 题材）
 * @param animes 这一行的番剧
 */
@Serializable
data class HomeRecommendRow(val title: String, val animes: List<AnimeBrief>)

/** 板块之间的间隔（Flutter 版用 10dp） */
private val SectionSpacing = 10.dp

/** 底部 Tab 内容高度（Flutter 的 kBottomNavigationBarHeight = 56） */
private val BottomBarContentHeight = 56.dp

/** 筛选栏高度（Flutter 版用的是 kToolbarHeight = 56） */
private val FilterBarHeight = 56.dp

/** 底部三个 Tab（图标沿用 Flutter 版 assets/images 里那三张，不写文字） */
enum class HomeTab(val label: String, val iconRes: Int) {
    Home("主页", R.drawable.ic_tab_home),
    AnimeList("番剧", R.drawable.ic_tab_anime),
    User("用户", R.drawable.ic_tab_user),
}

/**
 * 首页（底部 Tab 容器）。
 *
 * @description 结构对齐 Flutter 版：
 * - 顶部一条 AppBar（头像 + 假搜索框）；
 * - 底部三个 Tab（主页 / 番剧 / 用户），**只用图标**、标签始终隐藏；
 * - 主页 = 轮播 + 继续观看 + 若干题材推荐行（题材与标题前缀都读配置）；
 * - 番剧 = 全部动漫（三列网格，滚到底自动翻页）；
 * - 用户 = 个人信息（登录/退出、历史、设置入口）。
 *
 * 头像的行为：没登录 → 弹登录框；已登录 → 切到「用户」Tab。
 *
 * @param onOpenAnime 点番剧卡片（进播放页）
 * @param onContinue 点继续观看卡片（带着集数与进度进播放页）
 * @param onOpenSearch 打开搜索页（顶栏那个假搜索框）
 * @param selectedTab 当前选中的底部 Tab（由外层持有：从二级页返回时要能回到原来的 Tab）
 * @param onTabChange 切换底部 Tab
 * @param onOpenHistory 打开观看历史页
 * @param onOpenSettings 打开设置页
 */
@Composable
fun HomeScreen(
    onOpenAnime: (Int) -> Unit,
    onContinue: (WatchHistory) -> Unit,
    onOpenSearch: () -> Unit,
    selectedTab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showLogin by remember { mutableStateOf(false) }
    // 底部 Tab 的点击互动源（三个 Tab 共用一个就够，播放器控件那样不要涟漪）
    val bottomBarInteraction = remember { MutableInteractionSource() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                loggedIn = SessionState.loggedIn,
                avatarUrl = SessionState.profile?.avatarUrl,
                onAvatarClick = {
                    if (SessionState.loggedIn) {
                        onTabChange(HomeTab.User)
                    } else {
                        showLogin = true
                    }
                },
                onSearchClick = onOpenSearch,
            )
        },
        bottomBar = {
            // 底部栏：内容高 56dp（跟 Flutter 的 kBottomNavigationBarHeight 一致）。
            // 导航栏留白加在**外面**——否则 56 里还要塞进系统导航栏的高度，图标会被压扁。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BottomBarContentHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeTab.entries.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = bottomBarInteraction,
                                    indication = null,
                                    onClick = { onTabChange(item) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 选中态：一个小胶囊（跟 M3 / Flutter 的 indicator 一致）
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selectedTab == item) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = item.iconRes),
                                    contentDescription = item.label,
                                    tint = if (selectedTab == item) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                HomeTab.Home -> HomeTabContent(onOpenAnime = onOpenAnime, onContinue = onContinue)
                HomeTab.AnimeList -> AnimeListTab(onOpenAnime = onOpenAnime)
                HomeTab.User -> UserTab(
                    loggedIn = SessionState.loggedIn,
                    username = SessionState.profile?.displayName,
                    avatarUrl = SessionState.profile?.avatarUrl,
                    onLoginClick = { showLogin = true },
                    onLogout = { SessionState.logout() },
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    if (showLogin) {
        LoginDialog(onDismiss = { showLogin = false })
    }
}

/**
 * 主页内容：轮播 → 继续观看 → 题材推荐（配置里配几组就几组）。
 *
 * @param onOpenAnime 点番剧卡片
 * @param onContinue 点继续观看
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTabContent(
    onOpenAnime: (Int) -> Unit,
    onContinue: (WatchHistory) -> Unit,
) {
    val homeConfig = ConfigLoader.config.home

    // 这些列表用 rememberSaveable 存：从二级页返回时直接复用，不再请求一次、也不会白屏一下
    val hot = rememberSaveableList<AnimeBrief>("home_hot")
    val rows = rememberSaveableList<HomeRecommendRow>("home_rows")
    val continueWatching = rememberSaveableList<WatchHistory>("home_continue")
    var loading by rememberSaveable { mutableStateOf(true) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var loadedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        // 已经有数据（从二级页返回）且不是手动刷新：直接用缓存，不再打接口
        if (loadedOnce && reloadKey == 0) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        failed = false

        // 1) 轮播：后端热门，最多 carouselMax 个
        val hotResult = AnimeRepository.hot()
        hot.clear()
        hot.addAll(hotResult.getOrDefault(emptyList()))

        // 2) 题材推荐：配置里配了几个题材就几组，标题 = 前缀 + 题材
        rows.clear()
        rows.addAll(
            homeConfig.recommendTags.mapNotNull { tag ->
                AnimeRepository.recommendByTag(tag)
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { HomeRecommendRow("${homeConfig.recommendTitlePrefix}$tag", it) }
            },
        )

        // 3) 继续观看：需要登录；未登录后端会 401，这里就是空列表（整块不显示）
        continueWatching.clear()
        continueWatching.addAll(
            UserRepository.history(limit = homeConfig.continueWatchingMax).getOrDefault(emptyList()),
        )

        failed = hotResult.isFailure && rows.isEmpty()
        loading = false
        loadedOnce = true
    }

    // 下拉刷新：重新拉轮播 / 推荐 / 继续观看（已有内容时才显示刷新指示器，首次加载走中间的转圈）
    PullToRefreshBox(
        isRefreshing = loading && (hot.isNotEmpty() || rows.isNotEmpty()),
        onRefresh = { reloadKey++ },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PagePadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
        when {
            loading && hot.isEmpty() && rows.isEmpty() ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

            failed -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂时拿不到番剧数据，请稍后再试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { reloadKey++ }) { Text("重新加载") }
            }

            else -> {
                HomeHero(slides = hot, onOpenAnime = onOpenAnime)

                ContinueWatchingRow(items = continueWatching, onContinue = onContinue)

                rows.forEach { row ->
                    AnimeRow(title = row.title, animes = row.animes, onOpenAnime = onOpenAnime)
                }
            }
        }
        }
    }
}

/**
 * 「番剧」Tab：全部动漫，三列网格 + 滚到底自动加载下一页。
 *
 * @param onOpenAnime 点番剧卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeListTab(onOpenAnime: (Int) -> Unit) {
    val pageSize = ConfigLoader.config.home.listPageSize

    // 列表与分页状态都保存：从播放页返回时列表还在、滚动位置也还在，不会重新从第一页加载
    val items = rememberSaveableList<AnimeBrief>("anime_list_items")
    var page by rememberSaveable { mutableIntStateOf(0) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var hasMore by rememberSaveable { mutableStateOf(true) }
    var refreshing by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    // 下拉刷新：加一个自增 key 让下面的 LaunchedEffect 重新跑一遍（清空 + 拉第一页）
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    // 已经处理过的刷新 key：返回本页时如果没变过，就直接复用已有列表
    var handledRefreshKey by rememberSaveable { mutableIntStateOf(0) }

    // 筛选项（跟 Flutter 版一致：年份 / 月份 / 类型 / 来源 / 分类）
    var year by rememberSaveable { mutableStateOf("all") }
    var month by rememberSaveable { mutableStateOf("all") }
    var type by rememberSaveable { mutableStateOf("all") }
    var source by rememberSaveable { mutableStateOf("all") }
    var category by rememberSaveable { mutableStateOf("all") }

    /** 加载下一页（正在加载或没有下一页时直接返回） */
    suspend fun loadNextPage() {
        if (loading || !hasMore) {
            return
        }

        loading = true
        val nextPage = page + 1

        AnimeRepository.list(
            page = nextPage,
            limit = pageSize,
            year = year,
            month = month,
            type = type,
            source = source,
            category = category,
        )
            .onSuccess { paged ->
                items.addAll(paged.items)
                page = nextPage
                hasMore = paged.hasMore
                error = null
            }
            .onFailure { error = it.message ?: "加载失败" }

        loading = false
    }

    // 首次进入 + 筛选变化 + 下拉刷新：清空重新拉第一页；
    // 从二级页返回时（列表还在、条件没变）直接复用，不再请求
    LaunchedEffect(year, month, type, source, category, refreshKey) {
        if (items.isNotEmpty() && refreshKey == handledRefreshKey) {
            return@LaunchedEffect
        }

        items.clear()
        page = 0
        hasMore = true
        loadNextPage()
        handledRefreshKey = refreshKey
        refreshing = false
    }

    // 滑到底部（最后一项进入视野）才请求下一页：不提前预取，翻页动作跟用户看到的完全对应
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (items.isNotEmpty() && lastVisible >= items.size - 1) {
                    loadNextPage()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 筛选栏：横向可滑（跟 Flutter 版一致：高度 56、内边距 8）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FilterBarHeight)
                .horizontalScroll(rememberScrollState())
                .padding(PagePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterSelector(
                label = "年份",
                options = AnimeFilterOptions.year,
                value = year,
                onSelect = { year = it },
            )
            FilterSelector(
                label = "月份",
                options = AnimeFilterOptions.month,
                value = month,
                onSelect = { month = it },
            )
            FilterSelector(
                label = "类型",
                options = AnimeFilterOptions.type,
                value = type,
                onSelect = { type = it },
            )
            FilterSelector(
                label = "来源",
                options = AnimeFilterOptions.source,
                value = source,
                onSelect = { source = it },
            )
            FilterSelector(
                label = "分类",
                options = AnimeFilterOptions.category,
                value = category,
                onSelect = { category = it },
            )
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                refreshKey += 1
            },
            // weight 而不是 fillMaxSize：列里已经有一条筛选栏，占满整列会把列表顶出屏幕
            modifier = Modifier.weight(1f),
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(PagePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        items(items, key = { it.id }) { anime ->
            AnimePosterCard(
                anime = anime,
                onClick = { onOpenAnime(anime.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 底部状态：加载中 / 加载失败可重试 / 已经没有下一页了
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
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

                    error != null -> TextButton(onClick = { error = null; hasMore = true }) {
                        Text("加载失败，点这里重试")
                    }

                    !hasMore && items.isNotEmpty() -> Text(
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
