package top.nekoanime.mobile.app.page.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import top.nekoanime.mobile.app.R
import top.nekoanime.mobile.app.data.local.UpdatePreference
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import top.nekoanime.mobile.app.service.UpdateService
import top.nekoanime.mobile.app.state.SessionState

/**
 * 启动页最短展示时长（毫秒）。
 *
 * @description 初始化很快时（本机有 token、网络又快）启动页会一闪而过，
 * 视觉上像是界面闪了一下；给一个下限让过渡稳定，同时也不会让用户白等。
 */
private const val MinSplashMillis = 700L

/**
 * 启动页：负责 App 的初始化。
 *
 * @description 干四件事，做完才进首页：
 * 1. 拉一次用户资料（本机有 token 时刷新昵称/头像，顶栏要用）；
 * 2. **检查更新**（走 `/api/app/update`，跟第 1 步并发，不额外拖慢启动）；
 * 3. **清理更新目录**：上次下载中断留下的 `.part` 半成品、以及不再是目标版本的旧安装包；
 * 4. 保证启动页至少显示 {@link MinSplashMillis}，避免界面闪一下。
 *
 * 结束后通过 [onReady] 把「有没有新版本」交给上层：进首页后由上层弹更新窗，
 * 这样弹窗出现在首页而不是启动页上（跟用户要的行为一致）。
 *
 * @param onReady 初始化完成回调；参数是新版本信息（没有更新或检查失败时为 null）
 */
@Composable
fun SplashScreen(onReady: (AppUpdateDto?) -> Unit) {
    val context = LocalContext.current
    // 站点 Logo 是「纯黑 + 透明底」的图形，深色模式下直接用会看不见，
    // 所以深色模式把它整体染成白色（SrcIn 保留原来的透明度轮廓）。
    val darkMode = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()

        // 检查更新与拉资料并发：两者互不依赖，串行只会让启动页更久
        val updateResult = coroutineScope {
            val updateTask = async { UpdateService.check() }

            SessionState.refreshProfile()

            updateTask.await()
        }

        val update = updateResult.getOrNull()

        // 检查成功才清理：网络不通时不知道目标版本，清掉可能让已经下好的包白下
        if (updateResult.isSuccess) {
            UpdateService.cleanup(context, update?.version)
        }

        val elapsed = System.currentTimeMillis() - startedAt

        if (elapsed < MinSplashMillis) {
            delay(MinSplashMillis - elapsed)
        }

        // 用户勾过「7 天内不再提示」就安静启动（强制更新除外）
        val visible = update?.takeIf { it.force || !UpdatePreference(context).isSnoozed() }

        onReady(visible)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "NekoAnime",
                // 站点 Logo：保持比例铺满宽度，不加文字标题（Logo 本身带字）
                contentScale = ContentScale.Fit,
                colorFilter = if (darkMode) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null,
                modifier = Modifier.width(220.dp),
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(24.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
