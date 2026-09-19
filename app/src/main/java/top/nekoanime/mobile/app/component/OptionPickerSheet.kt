package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import top.nekoanime.mobile.app.config.AnimeFilterOption

/** 滚轮里一行的高度（统一高度：缩放只影响视觉，不影响布局，行距才匀） */
private val PickerRowHeight = 52.dp

/** 滚轮一次显示几行（被选中的那一项永远在正中） */
private const val PickerVisibleRows = 5

/** 滚轮可视区高度 = 行高 × 显示行数 */
private val PickerViewportHeight = PickerRowHeight * PickerVisibleRows

/**
 * 每一项相对中心的放大档位。
 *
 * @description 距离中心 0 / 1 / 2 行分别是 1 / 0.83 / 0.66，也就是你说的 3 / 2 / 1 三档；
 * 用连续函数算，滚动时是平滑过渡而不是整档跳。
 *
 * @param distance 距离中心多少行（0 表示正中）
 * @returns 缩放比例
 */
private fun scaleForDistance(distance: Float): Float = (1f - 0.17f * distance).coerceIn(0.66f, 1f)

/**
 * 底部弹出的选项选择面板（iOS 滚轮）。
 *
 * @description 与真正的 iOS 滚轮一致：**中间那条高亮带是固定的（这层 UI 不动），
 * 动的只有选项列表**；滚到中间的那一项就是当前项（最大、主色，也就是 3 / 2 / 1 里最大的那档），
 * 松手后会吸附到最近的一行，保证中间永远正好压着一项。
 *
 * 关闭方式只有两种：**点某一项**（选中它并关闭）、**点面板外的阴影**（直接关闭）。
 * 下拉关闭被显式禁掉——滚轮本来就要上下滑，不拦着会很容易误触把整个面板滑走。
 *
 * @param title 面板标题（一般是筛选项名字）
 * @param options 可选项
 * @param value 当前选中的值（打开时滚到它，并让它在正中）
 * @param onSelect 选中回调（参数是传给后端的值）
 * @param onDismiss 关闭面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionPickerSheet(
    title: String,
    options: List<AnimeFilterOption>,
    value: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { PickerRowHeight.toPx() }
    val selectedIndex = options.indexOfFirst { it.value == value }

    // 值变化之外（比如系统隐藏）都不接受，等价于"不能下滑关闭"
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // iOS 的选择面板没有那条拖拽条
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
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // 打开时把当前项滚到正中：可视区 5 行，第 3 行是正中，所以上边留 2 行的高度
            LaunchedEffect(selectedIndex, options) {
                if (selectedIndex >= 0) {
                    val centerOffset = ((PickerVisibleRows - 1) / 2) * rowHeightPx

                    listState.scrollToItem(selectedIndex, -centerOffset.toInt())
                }
            }

            // 松手后吸附到最近的一行：不做这一步会停在两行之间，中间就不是"一整项"了
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }
                    .filter { inProgress -> !inProgress }
                    .collect {
                        val layout = listState.layoutInfo
                        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val nearest = layout.visibleItemsInfo.minByOrNull { item ->
                            abs(item.offset + item.size / 2f - viewportCenter)
                        }

                        if (nearest != null) {
                            val delta = nearest.offset + nearest.size / 2f - viewportCenter

                            if (abs(delta) > 1f) {
                                listState.animateScrollBy(delta)
                            }
                        }
                    }
            }

            // 固定高亮带（不随列表动）+ 可滚动列表，叠在一起就是滚轮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PickerViewportHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PickerRowHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(options) { index, option ->
                        val layout = listState.layoutInfo
                        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val itemInfo = layout.visibleItemsInfo.firstOrNull { it.index == index }

                        // 离中心多少行：决定缩放与淡出；正中那一项（distance≈0）就是当前项
                        val distance = itemInfo?.let {
                            abs(it.offset + it.size / 2f - viewportCenter) / rowHeightPx
                        } ?: PickerVisibleRows.toFloat()
                        val scale = scaleForDistance(distance)
                        val centered = distance < 0.5f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PickerRowHeight)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onSelect(option.value)
                                        onDismiss()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label,
                                color = if (centered) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 16.sp,
                                fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    // 越靠边越淡，把注意力收在中间
                                    alpha = 0.45f + 0.55f * scale
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
