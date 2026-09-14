package top.nekoanime.mobile.app.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.component.theme.NekoDialogShape

/**
 * 修改密码弹框。
 *
 * @description 三个输入框：原密码、新密码、确认新密码；本地先校验两次新密码一致，
 * 再调 `POST /api/user/auth/password`。成功后由外层提示（后端不返回新 token）。
 *
 * 注意：这是**用户改自己的密码**；管理员改别人的密码走后台接口（以后在管理端做）。
 *
 * @param onDismiss 关闭
 * @param onChanged 修改成功
 */
@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        // 圆角统一 8dp
        shape = NekoDialogShape,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("原密码") },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    // 本地先做最简单的校验，避免白跑一次网络
                    when {
                        oldPassword.isBlank() || newPassword.isBlank() -> error = "请把三个输入框都填上"
                        newPassword != confirmPassword -> error = "两次输入的新密码不一致"
                        newPassword.length < 6 -> error = "新密码至少 6 位"
                        else -> scope.launch {
                            submitting = true
                            error = null

                            UserRepository.changePassword(oldPassword, newPassword)
                                .onSuccess {
                                    submitting = false
                                    onChanged()
                                    onDismiss()
                                }
                                .onFailure {
                                    submitting = false
                                    error = it.message ?: "修改失败"
                                }
                        }
                    }
                },
            ) {
                Text(if (submitting) "提交中…" else "确定")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") }
        },
    )
}
