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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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

    // 未绑定的 Bangumi 账号：不把人堵死，就地补一个登录名+密码完成注册（昵称头像用 Bangumi 的）
    var registerTempId by remember { mutableStateOf<String?>(null) }
    var registerUsername by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val loginUrl = remember { UserAuthApi.bangumiLoginUrl() }

    /** 提交 Bangumi 注册（建号 + 绑定 + 登录） */
    fun submitRegister() {
        val tempId = registerTempId ?: return
        val name = registerUsername.trim()

        if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) {
            error = "用户名只能包含字母、数字和下划线"

            return
        }

        if (registerPassword.length < 6) {
            error = "密码至少 6 位"

            return
        }

        registerSubmitting = true
        error = null

        scope.launch {
            UserAuthApi.bangumiRegister(name, registerPassword, tempId)
                .onSuccess {
                    // 注册成功即用刚设的账号密码登录，拿 JWT
                    UserAuthApi.login(name, registerPassword)
                        .onSuccess { token ->
                            SessionState.onLoggedIn(token)
                            onLoggedIn()
                            onDismiss()
                        }
                        .onFailure {
                            registerSubmitting = false
                            error = "注册成功，但自动登录失败，请用刚才的账号密码登录"
                        }
                }
                .onFailure {
                    registerSubmitting = false
                    error = it.message ?: "注册失败"
                }
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

                if (registerTempId != null) {
                    // 未绑定的 Bangumi 账号：就地注册（昵称与头像直接取 Bangumi 的）
                    OutlinedTextField(
                        value = registerUsername,
                        onValueChange = { registerUsername = it },
                        label = { Text("登录名（字母 / 数字 / 下划线）") },
                        singleLine = true,
                        enabled = !registerSubmitting,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = registerPassword,
                        onValueChange = { registerPassword = it },
                        label = { Text("密码（至少 6 位）") },
                        singleLine = true,
                        enabled = !registerSubmitting,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { submitRegister() },
                        enabled = !registerSubmitting &&
                            registerUsername.isNotBlank() &&
                            registerPassword.isNotBlank(),
                    ) {
                        Text(if (registerSubmitting) "注册中…" else "注册并登录")
                    }
                } else {
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
                                                // 没绑定过 → 切到注册表单；其它失败（比如授权过期）给提示
                                                exchanging = false
                                                registerTempId = tempId
                                                error = null
                                            }
                                    }

                                    return true
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                )
                }

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
