package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.config.AnimeFilterOption

/** 选择器高度（跟 Flutter 版 selector 一个量级） */
private val SelectorHeight = 40.dp

/** 选择面板里每一行的高度（iOS 行高差不多 44~48） */
private val SelectorOptionHeight = 48.dp

/**
 * 选择面板里选项区域的最大高度。
 *
 * @description 要给上面的标题和下面的「取消」留出位置：之前给 360dp，加上标题与取消后整体
 * 超出了屏幕高度，取消按钮直接被挤到屏幕外（点不到）。选项多时这块自己滚。
 */
private val SelectorSheetMaxHeight = 260.dp

/**
 * 筛选项选择器。
 *
 * @description 触发按钮是个胶囊，显示「标签 值」+ 下拉箭头；点开**从底部弹出选择面板**
 * （iOS 那种 action sheet：标题居中、选项一行一个、选中的打勾、底部单独一个「取消」）。
 * 之前用的是 Material 的下拉菜单，年份/月份这种十几个选项会拉成一长条，
 * 手机上既难点又挡住半屏内容。
 *
 * @param label 筛选项名称（年份/月份/…）
 * @param options 可选项
 * @param value 当前选中的值
 * @param onSelect 选中回调（参数是传给后端的值）
 * @param modifier 布局约束
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSelector(
    label: String,
    options: List<AnimeFilterOption>,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: options.first().label

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(SelectorHeight)
                // iOS 的筛选胶囊是圆头（胶囊形），不是小圆角方块
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { expanded = true },
                )
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label $selectedLabel",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                // iOS 的选择面板没有那条拖拽条，标题自己居中
                dragHandle = null,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 选项多（年份、月份十几个）时自己滚，面板最多占屏幕一半左右
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SelectorSheetMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.forEachIndexed { index, option ->
                            val active = option.value == value

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SelectorOptionHeight)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (active) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            expanded = false
                                            onSelect(option.value)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                )

                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 14.dp)
                                            .size(18.dp),
                                    )
                                }
                            }

                            if (index < options.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    // 取消：单独一块，跟选项分开（iOS action sheet 的做法）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SelectorOptionHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { expanded = false },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
