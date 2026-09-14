package top.nekoanime.mobile.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.theme.NekoPlayerTheme
import top.nekoanime.mobile.app.component.UpdateDialog
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import top.nekoanime.mobile.app.page.history.HistoryScreen
import top.nekoanime.mobile.app.page.home.HomeScreen
import top.nekoanime.mobile.app.page.home.HomeTab
import top.nekoanime.mobile.app.page.play.PlayRoute
import top.nekoanime.mobile.app.page.search.SearchScreen
import top.nekoanime.mobile.app.page.settings.SettingsScreen
import top.nekoanime.mobile.app.page.splash.SplashScreen
import top.nekoanime.mobile.app.state.SessionState

/**
 * 应用入口。
 *
 * @description 负责四件事：
 * 1. 启动前先读配置（接口地址、首页板块等都在 `assets/config/config.<模式>.json`）；
 * 2. 页面路由：自己维护一个**返回栈**（见 {@link backStack}），支持层级返回；
 * 3. 返回键：二级页面逐层回退；回到首页再按一次提示「再按一次退出应用」；
 * 4. 全屏：横屏 + 隐藏系统栏；系统栏图标的明暗按当前页面背景决定。
 */
class MainActivity : ComponentActivity() {
    /** 「再按一次退出应用」的确认窗口（毫秒） */
    private val exitConfirmMillis = 2000L

    /** 是否全屏（播放页用） */
    private var fullscreen by mutableStateOf(false)

    /**
     * 页面返回栈（栈顶就是当前页面）。
     *
     * @description 用栈而不是单个状态，是为了「层级返回」：
     * 播放页里的推荐点进去会再压一层播放页，返回时回到上一个播放页，而不是直接回首页；
     * 搜索页 → 播放页 返回也是回到搜索页（带着上次的关键字）。
     */
    private val backStack = mutableStateListOf<AppRoute>(AppRoute.Splash)

    /** 当前页面（返回栈栈顶） */
    private val route: AppRoute get() = backStack.last()

    /**
     * 本次切换是「进入下一层」还是「返回上一层」。
     *
     * @description 转场动画要用它决定从左边还是右边滑入；
     * 栈顶的 route 判断不出方向（播放页 → 播放页 的数据类不相等但都属于前进）。
     */
    private var navForward by mutableStateOf(true)

    /** 是否已经按过一次返回键（用于「再按一次退出应用」） */
    private var exitPending by mutableStateOf(false)

    /** 搜索页上次用的关键字：返回搜索页时接着用，避免回到空白页 */
    private var searchKeyword by mutableStateOf("")

    /**
     * 启动时检查到的新版本。
     *
     * @description 启动页在初始化阶段就发起检查（见 {@link SplashScreen}），
     * 检查结果先存在这里：进首页之后再弹更新窗，避免弹窗压在启动页上。
     */
    private var pendingUpdate by mutableStateOf<AppUpdateDto?>(null)

    /**
     * 首页选中的底部 Tab。
     *
     * @description 这个状态放在 Activity 而不是 HomeScreen 里：进二级页（历史/设置）时
     * HomeScreen 会被移出组合树，返回时 remember 的状态就没了，用户会莫名其妙被弹回「主页」。
     */
    private var homeTab by mutableStateOf(HomeTab.Home)

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
                val scope = rememberCoroutineScope()

                // 换页面时重算系统栏图标明暗（播放页顶部是黑底，首页是主题背景）
                LaunchedEffect(route) {
                    applySystemBarAppearance()
                }

                /*
                 * 首页（Tab 页）的返回键：
                 * 1. 不在「主页」Tab 时先切回主页（不退出）；
                 * 2. 已经在主页时提示「再按一次退出应用」，2 秒内再按一次才真的退出。
                 * 二级页面各自有 BackHandler，会先把返回键消费掉，不会走到这里。
                 */
                BackHandler(enabled = route is AppRoute.Home) {
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

                // 页面转场动画：进二级页从右侧滑入，返回时反向滑出
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        // 搜索页用「缩放」过渡（点假搜索框进去时像弹出来一样），其余二级页仍旧左右滑
                        when {
                            // 启动页 → 首页：纯淡入淡出（两边时长一致才是干净的交叠淡出，没有位移）
                            initialState is AppRoute.Splash ->
                                fadeIn(tween(300)) togetherWith fadeOut(tween(300))

                            targetState is AppRoute.Search ->
                                (scaleIn(initialScale = 0.92f, animationSpec = tween(220)) + fadeIn(tween(220))) togetherWith
                                    (scaleOut(targetScale = 1.04f, animationSpec = tween(180)) + fadeOut(tween(160)))

                            initialState is AppRoute.Search ->
                                (scaleIn(initialScale = 1.04f, animationSpec = tween(200)) + fadeIn(tween(200))) togetherWith
                                    (scaleOut(targetScale = 0.92f, animationSpec = tween(220)) + fadeOut(tween(180)))

                            navForward ->
                                (slideInHorizontally(animationSpec = tween(260)) { it } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(260)) { -it / 4 } + fadeOut(tween(200)))

                            else ->
                                (slideInHorizontally(animationSpec = tween(260)) { -it / 4 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(tween(200)))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "route",
                ) { current ->
                    when (current) {
                        is AppRoute.Splash -> SplashScreen(
                            onReady = { update ->
                                pendingUpdate = update
                                resetToHome()
                            },
                        )

                        is AppRoute.Home -> HomeScreen(
                            onOpenAnime = { animeId -> pushRoute(AppRoute.Play(animeId = animeId)) },
                            onContinue = { history ->
                                // 继续观看：把集数和进度带过去，播放页直接跳到那里（不看本地/线上）
                                pushRoute(
                                    AppRoute.Play(
                                        animeId = history.animeId,
                                        startEpisodeId = history.episodeId,
                                        startPositionMs = history.progressSeconds * 1000,
                                    ),
                                )
                            },
                            onOpenSearch = { pushRoute(AppRoute.Search) },
                            // 底部 Tab 的选中态交给 Activity 保管（见 homeTab 的说明）
                            selectedTab = homeTab,
                            onTabChange = { homeTab = it },
                            onOpenHistory = { pushRoute(AppRoute.History) },
                            onOpenSettings = { pushRoute(AppRoute.Settings) },
                        )

                        is AppRoute.Search -> SearchScreen(
                            onOpenAnime = { animeId -> pushRoute(AppRoute.Play(animeId = animeId)) },
                            onBack = { goBack() },
                            initialKeyword = searchKeyword,
                            onKeywordChange = { searchKeyword = it },
                        )

                        is AppRoute.History -> HistoryScreen(
                            // 点某条历史：带着那一集的 id 和当时的进度进播放页（跟 web 的继续观看一致）
                            onOpenEpisode = { animeId, episodeId, progressSeconds ->
                                pushRoute(
                                    AppRoute.Play(
                                        animeId = animeId,
                                        startEpisodeId = episodeId,
                                        startPositionMs = progressSeconds * 1000,
                                    ),
                                )
                            },
                            onBack = { goBack() },
                        )

                        is AppRoute.Settings -> SettingsScreen(onBack = { goBack() })

                        is AppRoute.Play -> PlayRoute(
                            animeId = current.animeId,
                            fullscreen = fullscreen,
                            onToggleFullscreen = { applyFullscreen(!fullscreen) },
                            onBack = {
                                if (fullscreen) {
                                    applyFullscreen(false)
                                } else {
                                    goBack()
                                }
                            },
                            onHome = { resetToHome() },
                            // 播放页底部的推荐：**压一层**新播放页，返回时回到上一个番剧（层级返回）
                            onOpenAnime = { animeId -> pushRoute(AppRoute.Play(animeId = animeId)) },
                            startEpisodeId = current.startEpisodeId,
                            startPositionMs = current.startPositionMs,
                        )
                    }
                }

                // 启动检查到的新版本：进入首页后才弹窗（用户在启动页时不该看见弹窗）
                val update = pendingUpdate

                if (update != null && route is AppRoute.Home) {
                    UpdateDialog(
                        info = update,
                        onDismiss = { pendingUpdate = null },
                    )
                }
            }
        }
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
     * 进入下一层页面（压栈）。
     *
     * @description 栈顶就是当前页面；转场方向标记为「前进」，动画从右侧滑入。
     *
     * @param next 目标页面
     */
    private fun pushRoute(next: AppRoute) {
        navForward = true
        backStack.add(next)
    }

    /**
     * 返回上一层（弹栈）。
     *
     * @description 用了自研返回栈后，二级页面一律逐层回退：
     * 播放页里的推荐点进去就再压了一层播放页，返回回到上一个番剧，而不是直接回首页。
     *
     * @returns 真的回退了一层返回 true；已经在根节点（首页）返回 false
     */
    private fun goBack(): Boolean {
        if (backStack.size <= 1) {
            return false
        }

        navForward = false
        backStack.removeAt(backStack.lastIndex)

        return true
    }

    /**
     * 直接回到首页根节点（清空返回栈）。
     *
     * @description 启动页初始化完成、播放页的「主页」按钮、以及任何「不管在哪都回首页」的场景用它；
     * 顺手退出全屏，系统栏图标会在路由变化后按首页重算。
     */
    private fun resetToHome() {
        if (fullscreen) {
            applyFullscreen(false)
        }

        navForward = true
        backStack.clear()
        backStack.add(AppRoute.Home)
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
        controller.isAppearanceLightStatusBars = if (route is AppRoute.Play) false else !darkMode
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
