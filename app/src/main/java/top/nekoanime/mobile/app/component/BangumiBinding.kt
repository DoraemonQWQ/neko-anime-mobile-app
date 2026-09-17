package top.nekoanime.mobile.app.component

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.theme.NekoDialogShape
import top.nekoanime.mobile.app.data.api.UserIdentityApi

/**
 * 个人页的「Bangumi 绑定」入口。
 *
 * @description 自己管状态：进来拉一次绑定状态，点一下按状态走绑定或解绑。
 * 未登录时置灰（绑定接口需要登录态）。
 *
 * @param loggedIn 是否已登录
 */
@Composable
fun BangumiBindingRow(loggedIn: Boolean) {
    var bound by remember { mutableStateOf(false) }
    var bangumiNickname by remember { mutableStateOf("") }
    var statusLoaded by remember { mutableStateOf(false) }
    var bindDialogOpen by remember { mutableStateOf(false) }
    var unbindConfirmOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    /** 拉一次绑定状态 */
    suspend fun refresh() {
        UserIdentityApi.bangumiBinding()
            .onSuccess {
                bound = it.bound
                bangumiNickname = it.nickname
            }

        statusLoaded = true
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            refresh()
        } else {
            bound = false
            statusLoaded = true
        }
    }

    val trailing = when {
        !loggedIn -> "登录后可用"
        !statusLoaded -> "查询中…"
        bound -> bangumiNickname.ifBlank { "已绑定" }
        else -> "未绑定"
    }

    EntryRow(
        icon = Icons.Filled.Sync,
        title = "Bangumi 绑定",
        enabled = loggedIn && !busy,
        trailing = trailing,
        onClick = {
            if (bound) {
                unbindConfirmOpen = true
            } else {
                bindDialogOpen = true
            }
        },
    )

    message?.let { text ->
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }

    if (bindDialogOpen) {
        BangumiBindDialog(
            onDismiss = { bindDialogOpen = false },
            onBound = {
                message = "Bangumi 绑定成功"
                scope.launch { refresh() }
            },
        )
    }

    if (unbindConfirmOpen) {
        AlertDialog(
            shape = NekoDialogShape,
            onDismissRequest = { if (!busy) unbindConfirmOpen = false },
            title = { Text("解绑 Bangumi") },
            text = { Text("解绑后这个 Bangumi 账号的追番与进度不再同步，确定解绑吗？") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            UserIdentityApi.unbindBangumi()
                                .onSuccess {
                                    message = "已解绑 Bangumi"
                                    refresh()
                                }
                                .onFailure { message = it.message ?: "解绑失败" }

                            busy = false
                            unbindConfirmOpen = false
                        }
                    },
                ) {
                    Text("解绑")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { unbindConfirmOpen = false }) { Text("取消") }
            },
        )
    }
}

/**
 * Bangumi 绑定弹框（内置 WebView）。
 *
 * @description 与登录不同：绑定要先带着登录态去后端换到授权地址（后端 302），
 * 所以在 App 侧请求拿到 Location 之后再交给 WebView 打开；授权完成后后端带
 * `bind=success|failed&reason=...` 跳回，拦下这个跳转即完成。
 *
 * @param onDismiss 关闭
 * @param onBound 绑定成功
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BangumiBindDialog(
    onDismiss: () -> Unit,
    onBound: () -> Unit = {},
) {
    var authorizeUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        UserIdentityApi.bangumiBindUrl()
            .onSuccess { authorizeUrl = it }
            .onFailure {
                loading = false
                error = it.message ?: "获取 Bangumi 授权地址失败"
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = NekoDialogShape, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "绑定 Bangumi 账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        loading -> "正在打开 Bangumi 授权页…"
                        else -> "在下面完成授权即可绑定"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                authorizeUrl?.let { url ->
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        webError: WebResourceError,
                                    ) {
                                        if (!request.isForMainFrame) {
                                            return
                                        }

                                        loading = false
                                        error = "打不开 Bangumi 授权页：${webError.description}。" +
                                            "请检查手机网络（必要时挂代理）后重试。"
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val target = request.url.toString()
                                        val bindState = Uri.parse(target).getQueryParameter("bind")
                                            ?: return false

                                        if (bindState == "success") {
                                            onBound()
                                            onDismiss()
                                        } else {
                                            loading = false
                                            error = Uri.parse(target).getQueryParameter("reason")
                                                ?: "绑定失败"
                                        }

                                        return true
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
