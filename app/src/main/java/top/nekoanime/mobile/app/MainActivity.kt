package top.nekoanime.mobile.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.UpdateDialog
import top.nekoanime.mobile.app.component.theme.NekoPlayerTheme
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.local.UpdatePreference
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import top.nekoanime.mobile.app.page.history.HistoryScreen
import top.nekoanime.mobile.app.page.home.HomeScreen
import top.nekoanime.mobile.app.page.home.HomeTab
import top.nekoanime.mobile.app.page.play.PlayRouteScreen
import top.nekoanime.mobile.app.page.search.SearchScreen
import top.nekoanime.mobile.app.page.settings.SettingsScreen
import top.nekoanime.mobile.app.page.splash.SplashScreen
import top.nekoanime.mobile.app.service.UpdateService
import top.nekoanime.mobile.app.state.SessionState

/**
 * 应用入口。
 *
 * @description 负责四件事：
 * 1. 启动前先读配置（接口地址、首页板块等都在 `assets/config/config.<模式>.json`）；
 * 2. 页面导航交给官方 Navigation Compose（类型安全路由见 {@link AppRoute}），
 *    这样每个页面有自己的返回栈与状态保存（返回时列表/滚动位置/搜索关键字都还在）；
 * 3. 返回键：二级页面逐层回退；回到首页这一层再按一次提示「再按一次退出应用」；
 * 4. 全屏：横屏 + 隐藏系统栏；系统栏图标的明暗按当前页面背景决定。
 */
class MainActivity : ComponentActivity() {
    /** 「再按一次退出应用」的确认窗口（毫秒） */
    private val exitConfirmMillis = 2000L

    /** 是否全屏（播放页用） */
    private var fullscreen by mutableStateOf(false)

    /** 当前是否在播放页（系统栏图标明暗要看它；更新弹窗也要避开这一页） */
    private var onPlayPage by mutableStateOf(false)

    /** 当前是否在首页（主动提示只在这一页弹；强制更新不看它） */
    private var onHomePage by mutableStateOf(true)

    /**
     * 待提示的新版本。
     *
     * @description 两个来源：启动页初始化阶段查到的（见 {@link SplashScreen}）、
     * 以及 App 从后台回到前台时补查到的。结果先存在这里：
     * 离开播放页后，强制更新立刻弹、主动提示等回到首页再弹。
     */
    private var pendingUpdate by mutableStateOf<AppUpdateDto?>(null)

    /** 是否已经走过一次 onStart（冷启动那次启动页已经查过了，不该再查一遍） */
    private var startedOnce = false

    /**
     * 首页选中的底部 Tab。
     *
     * @description 这个状态放在 Activity 而不是 HomeScreen 里：进二级页时 HomeScreen 会被移出组合树，
     * 返回时要能回到用户原来的 Tab（用户页进设置再返回，不该被弹回「主页」）。
     */
    private var homeTab by mutableStateOf(HomeTab.Home)

    /**
     * 搜索页的关键字。
     *
     * @description 只在这一页存活期间有意义：**离开搜索页就清空**，
     * 下次进来是干干净净的"待搜索"（见 [searchResetToken]）。
     */
    private var searchKeyword by mutableStateOf("")

    /**
     * 搜索页的重置计数。
     *
     * @description 搜索页的结果与输入框是 rememberSaveable 存着的，返回这一页时会被恢复；
     * 离开搜索页时把它 +1，回来时 composable 的 key 变了 → 整页状态重建，
     * 保证「每次进搜索都是待搜索」。纯数字，不参与界面渲染之外的事情。
     */
    private var searchResetToken by mutableStateOf(0)

    /** 上一次路由是不是搜索页（只在路由变化时用来判断"刚离开搜索页"） */
    private var wasOnSearchPage = false

    /** 是否已经按过一次返回键（用于「再按一次退出应用」） */
    private var exitPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先读配置再起界面：接口地址、首页要展示几组题材都来自配置文件
        ConfigLoader.init(applicationContext)

        // 再读登录态：本机有 token 就直接算已登录
        SessionState.init(applicationContext)

        enableEdgeToEdge()
        applySystemBarAppearance()

        // 看视频别锁屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            NekoPlayerTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val entry by navController.currentBackStackEntryAsState()

                // 路由变化时同步「当前是不是播放页 / 首页」，并重算系统栏图标明暗
                LaunchedEffect(entry) {
                    val onSearchPage = entry.isRoute<SearchRoute>()

                    // 刚离开搜索页：把关键字与整页状态一起丢掉，下次进来重新开始
                    if (wasOnSearchPage && !onSearchPage) {
                        searchKeyword = ""
                        searchResetToken += 1
                    }

                    wasOnSearchPage = onSearchPage
                    onPlayPage = entry.isRoute<PlayRoute>()
                    onHomePage = entry.isRoute<HomeRoute>()
                    applySystemBarAppearance()
                }

                /*
                 * 首页（Tab 页）的返回键：
                 * 1. 不在「主页」Tab 时先切回主页（不退出）；
                 * 2. 已经在主页时提示「再按一次退出应用」，2 秒内再按一次才真的退出。
                 * 二级页面由 NavHost 自己消费返回键（逐层弹栈），不会走到这里。
                 */
                BackHandler(enabled = entry.isRoute<HomeRoute>()) {
                    if (homeTab != HomeTab.Home) {
                        homeTab = HomeTab.Home
                        return@BackHandler
                    }

                    if (exitPending) {
                        finish()
                        return@BackHandler
                    }

                    exitPending = true
                    Toast.makeText(this@MainActivity, "再按一次退出应用", Toast.LENGTH_SHORT).show()

                    scope.launch {
                        delay(exitConfirmMillis)
                        exitPending = false
                    }
                }

                AppNavHost(
                    navController = navController,
                    fullscreen = fullscreen,
                    onToggleFullscreen = { applyFullscreen(!fullscreen) },
                    onReadyFromSplash = { update ->
                        // 启动页查到的版本跟「回到前台」查到的走同一套判断
                        applyUpdate(update)

                        navController.navigate(HomeRoute) {
                            // 启动页用完就从栈里去掉，返回键不会再回到它
                            popUpTo<SplashRoute> { inclusive = true }
                        }
                    },
                    homeTab = homeTab,
                    onHomeTabChange = { homeTab = it },
                    searchKeyword = searchKeyword,
                    searchResetToken = searchResetToken,
                    onSearchKeywordChange = { searchKeyword = it },
                )

                /*
                 * 待提示的版本怎么弹：
                 * - **播放页一律不弹**：用户正在看视频，弹什么更新都不合适；查到的先挂着，离开播放页再弹；
                 * - **强制更新**：除播放页外的任何页面，且不管有没有勾过「不再提示」，直接弹；
                 * - **主动提示**：只在首页弹（启动页查到的等进首页、回前台查到的等回到首页）。
                 */
                val update = pendingUpdate

                if (update != null && !onPlayPage && (update.force || onHomePage)) {
                    UpdateDialog(
                        info = update,
                        onDismiss = { pendingUpdate = null },
                    )
                }
            }
        }
    }

    /**
     * App 从后台回到前台。
     *
     * @description 冷启动那一次 onStart 紧跟在 onCreate 后面，启动页已经查过一遍更新了，
     * 这里跳过，免得同一秒查两次。之后每次回到前台都补查一次：
     * 用户挂着 App 好几天、期间服务端发了新版本，切回来就能看到提示。
     */
    override fun onStart() {
        super.onStart()

        if (!startedOnce) {
            startedOnce = true
            return
        }

        refreshUpdateOnForeground()
    }

    /**
     * 回到前台时补查一次更新。
     *
     * @description 查到的版本交给 [applyUpdate] 判断要不要提示；
     * 网络失败或已是最新时什么都不做（不回退掉已经在提示的那条）。
     */
    private fun refreshUpdateOnForeground() {
        lifecycleScope.launch {
            val result = UpdateService.check()

            result
                .onSuccess { info -> applyUpdate(info) }
                .onFailure { error -> Log.d("NekoUpdate", "回到前台检查更新失败：${error.message}") }
        }
    }

    /**
     * 判断检查到的版本要不要挂成待提示。
     *
     * @description 规则只有一条：强制更新无条件提示；主动提示要用户没勾过「7 天内不再提示」。
     * 至于"当前在不在首页 / 播放页"，是弹窗的时候才看（见 setContent 里的判断）。
     *
     * @param info 检查更新返回的版本信息；null 表示已是最新
     */
    private fun applyUpdate(info: AppUpdateDto?) {
        if (info == null) {
            return
        }

        if (!info.force && UpdatePreference(this).isSnoozed()) {
            Log.i("NekoUpdate", "回到前台发现新版本 ${info.version}，但用户勾过不再提示，跳过")
            return
        }

        pendingUpdate = info
    }

    /**
     * 系统暗亮模式变化。
     *
     * @description Activity 声明了 uiMode（切主题不重建 Activity），
     * 所以系统栏图标的明暗要自己补一次设置。
     *
     * @param newConfig 新配置
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarAppearance()
    }

    /**
     * 设置系统栏图标的明暗。
     *
     * @description 播放页顶部（状态栏那一条 + 播放器）是**固定黑底**，所以状态栏图标固定浅色；
     * 首页是主题背景（白/淡灰 or 深灰），所以按当前暗亮模式决定。导航栏一直在主题背景上，按暗亮模式走。
     */
    private fun applySystemBarAppearance() {
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        // 播放页：顶部永远黑 → 永远浅色图标；首页：亮色底用深色图标
        controller.isAppearanceLightStatusBars = if (onPlayPage) false else !darkMode
        controller.isAppearanceLightNavigationBars = !darkMode
    }

    /**
     * 切换全屏：横屏 + 隐藏系统栏。
     *
     * @param value 是否全屏
     */
    private fun applyFullscreen(value: Boolean) {
        fullscreen = value
        requestedOrientation = if (value) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (value) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * 判断当前返回栈条目是不是某个路由（类型安全路由的通用判断）。
 *
 * @param route 路由类型
 * @returns 命中返回 true
 */
private inline fun <reified T : Any> NavBackStackEntry?.isRoute(): Boolean =
    this?.destination?.hasRoute(T::class) == true

/**
 * 全站导航图。
 *
 * @description 转场动画保持跟自研路由时期一致：
 * - 启动页 → 首页：纯淡入淡出；
 * - 搜索页：进入/退出用缩放（像弹出来一样）；
 * - 其它页面：前进从右侧滑入、返回向左滑出；
 * - **回到首页**（点播放页的「主页」按钮或逐层返回）统一用「返回」动画，不做前进动画。
 *
 * @param navController 导航控制器
 * @param fullscreen 是否全屏（播放页用）
 * @param onToggleFullscreen 切换全屏
 * @param onReadyFromSplash 启动页初始化完成回调
 * @param homeTab 首页选中的 Tab
 * @param onHomeTabChange 切换首页 Tab
 * @param searchKeyword 搜索页关键字
 * @param searchResetToken 搜索页重置计数：值一变，搜索页整页状态重建（离开搜索页时 +1）
 * @param onSearchKeywordChange 搜索关键字变化回调
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onReadyFromSplash: (AppUpdateDto?) -> Unit,
    homeTab: HomeTab,
    onHomeTabChange: (HomeTab) -> Unit,
    searchKeyword: String,
    searchResetToken: Int,
    onSearchKeywordChange: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        enterTransition = { enterAnimation() },
        exitTransition = { exitAnimation() },
        popEnterTransition = { popEnterAnimation() },
        popExitTransition = { popExitAnimation() },
    ) {
        composable<SplashRoute> {
            SplashScreen(onReady = onReadyFromSplash)
        }

        composable<HomeRoute> {
            HomeScreen(
                onOpenAnime = { animeId -> navController.navigate(PlayRoute(animeId = animeId)) },
                onContinue = { history ->
                    // 继续观看：把集数和进度带过去，播放页直接跳到那里（不看本地/线上）
                    navController.navigate(
                        PlayRoute(
                            animeId = history.animeId,
                            startEpisodeId = history.episodeId,
                            startPositionMs = history.progressSeconds * 1000,
                        ),
                    )
                },
                onOpenSearch = { navController.navigate(SearchRoute) },
                selectedTab = homeTab,
                onTabChange = onHomeTabChange,
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }

        composable<SearchRoute> {
            /*
             * 用 key 把整页状态挂在"重置计数"上：离开搜索页时计数 +1，
             * 下次进来 key 变了，SearchScreen 里那些 rememberSaveable（输入框、结果、分页）
             * 会全部重建 —— 这就是"每次进搜索都是待搜索"的实现方式。
             */
            key(searchResetToken) {
                SearchScreen(
                    onOpenAnime = { animeId -> navController.navigate(PlayRoute(animeId = animeId)) },
                    onBack = { navController.popBackStack() },
                    initialKeyword = searchKeyword,
                    onKeywordChange = onSearchKeywordChange,
                )
            }
        }

        composable<HistoryRoute> {
            HistoryScreen(
                // 点某条历史：带着那一集的 id 和当时的进度进播放页（跟 web 的继续观看一致）
                onOpenEpisode = { animeId, episodeId, progressSeconds ->
                    navController.navigate(
                        PlayRoute(
                            animeId = animeId,
                            startEpisodeId = episodeId,
                            startPositionMs = progressSeconds * 1000,
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<PlayRoute> { entry ->
            val play = entry.toRoute<PlayRoute>()

            PlayRouteScreen(
                animeId = play.animeId,
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onBack = {
                    if (fullscreen) {
                        onToggleFullscreen()
                    } else {
                        navController.popBackStack()
                    }
                },
                // 一键回主页：直接弹回栈里的首页（而不是再压一个新的首页），
                // 走的是 pop，所以动画就是正常的「返回」动画，且首页原有的数据/滚动位置都还在
                onHome = {
                    if (fullscreen) {
                        onToggleFullscreen()
                    }

                    navController.popBackStack(HomeRoute, inclusive = false)
                },
                // 播放页底部的推荐：压一层新的播放页，返回时回到上一个番剧（层级返回）
                onOpenAnime = { animeId -> navController.navigate(PlayRoute(animeId = animeId)) },
                startEpisodeId = play.startEpisodeId,
                startPositionMs = play.startPositionMs,
            )
        }
    }
}

/** 目标是不是首页（回到首页统一用返回动画） */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.targetIsHome(): Boolean =
    targetState.destination.hasRoute(HomeRoute::class)

/** 起点是不是首页 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.initialIsHome(): Boolean =
    initialState.destination.hasRoute(HomeRoute::class)

/** 目标是不是搜索页（搜索页用缩放过渡） */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.targetIsSearch(): Boolean =
    targetState.destination.hasRoute(SearchRoute::class)

/** 起点是不是搜索页 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.initialIsSearch(): Boolean =
    initialState.destination.hasRoute(SearchRoute::class)

/** 起点是不是启动页（启动页 → 首页用淡入淡出） */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.initialIsSplash(): Boolean =
    initialState.destination.hasRoute(SplashRoute::class)

/**
 * 转场时长（毫秒）。
 *
 * @description 全站统一一个数值：进、出、返回、被覆盖都用它，位移与淡入淡出也用同一个时长——
 * 之前退出那条淡出写的 200ms、滑出 260ms（淡出先结束），看起来就是"进慢出快"。
 */
private const val SearchTransitionMillis = 260

/**
 * 搜索页出现时的动画（前进进入、返回回来都用它）。
 *
 * @returns 从 0.94 放大到 1 + 淡入
 */
private fun searchEnterTransition(): EnterTransition =
    scaleIn(initialScale = 0.94f, animationSpec = tween(SearchTransitionMillis)) +
        fadeIn(tween(SearchTransitionMillis))

/**
 * 搜索页消失时的动画（被覆盖、被返回都用它）。
 *
 * @returns 从 1 缩小到 0.94 + 淡出
 */
private fun searchExitTransition(): ExitTransition =
    scaleOut(targetScale = 0.94f, animationSpec = tween(SearchTransitionMillis)) +
        fadeOut(tween(SearchTransitionMillis))

/**
 * 前进时的「进入」动画。
 *
 * @returns 进入动画
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterAnimation(): EnterTransition = when {
    // 启动页 → 首页：纯淡入淡出（两边时长一致才是干净的交叠淡出，没有位移）
    initialIsSplash() -> fadeIn(tween(260))
    // 搜索页：进、出统一用 searchEnterTransition / searchExitTransition
    targetIsSearch() -> searchEnterTransition()
    initialIsSearch() -> searchEnterTransition()
    // 回到首页：按返回动画（从左侧滑入），不要用前进动画
    targetIsHome() -> slideInHorizontally(animationSpec = tween(260)) { -it } + fadeIn(tween(260))
    else -> slideInHorizontally(animationSpec = tween(260)) { it } + fadeIn(tween(260))
}

/**
 * 前进时的「退出」动画。
 *
 * @returns 退出动画
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitAnimation(): ExitTransition = when {
    initialIsSplash() -> fadeOut(tween(260))
    targetIsSearch() -> searchExitTransition()
    initialIsSearch() -> searchExitTransition()
    // 目标变回首页时，当前页按「被返回」的方向滑出
    targetIsHome() -> slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(tween(260))
    else -> slideOutHorizontally(animationSpec = tween(260)) { -it } + fadeOut(tween(260))
}

/**
 * 返回时的「进入」动画。
 *
 * @returns 进入动画
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterAnimation(): EnterTransition = when {
    /*
     * 从搜索页返回：下面的页面也要用搜索页那一套（缩放 + 淡入）。
     * 只处理 targetIsSearch() 是不够的——返回时 initial 才是搜索页，
     * 少这条就会出现"搜索页缩着消失、下面页面却整体滑进来"的错配。
     */
    initialIsSearch() -> searchEnterTransition()
    targetIsSearch() -> searchEnterTransition()
    else -> slideInHorizontally(animationSpec = tween(260)) { -it } + fadeIn(tween(260))
}

/**
 * 返回时的「退出」动画。
 *
 * @returns 退出动画
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitAnimation(): ExitTransition = when {
    initialIsSearch() -> searchExitTransition()
    else -> slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(tween(260))
}
