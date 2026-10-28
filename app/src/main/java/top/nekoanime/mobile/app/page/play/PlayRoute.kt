package top.nekoanime.mobile.app.page.play

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.component.rememberSaveableState
import top.nekoanime.mobile.app.component.PlaySkeleton
import top.nekoanime.mobile.app.model.entity.AnimeDetail
import top.nekoanime.mobile.app.state.SessionState
import top.nekoanime.mobile.app.logging.AppLog as Log
import kotlinx.coroutines.launch

/**
 * 播放路由。
 *
 * @description 移动端没有独立的详情页：从首页点卡片就直接进播放页，
 * 所以这里先按番剧 id 从后端取详情（番剧信息 + 剧集列表），拿到之后再交给 {@link PlayScreen}。
 * 加载失败就显示错误（并提供返回），不会给用户一个空白页。
 *
 * @param animeId 番剧 id
 * @param fullscreen 是否全屏
 * @param onToggleFullscreen 切换全屏
 * @param onBack 返回（回上一页）
 * @param onHome 回主页
 * @param onOpenAnime 点推荐卡片（参数是番剧 id）
 * @param startEpisodeId 从历史记录/继续观看进来时指定的剧集 id
 * @param startPositionMs 与 startEpisodeId 配套的播放位置
 */
@Composable
fun PlayRouteScreen(
    animeId: Int,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    startEpisodeId: Int? = null,
    startPositionMs: Long = 0L,
) {
    // 详情与加载状态保存：从推荐页返回时直接用已有详情，不再请求一次
    val animeState = rememberSaveableState<AnimeDetail>("play_detail_$animeId")
    // 播放会话（含 ExoPlayer）：跟着这个播放页的返回栈条目存活，
    // 所以「A → 推荐 → B → 返回 A」时 A 的播放器还是原来那个，直接续播不重新加载
    val playback: PlaybackViewModel = viewModel()
    val context = LocalContext.current
    val player = remember(playback) { playback.player(context) }
    val scope = rememberCoroutineScope()
    // 只记"详情加载失败了"这一个布尔值：原始报错（接口、后端说明）只写 logcat，不上界面
    var failed by rememberSaveable(animeId) { mutableStateOf(false) }
    var loading by rememberSaveable(animeId) { mutableStateOf(true) }
    val anime = animeState.value

    // 系统返回键：播放页的返回是先退全屏、再回上一页
    BackHandler {
        if (fullscreen) {
            onToggleFullscreen()
        } else {
            onBack()
        }
    }

    LaunchedEffect(animeId) {
        // 详情已经在（返回本页）：直接用缓存
        if (anime != null && anime.id == animeId) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        failed = false

        AnimeRepository.detail(animeId)
            .onSuccess { animeState.value = it }
            .onFailure { failure ->
                // 详情加载失败的具体原因只进日志，用户只需要知道"失败了、可以退回"
                Log.e("PlayRoute", "番剧详情加载失败: animeId=$animeId", failure)
                failed = true
            }

        loading = false
    }

    val detail = anime

    // 追番状态：跟番剧走，返回本页时还在。未登录时永远是 false（按钮也是禁用的）
    var subscribed by rememberSaveable(animeId) { mutableStateOf(false) }
    // 请求进行中：挡住连点，避免同一个按钮点两下发出两次取反请求
    var subscribeBusy by remember(animeId) { mutableStateOf(false) }

    // 登录了才问后端要状态；详情还没到、或者退出登录，都回到"未追番"
    LaunchedEffect(detail?.id, SessionState.loggedIn) {
        val current = detail

        if (current == null || !SessionState.loggedIn) {
            subscribed = false
            return@LaunchedEffect
        }

        UserRepository.subscribed(current.id).onSuccess { subscribed = it }
    }

    /**
     * 点追番 / 取消追番。
     *
     * @description 成功后用**后端返回的状态**更新界面（不拿本地状态猜），并提示一句结果；
     * 失败就只提示、不改状态，用户可以再点一次。未登录时按钮本来就是禁用的，这里再挡一层。
     */
    fun toggleSubscribe() {
        val current = detail ?: return

        if (subscribeBusy || !SessionState.loggedIn) {
            return
        }

        val target = !subscribed
        subscribeBusy = true

        scope.launch {
            UserRepository.updateSubscribed(current.id, target)
                .onSuccess { latest ->
                    subscribed = latest
                    Toast.makeText(
                        context,
                        if (latest) "已加入追番" else "已取消追番",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { failure ->
                    Toast.makeText(
                        context,
                        failure.message ?: "操作失败，请稍后再试",
                        Toast.LENGTH_SHORT,
                    ).show()
                }

            subscribeBusy = false
        }
    }

    if (detail != null) {
        PlayScreen(
            anime = detail,
            player = player,
            loggedIn = SessionState.loggedIn,
            subscribed = subscribed,
            onToggleSubscribe = { toggleSubscribe() },
            fullscreen = fullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onBack = onBack,
            onHome = onHome,
            onOpenAnime = onOpenAnime,
            startEpisodeId = startEpisodeId,
            startPositionMs = startPositionMs,
        )

        return
    }

    // 详情还没好（或失败）：别让用户看白屏
    when {
        // 加载失败：给一块黑底 + 说明（播放页上方本来就是黑底）
        failed -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "番剧信息加载失败\n（按返回键回上一页）",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }

        // 加载中：按播放页真实结构摆骨架（上方固定黑底播放器 + 下方主题色内容区）
        loading -> PlaySkeleton()
        else -> Unit
    }
}
