@file:OptIn(ExperimentalCoilApi::class)

package top.nekoanime.mobile.app.page.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.BuildConfig
import top.nekoanime.mobile.app.component.ChangePasswordDialog
import top.nekoanime.mobile.app.component.EntryRow
import top.nekoanime.mobile.app.component.NicknameDialog
import top.nekoanime.mobile.app.component.UpdateDialog
import top.nekoanime.mobile.app.data.repository.UserRepository
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import top.nekoanime.mobile.app.page.settings.component.AvatarCropDialog
import top.nekoanime.mobile.app.service.UpdateService
import top.nekoanime.mobile.app.state.SessionState
import top.nekoanime.mobile.app.logging.AppLog
import androidx.compose.material.icons.filled.Description

/**
 * 设置页。
 *
 * @description 四件事：
 * 1. **更换头像**：相册选图 → 圆形裁剪（可缩放拖动）→ 直传对象存储 → 写库；
 * 2. **修改密码**：弹框输入原密码 + 两次新密码 → 后端校验；
 * 3. **清除缓存**：清掉图片库（Coil）的内存与磁盘缓存；
 * 4. **检查更新**：调后端 `/api/app/update` → 有新版本弹更新框 → 下载 → 交给系统安装。
 *
 * 启动时也会自动检查一次更新（见启动页），这里是手动入口，方便用户自己点。
 *
 * @param onBack 返回上一页
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = context.imageLoader

    var message by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var cacheSize by remember { mutableStateOf(0L) }

    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateDto?>(null) }

    BackHandler { onBack() }

    // 进页面读一次图片缓存大小
    LaunchedEffect(Unit) {
        // 缓存大小同时计入持久化日志，清除入口对应实际会删除的图片和日志数据。
        cacheSize = (imageLoader.diskCache?.size ?: 0L) + AppLog.sizeBytes()
    }

    // 相册选图（系统照片选择器，不需要存储权限）→ 选完直接进裁剪弹窗
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            cropUri = uri
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：内容 56dp、状态栏留白在外
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "设置",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 头像与密码都是账号相关的操作：未登录时直接不显示（点进去也只会 401）
            if (SessionState.loggedIn) {
                EntryRow(
                    icon = Icons.Filled.AccountCircle,
                    title = if (uploadingAvatar) "正在上传头像…" else "更换头像",
                    trailing = if (uploadingAvatar) null else "相册选图",
                    onClick = {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )

                EntryRow(
                    icon = Icons.Filled.Lock,
                    title = "修改密码",
                    onClick = { showPasswordDialog = true },
                )

                EntryRow(
                    icon = Icons.Filled.Edit,
                    title = "修改名称",
                    // 右侧显示当前昵称，省得点进去才知道现在叫什么
                    trailing = SessionState.profile?.displayName ?: "",
                    onClick = { showNicknameDialog = true },
                )
            }

            EntryRow(
                icon = Icons.Filled.CleaningServices,
                title = "清除缓存",
                trailing = formatSize(cacheSize),
                onClick = {
                    scope.launch {
                        // 清理失败必须提示，只有图片与持久化日志均完成清理后才报告成功。
                        runCatching {
                            imageLoader.memoryCache?.clear()
                            imageLoader.diskCache?.clear()
                            AppLog.clear()
                        }.onSuccess {
                            cacheSize = 0L
                            message = "缓存和日志已清除"
                        }.onFailure {
                            message = "清除失败，请重试"
                        }
                    }
                },
            )

            EntryRow(
                icon = Icons.Filled.Refresh,
                title = if (checking) "正在检查更新…" else "检查更新",
                trailing = "当前 v${BuildConfig.VERSION_NAME}",
                onClick = {
                    if (checking) {
                        return@EntryRow
                    }

                    scope.launch {
                        checking = true
                        message = null

                        UpdateService.check()
                            .onSuccess { found ->
                                if (found == null) {
                                    message = "已是最新版本（v${BuildConfig.VERSION_NAME}）"
                                } else {
                                    // 这里是用户主动点「检查更新」：即使设过「7 天不再提示」也照样打开，
                                    // 否则用户想升级都没有入口（免打扰只针对启动时的自动提示）
                                    update = found
                                }
                            }
                            .onFailure { message = "检查更新失败：${it.message}" }

                        checking = false
                    }
                },
            )

            // 日志查看是设置页最后一个操作入口，业务只依赖日志抽象而不直接调用 VLog。
            EntryRow(
                icon = Icons.Filled.Description,
                title = "查看日志",
                trailing = "最近 7 天",
                onClick = {
                    scope.launch {
                        runCatching { AppLog.openViewer(context) }
                            .onFailure { message = "暂时无法打开日志" }
                    }
                },
            )

            message?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onChanged = { message = "密码已修改" },
        )
    }

    if (showNicknameDialog) {
        NicknameDialog(
            current = SessionState.profile?.displayName ?: "",
            onDismiss = { showNicknameDialog = false },
            onChanged = { newName -> message = "昵称已改为 $newName" },
        )
    }

    // 裁剪弹窗：上传期间保持在界面上（busy 会让按钮禁用），传完自动关闭
    cropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            busy = uploadingAvatar,
            onCancel = { if (!uploadingAvatar) cropUri = null },
            onConfirm = { bytes ->
                scope.launch {
                    uploadingAvatar = true
                    message = null

                    UserRepository.uploadAvatar(bytes, extension = ".png")
                        .onSuccess {
                            // 传完刷新资料：顶栏与用户页的头像立刻跟着变
                            SessionState.refreshProfile()
                            message = "头像已更新"
                            cropUri = null
                        }
                        .onFailure { message = "上传失败：${it.message}" }

                    uploadingAvatar = false
                }
            },
        )
    }

    update?.let { info ->
        UpdateDialog(
            info = info,
            // 用户主动检查更新：不给「7 天内不再提示」，他就是专门来看的
            allowSnooze = false,
            onDismiss = { update = null },
            onMessage = { message = it },
        )
    }
}

/**
 * 字节 → 可读大小（KB / MB）。
 *
 * @param bytes 字节数
 * @returns 形如 1.2 MB 的文本
 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
