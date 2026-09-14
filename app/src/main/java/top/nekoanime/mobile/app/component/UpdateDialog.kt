package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.nekoanime.mobile.app.component.theme.NekoDialogShape
import top.nekoanime.mobile.app.data.local.UpdatePreference
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import top.nekoanime.mobile.app.service.UpdateService
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 更新说明区域的最大高度（超过就自己滚，别把弹框撑到屏幕外） */
private val ChangelogMaxHeight = 200.dp

/** 「不再提示」的天数 */
private const val SnoozeDays = 7L

/**
 * 更新弹窗。
 *
 * @description 两个入口共用一个弹窗：
 * - **启动时自动检查**（启动页初始化完、进入首页后弹出）；
 * - **设置页手动点「检查更新」**。
 *
 * 两个入口的差别：启动时的**主动提醒**可以勾「7 天内不再提示」；
 * 用户在设置里**主动检查**时不给这个选项（`allowSnooze = false`），因为用户就是专门来看有没有新版的。
 *
 * 交互：「下载并安装」→ 带进度下载 → 下完**自动**交给系统安装界面；已下过的话按钮变成「重新安装」。
 * 弹窗一出现就先查本机有没有下好同一个包（上次装到一半退出了），有就直接安装，不会重复下载。
 * 强制更新（`force`）不给「稍后」，且下载过程中不能关闭。
 *
 * @param info 新版本信息（版本号、更新说明、下载地址等）
 * @param onDismiss 关闭弹窗（点「稍后」）
 * @param onMessage 需要提示给用户的信息（下载失败等）；设置页用来显示在列表下方
 * @param allowSnooze 是否允许「7 天内不再提示」；用户主动检查更新时传 false
 */
@Composable
fun UpdateDialog(
    info: AppUpdateDto,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit = {},
    allowSnooze: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var apk by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // 只有非强制更新才给「不再提示」的选项
    var snoozeChecked by remember { mutableStateOf(false) }

    // 打开弹窗先看本机有没有下好同一个版本：有就直接调起安装（避免重复下载 20MB）
    LaunchedEffect(info.version) {
        val downloaded = UpdateService.findDownloaded(context, info)

        if (downloaded != null) {
            apk = downloaded
            UpdateService.install(context, downloaded)
        }
    }

    AlertDialog(
        // 圆角统一 8dp（Material 默认 28dp 太圆）
        shape = NekoDialogShape,
        // 下载过程中不允许点外面关掉：这时候关掉只会让用户以为下载还在继续
        onDismissRequest = { if (!downloading && !info.force) onDismiss() },
        title = {
            DialogHeader(
                title = "发现新版本",
                subtitle = "v${info.version}${sizeSuffix(info.fileSize)}",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                formatPublishedAt(info.publishedAt)?.let { published ->
                    Text(
                        text = "发布时间 $published",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "更新内容",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = info.changelog.ifBlank { "本次更新没有写说明" },
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .heightIn(max = ChangelogMaxHeight)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }

                if (downloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "已下载 ${(progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = downloadAmountText(info.fileSize, progress),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (apk != null) {
                    Text(
                        text = "已自动调起系统安装，请按提示完成更新（取消过的话可以点「重新安装」）",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (allowSnooze && !info.force) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { snoozeChecked = !snoozeChecked },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = snoozeChecked,
                            enabled = !downloading,
                            onCheckedChange = { snoozeChecked = it },
                        )

                        Text(
                            text = "$SnoozeDays 天内不再提示",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                error?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            val downloaded = apk

            Button(
                enabled = !downloading,
                onClick = {
                    if (downloaded != null) {
                        // 用户在系统安装界面点了取消，这里允许再调起一次
                        UpdateService.install(context, downloaded)
                        return@Button
                    }

                    scope.launch {
                        downloading = true
                        progress = 0f
                        error = null

                        UpdateService.download(context, info) { value -> progress = value }
                            .onSuccess { file ->
                                apk = file
                                // 下载完成即自动安装：不让用户再点一次，也避免"下完了没人装"
                                UpdateService.install(context, file)
                            }
                            .onFailure { failure ->
                                error = failure.message ?: "下载失败"
                                onMessage(failure.message ?: "下载失败")
                            }

                        downloading = false
                    }
                },
            ) {
                Text(
                    when {
                        downloading -> "下载中…"
                        downloaded != null -> "重新安装"
                        else -> "下载并安装"
                    },
                )
            }
        },
        dismissButton = {
            // 强制更新不给「稍后」：只能升级
            if (!info.force) {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        // 勾了「不再提示」就在本机记下截止时间：之后启动检查到更新也不再弹
                        if (snoozeChecked) {
                            UpdatePreference(context).snooze(SnoozeDays, info.version)
                        }

                        onDismiss()
                    },
                ) {
                    // 主动提醒才有「稍后」的说法；用户自己点进来的直接叫「关闭」
                    Text(if (snoozeChecked || !allowSnooze) "关闭" else "稍后")
                }
            }
        },
    )
}

/**
 * 安装包大小的展示后缀。
 *
 * @param bytes 字节数；0 表示后端没给
 * @returns 形如「 · 20.8 MB」；未知时返回空串
 */
private fun sizeSuffix(bytes: Long): String = if (bytes > 0) " · ${formatSize(bytes)}" else ""

/**
 * 下载进度文案。
 *
 * @param totalBytes 安装包总大小
 * @param progress 进度（0-1）
 * @returns 形如「8.7 / 20.8 MB」；总大小未知时返回空串
 */
private fun downloadAmountText(totalBytes: Long, progress: Float): String {
    if (totalBytes <= 0) {
        return ""
    }

    return "${formatSize((totalBytes * progress).toLong())} / ${formatSize(totalBytes)}"
}

/**
 * 字节 → 可读大小。
 *
 * @param bytes 字节数
 * @returns 形如 20.8 MB 的文本
 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}

/**
 * 格式化发布时间。
 *
 * @param raw 后端返回的时间字符串（形如 2026-09-15T00:16:16.793125）
 * @returns 形如 2026-09-15 00:16；解析失败或时间明显不合理（0001-01-01 这种默认值）返回 null
 */
private fun formatPublishedAt(raw: String): String? {
    if (raw.isBlank()) {
        return null
    }

    return runCatching {
        val time = LocalDateTime.parse(raw)

        if (time.year < 2000) {
            null
        } else {
            time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
    }.getOrNull()
}
