package top.nekoanime.mobile.app.component

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import top.nekoanime.mobile.app.data.api.UserAuthApi
import top.nekoanime.mobile.app.state.SessionState

/**
 * Bangumi 一键登录弹框（内置 WebView）。
 *
 * @description 流程与网页端一致，只是把浏览器换成了 App 内的 WebView：
 * 1. 打开后端的 `/api/auth/oauth/login/bangumi`，它会 302 去 Bangumi 授权页；
 * 2. 授权完成后后端带着 `temp_id` 跳回站点回调地址 —— 这个跳转在 WebView 里被拦下（不真的加载网页）；
 * 3. 拿 `temp_id` 调 `.../status` 换 JWT，交给 [SessionState] 保存并关闭弹框。
 *
 * 账号没绑定过 Bangumi 时会拿到 404（后端在网页端是引导去注册页），这里给一句明确提示，
 * 避免用户停在空白页上不知道发生了什么。
 *
 * @param onDismiss 关闭弹框
 * @param onLoggedIn 登录成功
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BangumiLoginDialog(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit = {},
) {
    var error by remember { mutableStateOf<String?>(null) }
    var exchanging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loginUrl = remember { UserAuthApi.bangumiLoginUrl() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = NekoDialogShape, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "用 Bangumi 账号登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (exchanging) "正在登录…" else "在下面完成 Bangumi 授权即可",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val url = request.url.toString()
                                    val tempId = Uri.parse(url).getQueryParameter("temp_id")

                                    if (tempId.isNullOrBlank() || exchanging) {
                                        return false
                                    }

                                    // 拦下跳转：不再真的加载网页，直接拿 temp_id 换 token
                                    exchanging = true
                                    error = null

                                    scope.launch {
                                        UserAuthApi.bangumiLoginStatus(tempId)
                                            .onSuccess { token ->
                                                SessionState.onLoggedIn(token)
                                                onLoggedIn()
                                                onDismiss()
                                            }
                                            .onFailure {
                                                exchanging = false
                                                error = "这个 Bangumi 账号还没有绑定本地账号。" +
                                                    "请先在网页端用 Bangumi 注册/绑定，或换一个已绑定的账号。"
                                            }
                                    }

                                    return true
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("取消")
                }
            }
        }
    }
}
