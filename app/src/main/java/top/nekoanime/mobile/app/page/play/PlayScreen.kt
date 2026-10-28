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
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.logging.AppLog as Log
import top.nekoanime.mobile.app.data.local.LocalPlayProgress
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.model.entity.AnimeBrief
import top.nekoanime.mobile.app.model.entity.AnimeDetail
import top.nekoanime.mobile.app.model.entity.EpisodePlay
import top.nekoanime.mobile.app.page.play.component.AnimeInfoCard
import top.nekoanime.mobile.app.page.play.component.EpisodeSection
import top.nekoanime.mobile.app.page.play.component.RecommendSection
import top.nekoanime.mobile.app.page.play.component.player.NekoPlayer
import top.nekoanime.mobile.app.component.rememberSaveableList
import top.nekoanime.mobile.app.component.rememberSaveableState
import top.nekoanime.mobile.app.service.resolvePlaybackStart
import top.nekoanime.mobile.app.state.ContinueWatchingState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.nekoanime.mobile.app.state.SessionState
import androidx.compose.runtime.DisposableEffect
import kotlin.math.abs
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

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
 * 视频地址、所属剧集与外挂字幕元数据作为整体保存；字幕文件由播放会话独立异步加载。
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
 * @param player 返回栈会话持有的播放器
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
// 窗口 UID 是 Media3 的不稳定 API，仅用于识别实际媒体源，避免旧源结束跨会话触发。
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
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

    // 上一次播的那一集、播放响应、推荐列表一起保存，返回时不重建媒体源。
    var currentEpisodeId by rememberSaveable(anime.id) { mutableIntStateOf(start.episodeId) }

    /**
     * 上报进度的作用域与节流状态。
     *
     * @description 定时 30 秒一次；切集、离开播放页这类关键时机立刻补一次。
     * 「距离结尾 120 秒内」由服务端判定为已看完，所以只要结尾附近报过一次就能标记完成。
     */
    var lastReportedAtMs by remember { mutableStateOf(0L) }
    var lastPositionSeconds by remember { mutableStateOf(0) }
    var lastReportedSeconds by remember { mutableStateOf(0) }
    var progressEpisodeId by remember { mutableIntStateOf(start.episodeId) }

    /**
     * 上报真正加载的剧集进度（仅登录用户）。
     *
     * @param force true 表示忽略节流（切集 / 离开页面这类关键时机用）
     */
    fun reportProgress(force: Boolean = false) {
        if (!SessionState.loggedIn) {
            return
        }

        // 强制补报时直接采样播放器，覆盖拖动后立刻返回或切后台、尚未到五秒回调的窗口。
        // 切集请求尚未完成时仍按已加载媒体的剧集编号保存，避免把旧视频位置记到新一集。
        if (force && player.currentMediaItem?.mediaId?.toIntOrNull() == progressEpisodeId) {
            lastPositionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1000).toInt()
            progressStore.save(anime.id, progressEpisodeId, player.currentPosition.coerceAtLeast(0L))
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
        val episodeId = progressEpisodeId

        // 页面销毁不会取消最终上报；保存成功后状态层再刷新首页，保证展示服务端最新进度。
        ContinueWatchingState.reportProgress(episodeId, seconds)
    }

    // 离开播放页时补一次（用户可能直接返回，不等下一次定时）
    DisposableEffect(anime.id) {
        onDispose { reportProgress(force = true) }
    }
    // 进入后台或导航离开播放页时补报，避免不足 30 秒的播放进度在恢复首页时仍停留在旧值。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        reportProgress(force = true)
    }
    var resumePositionMs by rememberSaveable(anime.id) { mutableLongStateOf(start.positionMs) }

    val currentEpisode = anime.episodes.firstOrNull { it.id == currentEpisodeId }
        ?: anime.episodes.firstOrNull()

    // 原子保存所属剧集、视频地址及字幕，切集请求期间继续保留旧播放会话。
    val videoState = rememberSaveableState<EpisodePlay>("play_source_${anime.id}")
    var videoLoading by rememberSaveable(anime.id) { mutableStateOf(false) }
    // 只记"拿地址失败"这个布尔值：后端返回的原始报错只写 logcat，不给用户看
    var videoFailed by rememberSaveable(anime.id) { mutableStateOf(false) }
    val episodePlay = videoState.value

    LaunchedEffect(anime.id, currentEpisodeId) {
        val episode = currentEpisode ?: return@LaunchedEffect

        // 同一集的地址已经有了（返回本页 / 重组）：直接用，不重新请求
        if (episodePlay?.episode?.id == episode.id && episodePlay.videoUrl.isNotBlank()) {
            videoLoading = false
            return@LaunchedEffect
        }

        videoLoading = true
        videoFailed = false

        val result = AnimeRepository.episodePlay(episode.id, episode)
        // API 层可能包装取消异常；明确阻止已取消的旧集请求更新页面。
        coroutineContext.ensureActive()
        result
            .onSuccess { videoState.value = it }
            .onFailure { failure ->
                Log.e("PlayScreen", "拿播放地址失败: episode=${episode.id}", failure)
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

    val autoAdvance = remember(player, anime.id) { EpisodeAutoAdvance() }

    /**
     * 统一手动与自动切集：先补报实际媒体，再同步更新最新选集意图。
     * 自动连播从零开始，并覆盖目标集旧本地进度；手动选集仍保留原来的逐集续播规则。
     */
    fun switchEpisode(episodeId: Int, fromBeginning: Boolean = false) {
        if (episodeId == currentEpisodeId || anime.episodes.none { it.id == episodeId }) return
        if (!fromBeginning) autoAdvance.onManualSelection()
        reportProgress(force = true)
        currentEpisodeId = episodeId
        resumePositionMs = if (fromBeginning) 0L else progressStore.readPositionMs(episodeId)
        progressStore.save(anime.id, episodeId, resumePositionMs)
    }

    // 自动连播属于页面业务：此处同时掌握最新选集意图、播放响应和进度，播放器组件只负责建源。
    // 常驻监听器通过最新 lambda 读取页面状态；其中 currentEpisodeId 是状态委托，手动点击后
    // 无须等待重组就能读到新值，从而让尚在播放的旧源 ENDED 无法覆盖用户选择。
    val currentOnPlaybackEvent by rememberUpdatedState<(Player) -> Unit>({ observedPlayer ->
        val item = observedPlayer.currentMediaItem
        val timeline = observedPlayer.currentTimeline
        val windowIndex = observedPlayer.currentMediaItemIndex
        val source = if (item != null && !timeline.isEmpty &&
            windowIndex >= 0 && windowIndex < timeline.windowCount
        ) {
            // Timeline 窗口 UID 跟随实际媒体源；不能只比较页面标题或当前选中的集数。
            val window = timeline.getWindow(windowIndex, androidx.media3.common.Timeline.Window())
            EpisodeAutoAdvance.Source(
                mediaId = item.mediaId,
                url = item.localConfiguration?.uri?.toString().orEmpty(),
                windowUid = window.uid,
            )
        } else null
        val endPreviouslyConsumed = autoAdvance.hasConsumedEnd
        val nextEpisodeId = autoAdvance.nextEpisodeId(
            source = source,
            isReady = observedPlayer.playbackState == Player.STATE_READY,
            isEnded = observedPlayer.playbackState == Player.STATE_ENDED,
            selectedEpisodeId = currentEpisodeId,
            expectedEpisodeId = videoState.value?.episode?.id,
            expectedUrl = videoState.value?.videoUrl,
            episodes = anime.episodes,
        )
        if (nextEpisodeId != null) {
            Log.i("PlayScreen", "自然结束，自动下一集: episode=${source?.mediaId} next=$nextEpisodeId startMs=0")
            switchEpisode(nextEpisodeId, fromBeginning = true)
        } else if (!endPreviouslyConsumed && autoAdvance.hasConsumedEnd) {
            // 仅记录消费结束事件的边沿，末集停留期间的其他播放器事件不重复打日志。
            Log.i("PlayScreen", "剧集自然结束，无下一集，保持结束: episode=${source?.mediaId}")
        }
    })
    DisposableEffect(player, anime.id) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // 聚合事件回调内读取播放器最新状态，拒绝已排队但属于旧源的 ENDED 参数。
                // 暂停、缓冲、错误和 UI 重组都不等同于自然结束。
                currentOnPlaybackEvent(player)
            }
        }
        player.addListener(listener)
        // 返回保留的 READY 会话时补一次快照；ENDED 本身不能取得首次 READY 许可。
        currentOnPlaybackEvent(player)
        onDispose { player.removeListener(listener) }
    }

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
            val source = episodePlay

            if (source != null) {
                // 全屏与非全屏共用调用点；只在新响应成功后才一起切换剧集与源。
                NekoPlayer(
                    player = player,
                    episode = source.episode,
                    url = source.videoUrl,
                    subtitles = source.subtitles,
                    animeTitle = anime.title,
                    episodes = anime.episodes,
                    initialPositionMs = resumePositionMs,
                    onSelectEpisode = { episodeId -> switchEpisode(episodeId) },
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = onBack,
                    onHome = onHome,
                    onProgress = { episodeId, positionMs, durationMs ->
                        if (positionMs > 0 && durationMs > 0) {
                            progressStore.save(anime.id, episodeId, positionMs)
                            progressEpisodeId = episodeId
                            lastPositionSeconds = (positionMs / 1000).toInt()
                            reportProgress()
                        }
                    },
                    modifier = playerModifier(fullscreen),
                )
            } else {
                PlayerPlaceholder(
                    loading = videoLoading,
                    failed = videoFailed,
                    modifier = playerModifier(fullscreen),
                )
            }
        }

        if (fullscreen) {
            return@Column
        }

        EpisodeSection(
            episodes = anime.episodes,
            currentEpisodeId = currentEpisodeId,
            onSelect = { episodeId -> switchEpisode(episodeId) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
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
    false
}
