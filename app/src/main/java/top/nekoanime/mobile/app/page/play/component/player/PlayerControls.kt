package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 控制栏文字字号（时间、倍速）：B 站 的时间/倍速都是小字，别抢画面的视觉重心 */
private val ControlTextSize = 11.sp

/** 进度条已播放段的颜色：跟站点主色同一个粉 */
private val ProgressActiveColor = Color(0xFFE91E63)

/** 播放器控件的图标尺寸 */
private val ControlIconSize = 22.dp

/** 时间文本最少占多宽（"00:00/00:00" 这种固定格式，配等宽数字後宽度就不会再变） */
private val TimeTextMinWidth = 80.dp

/** 倍速按钮固定宽度：不管是"倍速"还是"1.25×"，占位都一样，不会把旁边的按钮顶来顶去 */
private val SpeedBoxWidth = 46.dp

/** 数字等宽：宽度不随数字变化（配合固定格式与固定宽度，控制栏就不会一直抖） */
private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** 没有任何系统栏 inset 的占位，非全屏时用它（播放器外层已经让开状态栏了） */
private val NoInsets = WindowInsets(0, 0, 0, 0)

/**
 * 播放器控制栏（布局与样式对齐 B 站移动端，三段式）。
 *
 * @description 三段：
 * - **上**：返回箭头 + 标题；全屏时它上面还有一条状态行（屏幕居中的当前时间 + 右侧电量）；
 * - **中**：右侧竖直居中的锁定键（锁定后只剩它自己，位置不变）；
 * - **下**：上一集 / 播放暂停 / 下一集（**只在全屏显示**）、当前时间、进度条、总时长、倍速、选集、全屏图标。
 *
 * 安全区：全屏时按钮按设备方向让开刘海/挖孔与系统手势区，但**背景渐变照旧铺满整条**，
 * 这样背景不会出现一条缝。非全屏时播放器外层已经让开状态栏，这里不再叠加。
 *
 * 进度条是自己画的：B 站 的进度条要同时显示「已缓冲」和「已播放」两段，
 * Material3 的 Slider 只能显示两段（已播放 / 未播放），所以底下用 Canvas 画轨道，
 * 上面盖一个透明 Slider 专门负责接收拖动（触摸区域和 Slider 一致，不会变难点）。
 *
 * @param playing 是否正在播放（决定全屏播放键的图标）
 * @param positionMs 当前播放位置
 * @param durationMs 总时长
 * @param bufferedMs 已缓冲位置（画进度条上那条浅色段）
 * @param speed 当前倍速
 * @param locked 是否锁定
 * @param fullscreen 是否全屏（决定状态行、上下集/播放键、全屏图标与安全区）
 * @param title 标题（番剧名 + 集数）
 * @param onTogglePlay 播放/暂停；为 null 表示不显示播放键（非全屏就是 null）
 * @param onSeek 进度条拖动结束（松手才真正跳）
 * @param onToggleSpeed 切换倍速
 * @param onToggleLock 锁定/解锁
 * @param onToggleFullscreen 全屏切换
 * @param onBack 返回
 * @param onHome 返回主页
 * @param modifier 由播放器给出的布局约束（一般是整块播放区域）
 * @param episodeDrawer 打开选集抽屉；为 null 表示当前不显示选集按钮（非全屏时不显示，选集列表就在播放器下面）
 * @param previousEpisode 上一集；为 null 就整块隐藏（非全屏不显示，或者已经是第一集）
 * @param nextEpisode 下一集；为 null 就整块隐藏（非全屏不显示，或者已经是最后一集）
 * @param onUserInteraction 点控制栏上的按钮（一次性操作）：控制栏自动隐藏重新计时
 * @param onInteractionStart 开始拖进度条：自动隐藏暂停计时
 * @param onInteractionEnd 拖完进度条：自动隐藏重新开始计时
 * @param onSeekPreview 拖动进度条过程中回报目标位置（播放器用它显示中央的指示框）
 * @param onSeekPreviewEnd 拖动进度条结束（把指示框收掉）
 * @param topBarOnly 只显示顶栏（返回 / 主页 / 标题），不显示底部操作栏和右侧锁定键。
 * 首次加载成功之前（加载中、以及加载失败）都用它：进度、时长、倍速、锁定都建立在"视频已就绪"之上，
 * 没成功之前摆出来全是空信息，只会让人以为可以操作；但返回键必须留着，否则用户会被困在这一页里。
 */
@Composable
fun PlayerControls(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    locked: Boolean,
    fullscreen: Boolean,
    title: String,
    onTogglePlay: (() -> Unit)?,
    onSeek: (Long) -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    // modifier 放在所有"有默认值的参数"最前面：Compose 的约定（lint 的 ModifierParameter 检查）
    modifier: Modifier = Modifier,
    episodeDrawer: (() -> Unit)? = null,
    previousEpisode: (() -> Unit)? = null,
    nextEpisode: (() -> Unit)? = null,
    onUserInteraction: () -> Unit = {},
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    onSeekPreview: (Long) -> Unit = {},
    onSeekPreviewEnd: () -> Unit = {},
    topBarOnly: Boolean = false,
) {
    // 倍速按钮：不要涟漪（播放器控件追求轻量）
    val speedInteraction = remember { MutableInteractionSource() }

    // 进度条的拖动状态。
    //
    // 为什么需要本地状态：视频在拖动过程中依然在播放，父级每 250ms 会把播放器的实时位置传下来。
    // 如果直接用它驱动进度条，手指拖到的位置会立刻被实时位置覆盖，表现就是"怎么拖都会往回跳"。
    // 所以拖动期间只认本地位置，松手才把最终位置交给父级去 seek。
    var dragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }

    // 时间与圆点都用这个位置：拖动中显示手指位置，其余时间显示播放器位置
    val displayMs = if (dragging) dragMs else positionMs
    val fraction = if (durationMs > 0) displayMs.toFloat() / durationMs else 0f
    val bufferedFraction = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f

    // 进度文本：固定格式的「当前/总时长」（不是"当前"一个、"总时长"另一个），放在进度条右边
    val progressText = formatProgressText(displayMs, durationMs)

    // 安全区：全屏时系统栏虽然隐藏了，但刘海/挖孔与手势区的 inset 还在，按方向让开。
    // 背景渐变是画在外层的，不受这个 padding 影响，所以"按钮让开、底色照旧铺满"。
    val topInsets = if (fullscreen) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        NoInsets
    }
    val bottomInsets = if (fullscreen) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    } else {
        NoInsets
    }
    val sideInsets = if (fullscreen) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    } else {
        NoInsets
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 锁定后把上下两条整体收掉，只留右侧那个解锁键（位置和锁定键完全一样）
        if (!locked) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // ===== 上：状态行（仅全屏）+ 返回 + 标题 =====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 只有一层自上而下的黑渐变：B 站 顶部栏不是实心黑条，压得太死会挡画面
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            ),
                        ),
                ) {
                    if (fullscreen) {
                        PlayerStatusBar(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(topInsets)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                onUserInteraction()
                                onBack()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(ControlIconSize),
                            )
                        }

                        // 一键回主页：只在非全屏显示——全屏是沉浸观看，顶栏只留返回 + 标题
                        if (!fullscreen) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onHome()
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = "主页",
                                    tint = Color.White,
                                    modifier = Modifier.size(ControlIconSize),
                                )
                            }
                        }

                        // 标题（番剧名 | 剧集名 | 集数）太长时自动滚动：
                        // 进来先停 10 秒，滚完再停 10 秒，放得下就完全不动
                        AutoScrollTitle(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }

                // ===== 下：上下集/播放（仅全屏）+ 时间 + 进度条 + 时长 + 倍速 + 选集 + 全屏 =====
                // 首次加载期间整条都不画（见 topBarOnly 的说明）
                if (!topBarOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(bottomInsets)
                                .padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 上一集：没有上一集时整块不显示（跟 web 一致），不是禁用而是隐藏
                            if (previousEpisode != null) {
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        previousEpisode()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "上一集",
                                        tint = Color.White,
                                        modifier = Modifier.size(ControlIconSize),
                                    )
                                }
                            }

                            // 播放/暂停：只在全屏显示（非全屏交给双击画面中间 + 中央那个大播放键）
                            if (onTogglePlay != null) {
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        onTogglePlay()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = if (playing) {
                                            Icons.Filled.Pause
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                        contentDescription = if (playing) "暂停" else "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(ControlIconSize),
                                    )
                                }
                            }

                            // 下一集：没有下一集时同样直接隐藏
                            if (nextEpisode != null) {
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        nextEpisode()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "下一集",
                                        tint = Color.White,
                                        modifier = Modifier.size(ControlIconSize),
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 自己画的轨道：未播放 / 已缓冲 / 已播放 三段 + 白色圆点
                                ProgressTrack(
                                    fraction = fraction,
                                    bufferedFraction = bufferedFraction,
                                    dragging = dragging,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp),
                                )

                                // 透明 Slider：只负责拖动与无障碍，视觉全部交给上面的 Canvas。
                                // 这样进度条的观感完全可控，触摸手感还是 Slider 那套（含拖动结束回调）。
                                Slider(
                                    value = fraction.coerceIn(0f, 1f),
                                    onValueChange = { value ->
                                        // 拖动开始：这期间自动隐藏暂停计时（拖多久都不该自己消失）
                                        if (!dragging) {
                                            onInteractionStart()
                                        }

                                        // 只更新本地预览，不在这里 seek：拖动中反复 seek 会被实时位置回调覆盖，造成回溯
                                        dragging = true
                                        dragMs = (value * durationMs).toLong().coerceIn(0L, durationMs)
                                        // 拖进度条也弹中央指示框（跟手势横滑一样，显示目标时间）
                                        onSeekPreview(dragMs)
                                    },
                                    onValueChangeFinished = {
                                        // 松手才真正跳转，并且立刻结束本地拖动状态
                                        val target = dragMs
                                        android.util.Log.i("NekoPlayer", "进度条松手 target=${target / 1000}s")
                                        dragging = false
                                        onInteractionEnd()
                                        onSeekPreviewEnd()
                                        onSeek(target)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Transparent,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent,
                                    ),
                                )
                            }

                            // 当前/总时长：固定格式（分钟补零）+ 等宽数字 + 最小宽度，
                            // 这样数字从 9 变 10 的时候宽度也不会跳，右边的按钮就不会被顶来顶去
                            Text(
                                text = progressText,
                                color = Color.White,
                                fontSize = ControlTextSize,
                                style = TabularNumbers,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .widthIn(min = TimeTextMinWidth)
                                    .padding(horizontal = 4.dp),
                            )

                            // 倍速：点一下在 1.0 / 1.25 / 1.5 / 2.0 之间循环。
                            // B 站 这里是纯文字（没有胶囊底色），1.0 时显示"倍速"。
                            // 固定宽度：不管文案是"倍速"还是"1.25×"，占位都一样。
                            Box(
                                modifier = Modifier
                                    .width(SpeedBoxWidth)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(
                                        interactionSource = speedInteraction,
                                        indication = null,
                                        onClick = {
                                            onUserInteraction()
                                            onToggleSpeed()
                                        },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (speed == 1f) "倍速" else "${speed}×",
                                    color = Color.White,
                                    fontSize = ControlTextSize,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // 选集：只在全屏出现，点开是从右侧滑出的抽屉
                            if (episodeDrawer != null) {
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        episodeDrawer()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = "选集",
                                        tint = Color.White,
                                        modifier = Modifier.size(ControlIconSize),
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onToggleFullscreen()
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (fullscreen) {
                                        Icons.Filled.FullscreenExit
                                    } else {
                                        Icons.Filled.Fullscreen
                                    },
                                    contentDescription = if (fullscreen) "退出全屏" else "全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(ControlIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===== 中：锁定键（右侧竖直居中，锁定/解锁都在这个位置）=====
        // 首次加载期间不显示：还没画面可锁，露一个锁头只是噪声
        if (!topBarOnly) {
            IconButton(
                onClick = {
                    onUserInteraction()
                    onToggleLock()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(sideInsets)
                    .padding(end = 8.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = if (locked) "解锁" else "锁定",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 进度文本：`当前时间/总时长`。
 *
 * @description 两个时间用同一套格式：总时长不足 1 小时就是 `mm:ss`（分钟补零），
 * 超过 1 小时才是 `h:mm:ss`。格式一旦确定，同一集里就不再变化，
 * 再配合等宽数字与最小宽度，数字从 9 变 10 时整条控制栏也不会来回抖。
 *
 * @param positionMs 当前位置
 * @param durationMs 总时长
 * @returns 形如 `00:23/23:49` 的文本
 */
private fun formatProgressText(positionMs: Long, durationMs: Long): String {
    val withHours = durationMs >= 3_600_000L

    return "${formatClock(positionMs, withHours)}/${formatClock(durationMs, withHours)}"
}

/**
 * 固定宽度的时长文本。
 *
 * @param milliseconds 毫秒
 * @param withHours 是否显示小时位
 * @returns `mm:ss` 或 `h:mm:ss`
 */
private fun formatClock(milliseconds: Long, withHours: Boolean): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (withHours) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * B 站样式进度条轨道。
 *
 * @description 三段：未播放（白 28%）、已缓冲（白 45%）、已播放（主色）；
 * 末端一个白色圆点，拖动时轨道变粗、圆点变大（B 站 就是靠这个反馈"正在拖"）。
 *
 * 左右各留出圆点半径的间距，圆点贴到两端时不会画到画面外面；
 * 这个间距取的是 Material3 Slider 的默认圆点半径（10dp），
 * 所以"看到的圆点位置"和"手指拖动映射"是对齐的。
 *
 * @param fraction 已播放比例 0f~1f
 * @param bufferedFraction 已缓冲比例 0f~1f
 * @param dragging 是否正在拖动
 * @param modifier 尺寸约束
 */
@Composable
private fun ProgressTrack(
    fraction: Float,
    bufferedFraction: Float,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val inset = 10.dp.toPx()
        val startX = inset
        val endX = (size.width - inset).coerceAtLeast(startX)
        val trackWidth = endX - startX

        val strokeWidth = (if (dragging) 4.dp else 3.dp).toPx()
        val thumbRadius = (if (dragging) 7.dp else 5.dp).toPx()

        fun xAt(value: Float): Float = startX + trackWidth * value.coerceIn(0f, 1f)

        // 未播放
        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // 已缓冲：比未播放亮一点，B 站 用它表示"这段已经下载好了"
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(startX, centerY),
            end = Offset(xAt(bufferedFraction), centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // 已播放
        drawLine(
            color = ProgressActiveColor,
            start = Offset(startX, centerY),
            end = Offset(xAt(fraction), centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // 圆点
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(xAt(fraction), centerY),
        )
    }
}
