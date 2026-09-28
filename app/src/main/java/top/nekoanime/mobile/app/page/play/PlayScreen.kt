package top.nekoanime.mobile.app.page.play

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.data.local.LocalPlayProgress
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.model.entity.AnimeBrief
import top.nekoanime.mobile.app.model.entity.AnimeDetail
import top.nekoanime.mobile.app.page.play.component.AnimeInfoCard
import top.nekoanime.mobile.app.page.play.component.EpisodeSection
import top.nekoanime.mobile.app.page.play.component.RecommendSection
import top.nekoanime.mobile.app.page.play.component.player.NekoPlayer
import top.nekoanime.mobile.app.component.rememberSaveableList
import top.nekoanime.mobile.app.component.rememberSaveableState
import top.nekoanime.mobile.app.service.resolvePlaybackStart
import top.nekoanime.mobile.app.data.api.UserIdentityApi
import top.nekoanime.mobile.app.state.SessionState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 播放页底部推荐几个 */
private const val RecommendCount = 10

/**
 * 播放器区域的底色：固定纯黑。
 *
 * @description 连状态栏那一条一起压黑（播放器自己也是黑底），所以切暗亮模式时顶部纹丝不动、
 * 状态栏图标保持浅色即可；播放器**下面**的页面内容仍然跟着主题规范走。
 */
private val PlayerBackdropColor = Color.Black

/**
 * 移动端播放页。
 *
 * @description 页面结构（自上而下）：播放器（16:9 / 全屏）→ 选集（固定）→ 可滚动区（番剧卡片 + 推荐）。
 *
 * 数据来源：番剧详情由 {@link PlayRoute} 从后端拉好后传进来；**每集的播放地址单独问后端**
 * （`/api/anime/episode/play/{episodeId}`），切集时重新取；推荐用后端的按题材推荐接口。
 *
 * 续播规则（优先级从高到低）：
 * 1. 从历史记录 / 继续观看进来：直接用外面给的集数与进度；
 * 2. 本地：本机看过这个番就续上本机记录的集数与位置（不管有没有登录）；
 * 3. 线上：登录后问后端（接口位置在 service 层留好了）；
 * 4. 都没有：第一集从头开始。
 *
 * 配色：**播放器部分固定白字 + 黑色遮罩**（不受应用深浅色与系统暗亮色影响）；
 * 页面内容（选集、卡片、推荐）跟随应用主题（白 / 淡灰 / 粉）。
 *
 * @param anime 番剧详情（含剧集列表）
 * @param fullscreen 是否全屏（由 MainActivity 控制方向与系统栏）
 * @param onToggleFullscreen 切换全屏
 * @param onBack 返回（全屏时先退出全屏）
 * @param onHome 一键回主页
 * @param onOpenAnime 点推荐卡片（参数是番剧 id）
 * @param startEpisodeId 从历史记录/继续观看进来时指定的剧集 id；为 null 表示"直接点卡片进来的"
 * @param startPositionMs 与 startEpisodeId 配套的播放位置（毫秒）
 * @param loggedIn 是否已登录（决定追番按钮能不能点）
 * @param subscribed 是否已追番（由 PlayRoute 从后端取；未登录时固定 false）
 * @param onToggleSubscribe 点追番 / 取消追番（成功后状态由上层更新）
 */
@Composable
fun PlayScreen(
    anime: AnimeDetail,
    player: androidx.media3.exoplayer.ExoPlayer,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    startEpisodeId: Int? = null,
    startPositionMs: Long = 0L,
    loggedIn: Boolean = false,
    subscribed: Boolean = false,
    onToggleSubscribe: () -> Unit = {},
) {
    val context = LocalContext.current
    val progressStore = remember { LocalPlayProgress(context) }

    // 导航方式：手势导航（小白条）做沉浸式，三键导航让开导航栏
    val gestureNavigation = remember(context) { isGestureNavigation(context) }

    // 进页面时先算好"从哪一集、哪一秒开始"
    val start = remember(anime.id, startEpisodeId, startPositionMs) {
        resolvePlaybackStart(anime, startEpisodeId, startPositionMs, progressStore)
    }

    // 这几个状态都保存下来：从别的播放页返回时不会「先空一下再重新请求」——
    // 上一次播的那一集、播放地址、推荐列表都还在，直接接着用（播放器实例也还在，见 PlaybackViewModel）
    var currentEpisodeId by rememberSaveable(anime.id) { mutableIntStateOf(start.episodeId) }

    /**
     * 上报进度的作用域与节流状态。
     *
     * @description 定时 30 秒一次；切集、离开播放页这类关键时机立刻补一次。
     * 「距离结尾 120 秒内」由服务端判定为已看完，所以只要结尾附近报过一次就能标记完成。
     */
    val scope = rememberCoroutineScope()
    var lastReportedAtMs by remember { mutableStateOf(0L) }
    var lastPositionSeconds by remember { mutableStateOf(0) }
    var lastReportedSeconds by remember { mutableStateOf(0) }

    /**
     * 上报当前剧集的观看进度（仅登录用户）。
     *
     * @param force true 表示忽略节流（切集 / 离开页面这类关键时机用）
     */
    fun reportProgress(force: Boolean = false) {
        if (!SessionState.loggedIn) {
            return
        }

        val seconds = lastPositionSeconds

        if (seconds <= 0) {
            return
        }

        val now = System.currentTimeMillis()

        // 定时上报的节流：不到 30 秒、或者位置只挪了不到 5 秒，都不发
        if (!force &&
            (now - lastReportedAtMs < 30_000 || abs(seconds - lastReportedSeconds) < 5)
        ) {
            return
        }

        lastReportedAtMs = now
        lastReportedSeconds = seconds

        val episodeId = currentEpisodeId

        scope.launch {
            UserIdentityApi.reportProgress(episodeId, seconds)
        }
    }

    // 离开播放页时补一次（用户可能直接返回，不等下一次定时）
    DisposableEffect(anime.id) {
        onDispose { reportProgress(force = true) }
    }
    var resumePositionMs by rememberSaveable(anime.id) { mutableLongStateOf(start.positionMs) }

    val currentEpisode = anime.episodes.firstOrNull { it.id == currentEpisodeId }
        ?: anime.episodes.firstOrNull()

    // 播放地址：切集重新问后端。注意拿到新地址前保留旧地址，避免播放器被销毁重建。
    val videoState = rememberSaveableState<String>("play_url_${anime.id}")
    // 这个地址是哪一集的：只有集数变了才重新问后端，返回本页时不用再要一次
    var videoUrlEpisodeId by rememberSaveable(anime.id) { mutableIntStateOf(-1) }
    var videoLoading by rememberSaveable(anime.id) { mutableStateOf(false) }
    // 只记"拿地址失败"这个布尔值：后端返回的原始报错只写 logcat，不给用户看
    var videoFailed by rememberSaveable(anime.id) { mutableStateOf(false) }
    val videoUrl = videoState.value

    LaunchedEffect(anime.id, currentEpisodeId) {
        val episode = currentEpisode ?: return@LaunchedEffect

        // 同一集的地址已经有了（返回本页 / 重组）：直接用，不重新请求
        if (videoUrlEpisodeId == episode.id && !videoUrl.isNullOrBlank()) {
            videoLoading = false
            return@LaunchedEffect
        }

        videoLoading = true
        videoFailed = false

        AnimeRepository.episodePlay(episode.id, episode)
            .onSuccess {
                videoState.value = it.videoUrl
                videoUrlEpisodeId = episode.id
            }
            .onFailure { failure ->
                // 原始报错（接口地址、后端说明）只进日志，界面上只提示"暂时播不了"
                android.util.Log.e("PlayScreen", "拿播放地址失败: episode=${episode.id}", failure)
                videoFailed = true
            }

        videoLoading = false
    }

    // 推荐：后端按这个番剧的题材随机挑 10 个、自动排除自己
    val recommendations = rememberSaveableList<AnimeBrief>("play_recommend_${anime.id}")

    LaunchedEffect(anime.id) {
        // 已经有了（返回本页）：不重新推荐
        if (recommendations.isNotEmpty()) {
            return@LaunchedEffect
        }

        AnimeRepository.recommendForAnime(anime.id, RecommendCount)
            .onSuccess { list ->
                recommendations.clear()
                recommendations.addAll(list)
            }
    }

    /** 切集：跳到这一集自己的本地进度，并把"这个番看到第几集"一起更新 */
    fun switchEpisode(episodeId: Int) {
        // 切集前先把这一集看到哪儿补报一次
        reportProgress(force = true)

        currentEpisodeId = episodeId
        resumePositionMs = progressStore.readPositionMs(episodeId)
        progressStore.save(anime.id, episodeId, resumePositionMs)
    }

    // 页面底色跟着主题走：亮色是白/淡灰、暗色是深灰（播放器那一段是固定黑，见下）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部固定纯黑：状态栏那一条 + 播放器都压在同一块黑底上。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayerBackdropColor),
        ) {
            val url = videoUrl

            if (url != null && currentEpisode != null) {
                // 播放器：全屏与非全屏共用同一个调用点（切全屏不会重建播放器）
                NekoPlayer(
                    player = player,
                    episode = currentEpisode,
                    url = url,
                    animeTitle = anime.title,
                    episodes = anime.episodes,
                    initialPositionMs = resumePositionMs,
                    onSelectEpisode = { episodeId -> switchEpisode(episodeId) },
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = onBack,
                    onHome = onHome,
                    onProgress = { episodeId, positionMs, durationMs ->
                        // 播放中周期性写本地：只要真在播（有位置、有时长）就记
                        if (positionMs > 0 && durationMs > 0) {
                            progressStore.save(anime.id, episodeId, positionMs)

                            // 登录用户同时上报服务端（节流在 reportProgress 里）
                            lastPositionSeconds = (positionMs / 1000).toInt()
                            reportProgress()
                        }
                    },
                    modifier = playerModifier(fullscreen),
                )
            } else {
                // 还没拿到播放地址（或者在切集途中失败了）：占位，保持和播放器一样的尺寸，页面不跳
                PlayerPlaceholder(
                    loading = videoLoading,
                    failed = videoFailed,
                    modifier = playerModifier(fullscreen),
                )
            }
        }

        // 全屏时下面的内容不参与布局
        if (fullscreen) {
            return@Column
        }

        // 固定区：选集（不跟着滚，切集时一直在眼前）
        EpisodeSection(
            episodes = anime.episodes,
            currentEpisodeId = currentEpisodeId,
            onSelect = { episodeId -> switchEpisode(episodeId) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )

        // 固定区与滚动区之间的间距：放在滚动容器外面，滚起来不会被卷走
        Spacer(modifier = Modifier.height(12.dp))

        // 可滚动区：番剧介绍卡片 → 推荐
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                // 三键导航：留白加在外面（内容不进按键区）；手势导航：留在滚动内容里（沉浸式）
                .then(if (gestureNavigation) Modifier else Modifier.navigationBarsPadding())
                .verticalScroll(rememberScrollState())
                .then(if (gestureNavigation) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimeInfoCard(
                anime = anime,
                subscribed = subscribed,
                loggedIn = loggedIn,
                onToggleSubscribe = onToggleSubscribe,
            )

            RecommendSection(items = recommendations, onSelect = onOpenAnime)
        }
    }
}

/**
 * 播放器（或占位）的尺寸：全屏铺满，非全屏 16:9 贴在状态栏下面。
 *
 * @param fullscreen 是否全屏
 * @returns 尺寸约束
 */
private fun playerModifier(fullscreen: Boolean): Modifier = if (fullscreen) {
    Modifier.fillMaxSize()
} else {
    Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .aspectRatio(16f / 9f)
}

/**
 * 播放地址还没拿到时的占位。
 *
 * @param loading 是否正在加载
 * @param failed 是不是失败了（具体报错只写日志，见拿播放地址那段）
 * @param modifier 尺寸约束（跟播放器一致）
 */
@Composable
private fun PlayerPlaceholder(
    loading: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            failed -> Text(
                text = "这一集暂时播不了，请稍后重试",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            loading -> CircularProgressIndicator(color = Color(0xFFE91E63))
            else -> Unit
        }
    }
}

/**
 * 当前是不是手势导航（小白条）。
 *
 * @description 系统里这个开关存在 Settings.Secure.navigation_mode：0=三键、1=两键、2=手势。
 * 手势导航可以做沉浸式（内容铺到屏幕底边，只让一条小白条浮在上面）；
 * 三键导航底部是实体按键，必须让开，否则按键会压在内容上。
 *
 * @param context 上下文
 * @returns true = 手势导航
 */
private fun isGestureNavigation(context: Context): Boolean = try {
    Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) >= 2
} catch (_: Settings.SettingNotFoundException) {
    // 读不到就按"非沉浸式"处理：让开导航栏最安全
    false
}
