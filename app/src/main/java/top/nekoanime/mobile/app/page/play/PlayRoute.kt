package top.nekoanime.mobile.app.page.play

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.data.repository.AnimeRepository
import top.nekoanime.mobile.app.model.entity.AnimeDetail

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
fun PlayRoute(
    animeId: Int,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    startEpisodeId: Int? = null,
    startPositionMs: Long = 0L,
) {
    var anime by remember(animeId) { mutableStateOf<AnimeDetail?>(null) }
    var error by remember(animeId) { mutableStateOf<String?>(null) }
    var loading by remember(animeId) { mutableStateOf(true) }

    // 系统返回键：播放页的返回是先退全屏、再回上一页
    BackHandler {
        if (fullscreen) {
            onToggleFullscreen()
        } else {
            onBack()
        }
    }

    LaunchedEffect(animeId) {
        loading = true
        error = null

        AnimeRepository.detail(animeId)
            .onSuccess { anime = it }
            .onFailure { error = it.message ?: "加载失败" }

        loading = false
    }

    val detail = anime

    if (detail != null) {
        PlayScreen(
            anime = detail,
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

    // 详情还没好（或失败）：给一个黑底占位，别让用户看白屏
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(
                text = "番剧信息加载失败\n$error\n（按返回键回上一页）",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )

            loading -> CircularProgressIndicator(color = Color(0xFFE91E63))
            else -> Unit
        }
    }
}
