package top.nekoanime.mobile.app.page.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.nekoanime.mobile.app.component.CoverImage
import top.nekoanime.mobile.app.component.NekoSearchBar

/**
 * 顶栏内容高度：Flutter 版 kToolbarHeight = 56（逻辑像素）= Compose 的 56dp。
 *
 * @description 注意 56 是**内容高度**，状态栏那一条由 {@code statusBarsPadding} 加在外面——
 * 之前把 height(56) 套在 Material 的 TopAppBar 外面，等于把状态栏留白也算进 56 里，内容就被压扁了。
 */
private val TopBarContentHeight = 56.dp

/** 头像尺寸（跟 Flutter 版 avatarSize 一致：40dp） */
private val AvatarSize = 40.dp

/** 顶栏左右内边距（跟 Flutter 版一致：16dp） */
private val TopBarHorizontalPadding = 16.dp

/**
 * 首页顶栏（主页 / 番剧 / 用户 三个 Tab 共用）。
 *
 * @description 对着 Flutter 版 `tab_page_app_bar` 写：
 * [头像 40dp] + 16dp 间隔 + [搜索框占满剩余宽度]，内容高 56dp、状态栏留白在外、左右内边距 16dp。
 * - 头像一直在：未登录显示默认账号图标，已登录显示真实头像（用户接口还没接，先留空）；
 * - 搜索框是"假"的（按钮模式）：点一下跳搜索页。
 *
 * @param loggedIn 是否已登录
 * @param avatarUrl 头像地址
 * @param onAvatarClick 点头像
 * @param onSearchClick 点搜索框
 */
@Composable
fun HomeTopBar(
    loggedIn: Boolean,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 系统栏留白在外：内容高度就是纯粹的 56dp
            .statusBarsPadding()
            .height(TopBarContentHeight)
            .padding(horizontal = TopBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onAvatarClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loggedIn && !avatarUrl.isNullOrBlank()) {
                CoverImage(
                    url = avatarUrl,
                    contentDescription = "个人信息",
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "个人信息",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AvatarSize),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        NekoSearchBar(
            placeholder = "搜索番剧",
            onPressed = onSearchClick,
            modifier = Modifier.weight(1f),
        )
    }
}
