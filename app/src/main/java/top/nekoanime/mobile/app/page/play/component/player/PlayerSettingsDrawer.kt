package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 播放器设置抽屉：主菜单调字幕字号，二级菜单单选播放倍速。
 *
 * 这里只显示外部状态并上报操作；菜单层级、实际播放速度和偏好持久化由播放器统一管理。
 * [subtitleSizeSp] 由调用方提供，默认偏好为 16sp，可调范围为 12..32sp，每次调整 1sp。
 * 面板宽度、底色、标题竖条和选项高亮与 [SubtitleDrawer] 共用，横竖屏都由外部传入宽度。
 */
@Composable
internal fun PlayerSettingsDrawer(
    speed: Float,
    subtitleSizeSp: Int,
    showSpeedOptions: Boolean,
    onOpenSpeed: () -> Unit,
    onBack: () -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    width: Dp = PlayerDrawerWidth,
    modifier: Modifier = Modifier,
) {
    // 两层菜单各自保留滚动位置，竖屏播放区域较矮时仍能访问完整选项。
    val settingsScrollState = rememberScrollState()
    val speedScrollState = rememberScrollState()
    val headerInteraction = remember { MutableInteractionSource() }
    val speedInteraction = remember { MutableInteractionSource() }
    // 防止旧偏好或恢复状态越界；按钮始终只上报允许范围内的字号。
    val displayedSizeSp = subtitleSizeSp.coerceIn(12, 32)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(PlayerDrawerPanelColor)
            // 和字幕抽屉一样消费面板空白处的点击，避免关闭抽屉或触发底下的播放控件；
            // 子节点仍能优先处理按钮点击和竖向滚动。
            .pointerInput(Unit) {
                detectTapGestures { /* 仅消费面板点击，不改变外部状态。 */ }
            }
            .padding(16.dp),
    ) {
        // 二级标题整行可点返回，保留与字幕标题相同的主色竖条和字号。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showSpeedOptions) {
                        Modifier
                            .semantics { contentDescription = "返回设置，倍速调整" }
                            .clickable(
                                interactionSource = headerInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onBack,
                            )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlayerDrawerActiveColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (showSpeedOptions) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = if (showSpeedOptions) "倍速调整" else "设置",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showSpeedOptions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(speedScrollState)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Float.toString 保留 1.0 / 2.0，和菜单约定的六档显示完全一致。
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { option ->
                    Box(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            role = Role.RadioButton
                            selected = speed == option
                        },
                    ) {
                        PlayerDrawerOptionButton(
                            label = "${option}x",
                            active = speed == option,
                            onClick = { onSpeedSelect(option) },
                            centered = true,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(settingsScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .semantics(mergeDescendants = true) {
                            contentDescription = "倍速调整，当前 ${speed}x，打开倍速选项"
                        }
                        .clickable(
                            interactionSource = speedInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onOpenSpeed,
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "倍速调整", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${speed}x ›",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }

                // 字号名称和数值选择器分行排列，窄面板也能保留完整文字与 48dp 触摸目标。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "字幕大小", color = Color.White, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubtitleSizeStepButton(
                            increase = false,
                            enabled = displayedSizeSp > 12,
                            onClick = { onSubtitleSizeChange((displayedSizeSp - 1).coerceAtLeast(12)) },
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${displayedSizeSp}sp",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.semantics {
                                    contentDescription = "当前字幕大小 $displayedSizeSp"
                                },
                            )
                        }
                        SubtitleSizeStepButton(
                            increase = true,
                            enabled = displayedSizeSp < 32,
                            onClick = { onSubtitleSizeChange((displayedSizeSp + 1).coerceAtMost(32)) },
                        )
                    }
                }
            }
        }
    }
}

/** 字号步进按钮：边界保持可见但不可点击，读屏可区分增加与减小及其单位。 */
@Composable
private fun SubtitleSizeStepButton(
    increase: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .semantics {
                contentDescription = if (increase) "增大字幕字号 1sp，最大 32sp" else "减小字幕字号 1sp，最小 12sp"
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (increase) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 0.88f else 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}
