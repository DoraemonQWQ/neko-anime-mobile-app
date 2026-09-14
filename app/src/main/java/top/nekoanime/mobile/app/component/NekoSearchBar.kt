package top.nekoanime.mobile.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/** 搜索框高度（跟 Flutter 版 NekoSearchBar 一致：40dp） */
private val SearchBarHeight = 40.dp

/** 搜索框圆角：Flutter 那边是 radius 100（胶囊），这里用足够大的圆角等价 */
private val SearchBarShape = RoundedCornerShape(100.dp)

/**
 * 搜索框。
 *
 * @description 参数与外观对齐 Flutter 版 `neko_search_bar`：高 40dp、胶囊形、
 * 底色 surfaceContainerHighest、1dp outline 描边、左侧放大镜、占位文字 14sp。
 *
 * 两种用法：
 * - **按钮模式**（isButton = true）：不可输入，点一下触发 onPressed —— 首页顶栏那个"假搜索框"用它跳搜索页；
 * - **输入模式**（isButton = false）：真输入框 —— 搜索页用它。
 *
 * @param placeholder 占位文字
 * @param modifier 布局约束
 * @param value 输入模式的当前文本
 * @param onValueChange 输入模式的文本变化
 * @param onSubmit 输入模式回车/搜索
 * @param onPressed 按钮模式的点击
 * @param autofocus 输入模式是否自动聚焦（这里只作为语义标记，聚焦由调用方用 FocusRequester 控制）
 * @param enabled 是否可交互
 */
@Composable
fun NekoSearchBar(
    placeholder: String,
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onPressed: (() -> Unit)? = null,
    autofocus: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val isButton = onPressed != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarHeight)
            .clip(SearchBarShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, SearchBarShape)
            .then(
                if (isButton && enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onPressed!!,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "搜索",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        if (isButton) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    // 输入文字与光标的颜色必须显式给：BasicTextField 的默认文字是纯黑、默认光标也是黑色，
                    // 暗色模式下等于黑字黑底，看着就像"没输入进去"。
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
