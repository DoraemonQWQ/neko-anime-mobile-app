package top.nekoanime.mobile.app.page.play.component.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import top.nekoanime.mobile.app.model.entity.Episode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** 双击 / 快进的步长（秒） */
private const val SkipSeconds = 10

/**
 * 双击时左右两侧「快退 / 快进 10 秒」各占整屏宽度的比例。
 *
 * @description 分区是「两侧各 15%，中间 70% 是播放/暂停」。
 * 中间必须给足宽度：播放/暂停是最高频的操作，区域太窄会变成"怎么点都在快进"。
 */
private const val DoubleTapSideFraction = 0.15f

/**
 * 调试开关：把双击的三个区域画在画面上（半透明底色 + 分界线 + 文字）。
 *
 * @description 调区域比例时打开看效果用。比例定下来后改成 false 就没有任何绘制，也不影响手势
 * （这层没有 pointerInput，本来就不拦触摸）。
 */
private const val ShowDoubleTapZones = false

/** HUD 显示时长（毫秒） */
private const val HudDurationMs = 900L

/** 控制栏自动隐藏时间（毫秒）：显示后有操作就重新计时 */
private const val ControlsAutoHideMillis = 5_000L

/**
 * 全屏时顶部的手势安全区高度。
 *
 * @description 全屏会把系统栏藏起来，靠"从顶部往下滑"唤出状态栏；
 * 这一段要是也当播放器手势处理，用户想唤出状态栏就会顺手把亮度/音量改了。
 * 48dp 约等于状态栏高度，足够覆盖系统那一条边缘手势区。
 */
private val FullscreenTopGestureSafeZone = 48.dp

/** 进度预览时，一次拖动最多能改变多少百分比（避免轻轻一滑就跳到头） */
private const val MaxSeekFractionPerDrag = 0.5f

/**
 * NekoAnime 播放器（Compose + Media3/ExoPlayer）。
 *
 * @description 只做播放器本身：画面、手势、控制栏、HUD、弹幕预留位。
 * 业务（选集、Bangumi 数据、上报进度）都留在外面，方便并入正式项目时按模块拼装。
 *
 * 手势与 B 站移动端对齐：单击显隐控制栏、双击播放/暂停与快进快退、
 * 水平拖动调进度、左半屏竖滑调亮度、右半屏竖滑调音量、长按 2 倍速；锁定后除解锁外全部忽略。
 *
 * 双击分区：中间 50% 是播放/暂停，左右各 25% 是后退/前进 10 秒
 * （播放/暂停是最高频操作，分区给足宽度才不会"怎么点都在快进"）。
 *
 * 切集靠换 MediaItem，播放器实例、播放方向与全屏状态都不重建：
 * 全屏选集抽屉走的是 {@link onSelectEpisode}，由外层统一改当前集。
 *
 * @param episode 当前剧集（视频地址、标题、集数都在里面）
 * @param url 当前剧集的播放地址（由外层从后端拿到后传进来）
 * @param modifier 尺寸约束
 * @param animeTitle 番剧名（顶栏标题的第一段：番剧名 + 剧集名 + 集数）
 * @param episodes 全部剧集（已按集数升序传进来的那份也可以，内部会再排一次）；用来算上下集与抽屉列表
 * @param onSelectEpisode 请求切到某一集（参数是剧集 id）；当前集不变时不会触发
 * @param danmakuLayer 弹幕层（默认空实现，界面上不显示任何弹幕内容）
 * @param fullscreen 是否全屏（由外层控制，切全屏时不重建播放器）
 * @param initialPositionMs 续播位置：换源（首次进入 / 切集）时跳到这里；0 表示从头播
 * @param onToggleFullscreen 请求切换全屏
 * @param onBack 返回
 * @param onHome 返回主页
 * @param onPlayerReady 播放器创建完成（外层可用来做统计、上报）
 * @param onProgress 进度回报（剧集 id、当前位置、总时长）：每 5 秒一次，换源前与离开时各补一次；
 * 外层用它写本地进度。注意第一个参数是**播放器里真正加载的那一集**，不是"用户刚点的那一集"。
 */
// Media3 把 PlayerView / AspectRatioFrameLayout / Player#getVideoFormat 这些标成了 @UnstableApi：
// 用它们必须显式 opt-in，否则 lint 会按 error 报（编辑器里就是那三处红），
// 但纯 kotlinc 编译不受影响——所以只跑 assembleDebug 是看不出来的，得跑 lint 或看 IDE 检查。
// 这里用 androidx 的 @OptIn（不是 kotlin.OptIn，那个要求标记注解是 Kotlin 的 @RequiresOptIn）：
// 它只作用于本函数，不会把这个要求传染给调用方（MainActivity / PlayerScreen 不用跟着标）。
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun NekoPlayer(
    player: ExoPlayer,
    episode: Episode,
    url: String,
    modifier: Modifier = Modifier,
    animeTitle: String = "",
    episodes: List<Episode> = emptyList(),
    onSelectEpisode: (Int) -> Unit = {},
    danmakuLayer: DanmakuLayer = NoDanmakuLayer,
    fullscreen: Boolean = false,
    initialPositionMs: Long = 0L,
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit = {},
    onHome: () -> Unit = {},
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onProgress: (episodeId: Int, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // 顶栏标题：番剧名、剧集名、集数三段用「 | 」拼成一条（空格当分隔太不明显；
    // 太长时由控制栏做跑马灯，每滚一次停 10 秒、进来也先停 10 秒）
    val title = listOf(animeTitle, episode.title, "第 ${episode.number} 集")
        .filter { it.isNotBlank() }
        .joinToString(" | ")

    // 按集数升序：选集抽屉按这个顺序排，跟 web 的选集顺序一致。
    // key 用 episodes.toList() 而不是 episodes 本身：外层用的是可变列表（同一实例内容会变），
    // 直接拿实例当 key 的话列表变了也不会重算。toList() 读的是快照状态，内容一变就会重新排序。
    val sortedEpisodes = remember(episodes.toList()) { episodes.sortedBy { it.number } }

    // 全屏底部栏的上一集/下一集按钮要用：按集数顺序找前后一集，找不到就是 null（按钮隐藏）
    val currentIndex = sortedEpisodes.indexOfFirst { it.id == episode.id }
    val previousEpisode = if (currentIndex > 0) sortedEpisodes[currentIndex - 1] else null
    val nextEpisode = if (currentIndex >= 0 && currentIndex < sortedEpisodes.size - 1) {
        sortedEpisodes[currentIndex + 1]
    } else {
        null
    }

    // 全屏选集抽屉是否展开
    var drawerOpen by remember { mutableStateOf(false) }

    // 播放器里**当前真正加载**的那一集：必须和外层传进来的 episode 分开——
    // 外层一改 state，episode 立刻是新剧集，但播放器里播的还是上一集（换源是后面的 effect 才做的）。
    // 不分开的话，切集时那几百毫秒的进度会被写到新剧集头上（表现就是"新剧集一打开就在上一集的位置"）。
    val loadedEpisodeId = remember { mutableIntStateOf(episode.id) }

    // 进度回调包一层：下面的常驻 effect 只会启动一次，直接捕获参数会一直用第一次组合时的 lambda
    val currentOnProgress by rememberUpdatedState(onProgress)

    // 播放器由外层（播放页的 ViewModel）持有：
    // - 同一页面里切 URL 只换 MediaItem，不重建播放器；
    // - 从别的播放页返回时拿到的是同一个实例，缓冲与进度都还在，不会重新加载。

    // 切到后台之前是不是在播：决定回到前台要不要自动继续
    // （用户自己按了暂停再切后台，回来就不该自己播起来）
    var resumeOnForeground by remember { mutableStateOf(false) }

    // 正在进行中的触摸数（手势/拖进度条）：>0 时自动隐藏要暂停计时。
    // 用计数而不是布尔，是因为一次触摸可能同时被点击识别器和拖动识别器看到。
    var interactingCount by remember { mutableIntStateOf(0) }

    // 操作计数：每开始一次操作都 +1，用来给自动隐藏重新计时
    var interactionTick by remember { mutableIntStateOf(0) }

    // 切到后台：暂停播放（不暂停的话后台会一直跑流量，切回来时进度也容易乱跳）
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        resumeOnForeground = player.isPlaying
        player.pause()
        // 手势可能被系统打断（比如手势中途切后台），把进行中的计数清零，
        // 不然自动隐藏会一直以为"用户还按着"，再也不自动隐藏
        interactingCount = 0
        android.util.Log.i("NekoPlayer", "切到后台：暂停（回前台恢复=$resumeOnForeground）")
    }

    // 回到前台：只有切走之前确实在播才继续
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (resumeOnForeground) {
            resumeOnForeground = false
            player.play()
            android.util.Log.i("NekoPlayer", "回到前台：继续播放")
        }
    }

    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    // 只记"出错了没有"这一个布尔值：具体错误码、错误信息、堆栈一律只写 logcat（见 onPlayerError），
    // 界面上只给用户一句"播放失败"+ 重试按钮，不把播放器的原始报错糊到用户脸上
    var hasError by remember { mutableStateOf(false) }

    /**
     * 首次加载是否已经完成（画面第一帧真正就绪）。
     *
     * @description 首次加载期间：控制栏常驻但**只露顶栏**，并且禁用双击跳转 / 拖动进度 / 长按倍速。
     * 切集不算首次加载（那是同一个播放会话里的换源），所以这个值一旦为 true 就不再回落。
     *
     * 初值直接读播放器状态：从推荐页返回本页时播放器早就是 READY 了，
     * 若固定从 false 起步，返回的那一瞬间会闪一下"加载中"的顶栏-only 状态。
     */
    var firstLoadDone by remember(player) {
        mutableStateOf(player.playbackState == Player.STATE_READY)
    }

    /** 首次加载进行中（出错时不算：出错走"播放失败"那套常驻控制栏） */
    val firstLoading = !firstLoadDone && !hasError

    /** 控制栏是否被强制常驻：首次加载中、或播放失败时，用户不能主动收起，也不会自动隐藏 */
    val controlsPinned = firstLoading || hasError

    var buffering by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var hud by remember { mutableStateOf<PlayerHudState?>(null) }

    // 拖进度条期间中央指示框要一直挂着（固定位置不变，不该等 900ms 自己消失）
    var hudPinned by remember { mutableStateOf(false) }

    // 手势过程中的临时状态
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }

    // seek 刚下发时播放器不会立刻回报新位置（异步生效，通常要一两百毫秒），
    // 这段时间里 player.currentPosition 还是旧值。若直接用旧值刷新 UI，进度条就会先跳回旧位置，
    // 表现为「松手以后又弹回去」。这里记住目标位置，等播放器追上或超时再交回实时位置。
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        // 只用于验证「切全屏不会重建播放器」：正常一次播放会话里这行只应该出现一次
        android.util.Log.i("NekoPlayer", "ExoPlayer 创建")
        onPlayerReady(player)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                danmakuLayer.onPlayStateChanged(isPlaying)
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
                danmakuLayer.onViewportChanged(size.width, size.height)
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING

                // 第一帧就绪：首次加载结束，手势与整条控制栏全部放开
                if (state == Player.STATE_READY) {
                    firstLoadDone = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 报错细节只进日志：现场排查靠这行（错误码 + 底层信息 + 堆栈全在）
                hasError = true

                // 出错时把控制栏亮出来：返回键、主页键都在里面，
                // 不然用户一上来看到"播放失败"却连退出去的按钮都找不到
                controlsVisible = true

                android.util.Log.e("NekoPlayer", "播放出错: ${error.errorCodeName} / ${error.message}", error)
            }
        }

        player.addListener(listener)

        onDispose {
            // 离开播放页：把最后的位置补报一次，别丢最后几秒
            currentOnProgress(
                loadedEpisodeId.intValue,
                player.currentPosition,
                player.duration.coerceAtLeast(0L),
            )

            player.removeListener(listener)

            // 只暂停、不释放：这个播放页可能只是被压在另一个播放页下面（返回时要接着播）。
            // 真正的释放交给 PlaybackViewModel.onCleared（页面被弹出时）。
            player.pause()
            android.util.Log.i("NekoPlayer", "离开播放页：暂停（播放器由 ViewModel 持有，不释放）")
        }
    }

    // URL 变化：换源（同一个播放器实例）
    LaunchedEffect(url) {
        // 返回本页（或被压在下面再回来）：媒体源没变就直接接着播——
        // 不重新 setMediaItem、不 prepare、也不重新 seek，缓冲与进度都保留着
        val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString()

        if (player.mediaItemCount > 0 && currentUrl == url) {
            loadedEpisodeId.intValue = episode.id

            // 上次会话是停在出错状态（idle）的：这时候光 play() 不会重新加载，
            // 必须先 prepare 才会再去请求一次，否则返回本页就是一块永远不动的黑屏
            if (player.playbackState == Player.STATE_IDLE) {
                android.util.Log.i("NekoPlayer", "媒体源未变但播放器停在 idle，重新 prepare")
                player.prepare()
            }

            player.play()
            android.util.Log.i("NekoPlayer", "媒体源未变，直接续播（不重新加载）")

            return@LaunchedEffect
        }

        hasError = false
        android.util.Log.i("NekoPlayer", "换源: $url")

        // 换源前把上一集的进度补报一次（此时 episode 已经是新值，所以要报 loadedEpisodeId）
        if (player.mediaItemCount > 0) {
            currentOnProgress(loadedEpisodeId.intValue, player.currentPosition, durationMs)
        }

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()

        // 续播：外层已经把"这一集该从哪秒开始"算好了（本地进度优先），这里只负责跳过去
        val resumeMs = initialPositionMs.coerceAtLeast(0L)

        if (resumeMs > 0L) {
            player.seekTo(resumeMs)
            positionMs = resumeMs
            pendingSeekMs = resumeMs
            pendingSeekAt = SystemClock.elapsedRealtime()
        }

        player.playWhenReady = true
        loadedEpisodeId.intValue = episode.id
    }

    // 全屏切换只改布局，不碰播放器。打一行日志方便用 logcat 验证「切全屏没有重新加载」
    LaunchedEffect(fullscreen) {
        android.util.Log.i("NekoPlayer", "fullscreen=$fullscreen（播放器不重建）")

        // 退出全屏（比如旋转回竖屏）时把抽屉收掉，免得留一层遮罩在竖屏界面上
        if (!fullscreen) {
            drawerOpen = false
        }
    }

    // 抽屉展开时，系统返回键先收抽屉，而不是直接退出播放页
    BackHandler(enabled = drawerOpen) {
        drawerOpen = false
    }

    /** 一次操作开始：暂停自动隐藏 + 重新计时 */
    fun beginInteraction() {
        interactingCount++
        interactionTick++
    }

    /** 一次操作结束：自动隐藏从这里开始重新计时 */
    fun endInteraction() {
        interactingCount = (interactingCount - 1).coerceAtLeast(0)
    }

    /**
     * 重试播放。
     *
     * @description 出错后 ExoPlayer 会停在 idle 状态，不会自己再往下走，必须重新 prepare 才会再次发起请求。
     * 重试前先记下当前进度，重新加载后跳回去，用户不会因为一次网络抖动就从这一集的开头重看。
     *
     * 失败原因（错误码、底层 message、堆栈）只写 logcat，不往界面上传递。
     */
    fun retryPlayback() {
        val resumeMs = player.currentPosition.coerceAtLeast(0L)

        android.util.Log.i("NekoPlayer", "重试播放: $url @ ${resumeMs}ms")

        hasError = false
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()

        if (resumeMs > 0L) {
            player.seekTo(resumeMs)
            positionMs = resumeMs
        }

        player.playWhenReady = true
    }

    // 控制栏显示后 5 秒没操作就自动隐藏：
    // - 有任何操作（interactionTick 变）都会把 5 秒重新开始；
    // - 操作进行中（interactingCount > 0，比如按住不放、一直拖进度条）**暂停计时**，
    //   不会出现"手指还在屏幕上，控制栏自己消失了"；
    // - 抽屉展开期间同样不隐藏。
    // - 首次加载 / 播放失败时会话被钉住（controlsPinned），根本不该起这个计时。
    LaunchedEffect(controlsVisible, interactionTick, interactingCount, drawerOpen, controlsPinned) {
        if (controlsVisible && !drawerOpen && interactingCount == 0 && !controlsPinned) {
            delay(ControlsAutoHideMillis)
            controlsVisible = false
            android.util.Log.i("NekoPlayer", "控制栏 5 秒无操作，自动隐藏")
        }
    }

    // 控制栏显隐日志：真机跑自动化脚本时靠它判断现在点画面到底会「显示」还是「隐藏」控制栏
    LaunchedEffect(controlsVisible, locked) {
        android.util.Log.i("NekoPlayer", "控制栏 visible=$controlsVisible locked=$locked")
    }

    // 每 250ms 刷一次进度：进度条与 HUD 的平滑度靠它
    LaunchedEffect(player) {
        // 日志计数：按时间打（20 × 250ms = 5 秒一条），
        // 不用"位置是 5 秒整数倍"来判断——暂停时那样会一条都不打，排查问题时看不到状态
        var logTicks = 0

        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition

            when {
                // 水平拖动进度中：显示手指位置（预览），松手才真正 seek
                seeking -> Unit

                // seek 已下发、播放器还没追上：显示目标位置，不回退
                pendingSeekMs != null -> {
                    val target = pendingSeekMs ?: 0L
                    val live = player.currentPosition

                    // 播放器已追上目标位置，或者等了 2 秒还没追上（异常情况兜底，避免进度条卡死）
                    if (abs(live - target) <= 800L || SystemClock.elapsedRealtime() - pendingSeekAt > 2000L) {
                        android.util.Log.i(
                            "NekoPlayer",
                            "seek 生效 target=${target / 1000}s live=${live / 1000}s",
                        )
                        pendingSeekMs = null
                        positionMs = live
                    } else {
                        positionMs = target
                    }

                    danmakuLayer.onPositionChanged(positionMs)
                }

                else -> {
                    positionMs = player.currentPosition
                    danmakuLayer.onPositionChanged(positionMs)
                }
            }

            // 每 5 秒打一条状态，方便用 adb logcat 验证「在播、播到哪、掉了几帧」
            if (logTicks++ % 20 == 0) {
                android.util.Log.i(
                    "NekoPlayer",
                    "pos=${positionMs / 1000}s dur=${durationMs / 1000}s buffered=${bufferedMs / 1000}s " +
                        "playing=$playing dropped=${player.videoFormat?.width}x${player.videoFormat?.height}",
                )

                // 顺手把进度交给外层写本地（将来登录后的线上进度也走这个回调）
                currentOnProgress(loadedEpisodeId.intValue, positionMs, durationMs)
            }

            delay(250)
        }
    }

    // HUD 自动消失（拖进度条期间由 hudPinned 顶着，不消失）
    LaunchedEffect(hud, hudPinned) {
        if (hud != null && hud !is PlayerHudState.Speed && !hudPinned) {
            delay(HudDurationMs)
            hud = null
        }
    }

    /** 统一改音量：0f~1f */
    fun setVolumeFraction(value: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (value.coerceIn(0f, 1f) * max).roundToInt()

        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        hud = PlayerHudState.Vertical(PlayerHudState.Vertical.Kind.Volume, target.toFloat() / max)
    }

    /** 统一改亮度：0f~1f（0.01 兜底，太暗会看起来像黑屏） */
    fun setBrightnessFraction(value: Float) {
        val window = activity?.window ?: return
        val target = value.coerceIn(0.01f, 1f)

        window.attributes = window.attributes.apply { screenBrightness = target }
        hud = PlayerHudState.Vertical(PlayerHudState.Vertical.Kind.Brightness, target)
    }

    /** 读当前音量比例（系统音量为准） */
    fun currentVolumeFraction(): Float {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** 读当前亮度比例 */
    fun currentBrightnessFraction(): Float {
        val window = activity?.window ?: return 1f
        val value = window.attributes.screenBrightness

        // 窗口亮度为 -1 表示"跟随系统"，这时按系统亮度起算，
        // 否则第一次滑动会把亮度硬拉到 50%，屏幕会突然变暗一下
        if (value >= 0f) {
            return value
        }

        val system = android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            128,
        )

        return (system / 255f).coerceIn(0.01f, 1f)
    }

    /**
     * 统一下发 seek。
     *
     * @description 进度条拖动、手势水平拖动、双击快进全部走这里，
     * 这样才能共用「seek 生效前不被实时位置覆盖」的保护逻辑。
     *
     * @param target 目标位置（毫秒）
     */
    fun seekTo(target: Long) {
        val safe = target.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)

        player.seekTo(safe)
        positionMs = safe
        pendingSeekMs = safe
        pendingSeekAt = SystemClock.elapsedRealtime()
    }

    Box(
        modifier = modifier.background(Color.Black),
    ) {
        // 画面：用官方 PlayerView（SurfaceView 渲染，硬解），但关掉它自带控制器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // 手势层：**必须叠在视频之上**。
        // PlayerView 是原生 View，自己会消费触摸事件，手势挂在它下面（外层 Box）根本收不到——
        // 真机上表现为「滑动没反应」。放到它上面，事件先在 Compose 里过一遍。
        // 控制栏与 HUD 又叠在手势层之上，所以按钮、进度条照常可点：
        // Compose 只把手势分发给命中路径上的节点，没有 pointerInput 的容器不会挡。
        Box(
            modifier = Modifier
                .matchParentSize()
                .playerGestures(
                    // 抽屉展开时关掉播放器手势：抽屉是模态浮层，
                    // 这时在阴影/抽屉上滑动不该再改播放进度或亮度音量
                    enabled = !drawerOpen,
                    locked = locked,
                    // 首次加载没完成、或者播放失败时：双击跳转 / 双击播放暂停 / 拖动进度 / 长按倍速全关。
                    // 单击仍然保留——控制栏要通过它显隐（不过这两种状态下控制栏本来就被钉住不隐藏）。
                    // 竖直拖动（亮度 / 音量）不受影响，加载期间照样能调。
                    advancedGesturesEnabled = firstLoadDone && !hasError,
                    // 锁定后单击依然可以显隐控制栏（解锁键跟着一起动），其它手势在 PlayerGestures 里被忽略
                    // 常驻状态（首次加载 / 播放失败）下点击画面不做任何事，避免用户把返回键点没了
                    onTap = { if (!controlsPinned) controlsVisible = !controlsVisible },
                    onDoubleTap = { fraction ->
                        if (!locked) {
                            when {
                                // 左 25%：后退 10 秒
                                fraction < DoubleTapSideFraction -> {
                                    seekTo(player.currentPosition - SkipSeconds * 1000)
                                    hud = PlayerHudState.Skip(-SkipSeconds)
                                }

                                // 右 25%：前进 10 秒
                                fraction > 1f - DoubleTapSideFraction -> {
                                    seekTo(player.currentPosition + SkipSeconds * 1000)
                                    hud = PlayerHudState.Skip(SkipSeconds)
                                }

                                // 中间 50%：播放 / 暂停
                                else -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    },
                    onSeekDrag = { deltaFraction ->
                        if (durationMs > 0) {
                            if (!seeking) {
                                seeking = true
                                seekStartMs = player.currentPosition
                                // 预览位置从当前位置起算，后续每次拖动都在这上面叠加
                                seekPreviewMs = seekStartMs
                            }

                            val delta = (deltaFraction * durationMs).coerceIn(
                                -durationMs * MaxSeekFractionPerDrag,
                                durationMs * MaxSeekFractionPerDrag,
                            )

                            // 注意：手势回调给的是「本次事件的位移」，不是相对起点的累计位移，
                            // 这里必须累加。之前写成 seekStartMs + delta，等于只用最后一个事件的位移，
                            // 表现就是「拖了半个屏幕，进度只动了一点点」。
                            seekPreviewMs = (seekPreviewMs + delta.toLong()).coerceIn(0L, durationMs)

                            hud = PlayerHudState.Seek(targetMs = seekPreviewMs)
                        }
                    },
                    onSeekEnd = {
                        if (seeking) {
                            android.util.Log.i(
                                "NekoPlayer",
                                "手势 seek: start=${seekStartMs / 1000}s target=${seekPreviewMs / 1000}s",
                            )
                            seekTo(seekPreviewMs)
                            seeking = false
                        }
                    },
                    onVerticalDrag = { startXFraction, deltaFraction ->
                        val step = deltaFraction * 0.9f

                        if (startXFraction < 0.5f) {
                            setBrightnessFraction(currentBrightnessFraction() + step)
                        } else {
                            setVolumeFraction(currentVolumeFraction() + step)
                        }
                    },
                    onVerticalDragEnd = { hud = null },
                    onLongPressStart = {
                        if (!locked) {
                            speed = 2f
                            player.setPlaybackSpeed(2f)
                            hud = PlayerHudState.Speed(2f)
                        }
                    },
                    onLongPressEnd = {
                        if (speed != 1f) {
                            speed = 1f
                            player.setPlaybackSpeed(1f)
                            hud = null
                        }
                    },
                    // 视频区手势：按下暂停计时、抬起继续计时
                    onInteractionStart = { beginInteraction() },
                    onInteractionEnd = { endInteraction() },
                    // 全屏时顶部留一条安全区给"下滑唤出状态栏"
                    topSafeZonePx = if (fullscreen) {
                        with(LocalDensity.current) { FullscreenTopGestureSafeZone.toPx() }
                    } else {
                        0f
                    },
                ),
        )

        // 弹幕预留位：现在传进来的是空实现，界面上什么都看不到
        danmakuLayer.Content(Modifier.fillMaxSize())

        // 双击分区调试线（调比例时打开看效果用，见 ShowDoubleTapZones）
        if (ShowDoubleTapZones) {
            DoubleTapZoneGuide(
                sideFraction = DoubleTapSideFraction,
                modifier = Modifier.matchParentSize(),
            )
        }

        // 缓冲圈
        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFE91E63),
            )
        }

        // 播放失败：只给一句笼统提示 + 一个重试按钮。
        // 原始错误（错误码/底层 message/堆栈）只写 logcat，见 onPlayerError，界面上一个字都不露。
        // 整块是一个居中的 Column，不含任何单边 padding，所以按钮与文字严格落在画面中心。
        if (hasError) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = { retryPlayback() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "重试",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Text(
                    text = "播放失败",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 中央 HUD
        PlayerHud(state = hud, modifier = Modifier.fillMaxSize())

        // 暂停时画面中央的大播放按钮（B 站 也是这样：暂停后中间有个明显的 ▶，点一下继续）。
        // 控制栏里已经没有播放/暂停按钮了，这个就是"恢复播放"最直观的入口。
        // 首次加载没完成时不显示（那时画面还没出来，摆个播放键只会让人以为能播起来了）
        if (!playing && !buffering && !hasError && !locked && firstLoadDone) {
            IconButton(
                onClick = { player.play() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "继续播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // 控制栏：点击画面显隐。整块铺满播放区（顶栏贴上、底栏贴下），
        // 中间是空的、没有任何 pointerInput，所以双击/滑动这些手势照常穿透到下面的手势层。
        AnimatedVisibility(
            // 锁定后也走同一套显隐逻辑：单击画面照旧显示/隐藏，解锁键跟着一起出现或收掉。
            // 首次加载 / 播放失败时 controlsPinned 为真 → 一直显示，用户点画面也收不掉。
            visible = controlsVisible || controlsPinned,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            PlayerControls(
                playing = playing,
                positionMs = if (seeking) seekPreviewMs else positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                speed = speed,
                locked = locked,
                fullscreen = fullscreen,
                title = title,
                // 播放键全屏/非全屏都保留（非全屏另外还有双击画面中间与中央的大播放键）
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { target -> seekTo(target) },
                onToggleSpeed = {
                    // 1.0 → 1.25 → 1.5 → 2.0 → 1.0
                    speed = when (speed) {
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    player.setPlaybackSpeed(speed)
                },
                onToggleLock = { locked = !locked },
                onToggleFullscreen = onToggleFullscreen,
                onBack = onBack,
                onHome = onHome,
                // 按钮是一次性操作：直接重置计时；进度条拖动则用 start/end 包起来
                onUserInteraction = {
                    beginInteraction()
                    endInteraction()
                },
                onInteractionStart = { beginInteraction() },
                onInteractionEnd = { endInteraction() },
                // 拖进度条：中央显示目标时间，松手收掉
                onSeekPreview = { targetMs ->
                    hudPinned = true
                    hud = PlayerHudState.Seek(targetMs)
                },
                onSeekPreviewEnd = {
                    hudPinned = false
                    hud = null
                },
                // 首次加载成功之前一律只露顶栏：进度条、时长、倍速、锁定键这些内容
                // 都建立在"视频已经就绪"之上，没成功之前摆出来全是空信息（失败了也一样）。
                // 顶栏必须留着——返回/主页是用户唯一的出路。
                topBarOnly = !firstLoadDone,
                modifier = Modifier.fillMaxSize(),
                // 上下集按钮同样只在全屏显示（竖屏时选集列表就在播放器下面）
                previousEpisode = if (fullscreen) {
                    previousEpisode?.let { target -> { onSelectEpisode(target.id) } }
                } else {
                    null
                },
                nextEpisode = if (fullscreen) {
                    nextEpisode?.let { target -> { onSelectEpisode(target.id) } }
                } else {
                    null
                },
                // 选集抽屉按钮只在全屏显示（竖屏时选集列表就在播放器下面），且至少要两集才有意义
                episodeDrawer = if (fullscreen && sortedEpisodes.size > 1) {
                    { drawerOpen = true }
                } else {
                    null
                },
            )
        }

        // ===== 全屏选集抽屉 =====
        //
        // 层级（从低到高）：画面 < 手势层 < 控制栏 < 遮罩 < 抽屉。
        // 遮罩夹在控制栏和抽屉之间，是为了「点抽屉外面就关闭」——
        // 如果遮罩在控制栏下面，点在控制栏区域会先触发播放/进度条，而不是关抽屉。
        if (drawerOpen) {
            // 遮罩分两层，别把"点阴影关闭"做成一整块都能关：
            // 1) 整块压暗——纯绘制，不接任何手势；
            // 2) 只有抽屉以外的那块阴影接点击 → 点阴影才关抽屉。
            //    抽屉自己那块不接，所以点标题、点按钮之间的空隙都算"点在抽屉里"，不关。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        // 给抽屉让出右侧宽度：padding 之后的这段才是点击命中区（左侧阴影）
                        .padding(end = EpisodeDrawerWidth)
                        .pointerInput(Unit) {
                            detectTapGestures { drawerOpen = false }
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            EpisodeDrawer(
                episodes = sortedEpisodes,
                currentEpisodeId = episode.id,
                onSelect = { id ->
                    // 只是把「当前集」交给外层；换源由外层改 episode 后走同一个播放器实例完成，
                    // 所以全屏状态不会因为切集而退出，切完也会自动接着播。
                    onSelectEpisode(id)
                    drawerOpen = false
                },
            )
        }

        // 分辨率与缓冲/丢帧统计不再画在画面上（会跟 B 站 风格的控制栏打架），
        // 需要看的时候用 adb logcat -s NekoPlayer：每 5 秒一条 pos/dur/buffered/playing。
    }
}

/**
 * 从 Compose 的 Context 里找 Activity（改亮度、切方向都要用）。
 *
 * @returns Activity；找不到返回 null
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }

        current = current.baseContext
    }

    return null
}
