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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.state.SessionState
import top.nekoanime.mobile.app.component.theme.NekoDialogShape

/** 昵称最大长度（跟后端一致：24 个字符） */
private const val MaxNicknameLength = 24

/**
 * 修改昵称弹框。
 *
 * @description 改的是 **user_profile.nickname**（展示名），不是登录名 username：
 * 登录名是账号标识，不能改；昵称只影响界面展示，改完不会被 Bangumi 同步覆盖。
 *
 * @param current 当前昵称（打开时预填，方便只改几个字）
 * @param onDismiss 关闭
 * @param onChanged 修改成功（参数是新昵称）
 */
@Composable
fun NicknameDialog(
    current: String,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit = {},
) {
    var nickname by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        // 圆角统一 8dp
        shape = NekoDialogShape,
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改名称") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "昵称只影响站内展示，登录名不受影响。最多 $MaxNicknameLength 个字符。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val value = nickname.trim()

                    when {
                        value.isEmpty() -> error = "昵称不能为空"
                        value.length > MaxNicknameLength -> error = "昵称最多 $MaxNicknameLength 个字符"
                        value == current -> {
                            // 没改就别打接口了
                            onDismiss()
                        }

                        else -> scope.launch {
                            submitting = true
                            error = null

                            UserRepository.updateNickname(value)
                                .onSuccess { updated ->
                                    // 接口会返回最新资料，直接用，省一次查询
                                    SessionState.applyProfile(updated)
                                    submitting = false
                                    onChanged(updated.displayName)
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
