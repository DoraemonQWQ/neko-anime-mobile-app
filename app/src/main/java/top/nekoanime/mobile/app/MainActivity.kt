package top.nekoanime.mobile.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
 * @description 负责三件事：
 * 1. 启动前先读配置（接口地址、首页板块等都在 `assets/config/config.<模式>.json`）；
 * 2. 页面路由：首页 ⇄ 播放页（移动端没有详情页，点卡片直接进播放）；
 * 3. 全屏：横屏 + 隐藏系统栏；系统栏图标的明暗按当前页面背景决定。
 */
class MainActivity : ComponentActivity() {
    /** 是否全屏（播放页用） */
    private var fullscreen by mutableStateOf(false)

    /** 当前页面（从启动页开始） */
    private var route by mutableStateOf<AppRoute>(AppRoute.Splash)

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
                // 换页面时重算系统栏图标明暗（播放页顶部是黑底，首页是主题背景）
                LaunchedEffect(route) {
                    applySystemBarAppearance()
                }

                // 页面转场动画：进二级页从右侧滑入，返回时反向滑出
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val forward = targetState !is AppRoute.Home

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

                            forward ->
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
                                route = AppRoute.Home
                            },
                        )

                        is AppRoute.Home -> HomeScreen(
                            onOpenAnime = { animeId -> route = AppRoute.Play(animeId = animeId) },
                            onContinue = { history ->
                                // 继续观看：把集数和进度带过去，播放页直接跳到那里（不看本地/线上）
                                route = AppRoute.Play(
                                    animeId = history.animeId,
                                    startEpisodeId = history.episodeId,
                                    startPositionMs = history.progressSeconds * 1000,
                                )
                            },
                            onOpenSearch = { route = AppRoute.Search },
                            // 底部 Tab 的选中态交给 Activity 保管（见 homeTab 的说明）
                            selectedTab = homeTab,
                            onTabChange = { homeTab = it },
                            onOpenHistory = { route = AppRoute.History },
                            onOpenSettings = { route = AppRoute.Settings },
                        )

                        is AppRoute.Search -> SearchScreen(
                            onOpenAnime = { animeId -> route = AppRoute.Play(animeId = animeId) },
                            onBack = { backToHome() },
                        )

                        is AppRoute.History -> HistoryScreen(
                            // 点某条历史：带着那一集的 id 和当时的进度进播放页（跟 web 的继续观看一致）
                            onOpenEpisode = { animeId, episodeId, progressSeconds ->
                                route = AppRoute.Play(
                                    animeId = animeId,
                                    startEpisodeId = episodeId,
                                    startPositionMs = progressSeconds * 1000,
                                )
                            },
                            onBack = { backToHome() },
                        )

                        is AppRoute.Settings -> SettingsScreen(onBack = { backToHome() })

                        is AppRoute.Play -> PlayRoute(
                            animeId = current.animeId,
                            fullscreen = fullscreen,
                            onToggleFullscreen = { applyFullscreen(!fullscreen) },
                            onBack = {
                                if (fullscreen) {
                                    applyFullscreen(false)
                                } else {
                                    backToHome()
                                }
                            },
                            onHome = { backToHome() },
                            // 播放页底部的推荐：直接换成那个番剧的播放页
                            onOpenAnime = { animeId -> route = AppRoute.Play(animeId = animeId) },
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
     * 回首页：顺手退出全屏，并把系统栏图标切回"按主题"。
     */
    private fun backToHome() {
        if (fullscreen) {
            applyFullscreen(false)
        }

        route = AppRoute.Home
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
