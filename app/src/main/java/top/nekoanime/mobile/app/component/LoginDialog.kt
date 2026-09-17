package top.nekoanime.mobile.app.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.theme.NekoDialogShape
import top.nekoanime.mobile.app.data.api.UserAuthApi
import top.nekoanime.mobile.app.state.SessionState

/**
 * 登录弹框。
 *
 * @description 本地账号登录：用户名 + 密码 → `POST /api/user/auth/login` 拿到 JWT →
 * 交给 {@link SessionState} 保存（之后所有接口自动带 Authorization），并顺便拉一次用户资料。
 * 登录成功后自动关闭。
 *
 * @param onDismiss 关闭弹框
 * @param onLoggedIn 登录成功（外层可以用来刷新页面数据）
 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLoggedIn: () -> Unit = {},
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var bangumiOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    /** 提交登录 */
    fun submit() {
        if (submitting || username.isBlank() || password.isBlank()) {
            return
        }

        scope.launch {
            submitting = true
            error = null

            UserAuthApi.login(username.trim(), password)
                .onSuccess { token ->
                    SessionState.onLoggedIn(token)
                    submitting = false
                    onLoggedIn()
                    onDismiss()
                }
                .onFailure {
                    submitting = false
                    error = it.message ?: "登录失败"
                }
        }
    }

    AlertDialog(
        // 圆角统一 8dp
        shape = NekoDialogShape,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            DialogHeader(
                title = "登录 NekoAnime",
                subtitle = "登录后追番与观看进度会同步到账号",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Bangumi 一键登录：账号没绑定过时会提示先去网页端注册/绑定
                TextButton(onClick = { bangumiOpen = true }, enabled = !submitting) {
                    Text("用 Bangumi 账号登录")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && username.isNotBlank() && password.isNotBlank(),
                onClick = { submit() },
            ) {
                Text(if (submitting) "登录中…" else "登录")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") }
        },
    )

    if (bangumiOpen) {
        BangumiLoginDialog(
            onDismiss = { bangumiOpen = false },
            onLoggedIn = {
                onLoggedIn()
                onDismiss()
            },
        )
    }
}
