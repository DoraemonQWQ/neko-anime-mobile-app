package top.nekoanime.mobile.app.page.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.component.EntryRow
import top.nekoanime.mobile.app.component.BangumiBindingRow

/** 头像容器高度（跟 Flutter 版的 avatarBox 一个量级） */
private val AvatarBoxHeight = 240.dp

/** 头像大小（跟 Flutter 版一致：120dp） */
private val AvatarSize = 120.dp

/**
 * 「用户」Tab。
 *
 * @description 结构对齐 Flutter 版的 user_tab_page：
 * 一个大头像区（未登录显示默认头像 + 登录按钮）+ 历史记录 / 设置两个入口。
 * 用户系统接口（昵称、头像、追番、历史）接上后往这里填，页面骨架不再改。
 *
 * @param loggedIn 是否已登录
 * @param username 用户名（没取到就显示「未登录」）
 * @param avatarUrl 头像地址
 * @param onLoginClick 点登录（外层弹登录框）
 * @param onLogout 退出登录
 * @param onOpenHistory 打开观看历史页
 * @param onOpenSettings 打开设置页
 */
@Composable
fun UserTab(
    loggedIn: Boolean,
    username: String?,
    avatarUrl: String?,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 头像区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AvatarBoxHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loggedIn && !avatarUrl.isNullOrBlank()) {
                    CoverImage(
                        url = avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(AvatarSize)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AvatarSize),
                    )
                }

                Text(
                    text = if (loggedIn) (username ?: "已登录") else "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (loggedIn) {
                    OutlinedButton(onClick = onLogout) { Text("退出登录") }
                } else {
                    Button(onClick = onLoginClick) { Text("登录") }
                }
            }
        }

        // 入口列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EntryRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "历史记录",
                // 观看历史存在服务端、必须登录才能查；未登录时置灰不可点，而不是点进去报 401
                enabled = loggedIn,
                onClick = onOpenHistory,
            )

            // Bangumi 绑定：放在历史记录下面；自己管绑定状态，未登录时置灰
            BangumiBindingRow(loggedIn = loggedIn)

            EntryRow(
                icon = Icons.Filled.Settings,
                title = "设置",
                onClick = onOpenSettings,
            )
        }
    }
}
