package top.nekoanime.mobile.app.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import top.nekoanime.mobile.app.config.AnimeFilterOption

/** 滚轮里一行的高度（统一高度：缩放只影响视觉，不影响布局，行距才匀） */
private val PickerRowHeight = 52.dp

/** 滚轮一次显示几行（当前项永远在正中） */
private const val PickerVisibleRows = 5

/** 滚轮可视区高度 = 行高 × 显示行数 */
private val PickerViewportHeight = PickerRowHeight * PickerVisibleRows

/** 面板高度：标题 + 滚轮 + 上下留白 */
private val PickerPanelHeight = PickerViewportHeight + 96.dp

/**
 * 每一项相对中心的放大档位。
 *
 * @description 距离中心 0 / 1 / 2 行分别是 1 / 0.83 / 0.66，也就是 3 / 2 / 1 三档；
 * 用连续函数算，滚动时平滑过渡而不是整档跳。
 *
 * @param distance 距离中心多少行（0 表示正中）
 * @returns 缩放比例
 */
private fun scaleForDistance(distance: Float): Float = (1f - 0.17f * distance).coerceIn(0.66f, 1f)

/**
 * 底部弹出的选项选择面板（iOS 滚轮）。
 *
 * @description 这里没有用 Material 的 ModalBottomSheet，而是自己画一层浮层：
 * 那个组件自带"下拉关闭"，而它关闭用的是和"点阴影关闭"同一个内部状态，
 * 我们之前想只禁掉下拉、结果把点阴影关闭也一起堵死了（点哪儿都关不掉）。
 * 自己画就完全可控：
 * - **中间高亮带固定不动**，动的只有列表，滚到中间的那一项就是当前项（最大、主色）；
 * - 松手吸附到最近一行，中间永远正好压着一整项；
 * - 关闭方式：点某一项（选中并关闭）、点面板外的阴影、系统返回键；
 * - 面板本身**不可拖拽**，只有滚轮能上下滑，不会误触把面板拖走。
 *
 * @param title 面板标题（一般是筛选项名字）
 * @param options 可选项
 * @param value 当前选中的值（打开时滚到它，并让它在正中）
 * @param onSelect 选中回调（参数是传给后端的值）
 * @param onDismiss 关闭面板
 */
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
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 入场动画：先渲染在屏幕外，下一帧再滑进来
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shown = true
    }

    // 打开时把当前项滚到正中：可视区 5 行，第 3 行是正中，所以上边留 2 行的高度
    LaunchedEffect(selectedIndex, options) {
        if (selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex, -(((PickerVisibleRows - 1) / 2) * rowHeightPx).toInt())
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

    Popup(
        // focusable：能拿到返回键（Popup 的 onDismissRequest 就是返回键），也能独占触摸
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 阴影：点它就关（只有这一层接点击，点别处不会穿透到底下的列表）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PickerPanelHeight + bottomInset)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 16.dp)
                        .padding(bottom = bottomInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )

                    // 固定高亮带（不随列表动）+ 可滚动列表，叠在一起就是滚轮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(PickerViewportHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
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
    }
}
