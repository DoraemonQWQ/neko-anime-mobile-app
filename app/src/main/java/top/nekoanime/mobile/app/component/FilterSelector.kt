package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekoanime.mobile.app.config.AnimeFilterOption

/** 触发胶囊高度（跟 Flutter 版 selector 一个量级） */
private val SelectorHeight = 40.dp

/**
 * 筛选项选择器（触发胶囊）。
 *
 * @description 只是一个胶囊：显示「标签 当前值」+ 下拉箭头，点开调用 {@link OptionPickerSheet}
 * 弹出底部滚轮选择面板。选择面板本身抽成了独立组件，别处要用同样的选择交互可以直接复用。
 *
 * @param label 筛选项名称（年份/月份/…）
 * @param options 可选项
 * @param value 当前选中的值
 * @param onSelect 选中回调（参数是传给后端的值）
 * @param modifier 布局约束
 */
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
            OptionPickerSheet(
                title = label,
                options = options,
                value = value,
                onSelect = onSelect,
                onDismiss = { expanded = false },
            )
        }
    }
}
