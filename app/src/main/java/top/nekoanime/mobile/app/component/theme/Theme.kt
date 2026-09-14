package top.nekoanime.mobile.app.component.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 弹框圆角：全站统一 8dp。
 *
 * @description Material 3 的 AlertDialog 默认是 28dp 的大圆角，跟站点里「卡片/输入框统一小圆角」
 * 的风格不搭，所以所有弹框都显式套这个形状。
 */
val NekoDialogShape = RoundedCornerShape(8.dp)

/** 站点主色（与前台一致：白 + 淡灰 + 粉） */
private val NekoPink = Color(0xFFE91E63)
private val NekoPinkLight = Color(0xFFFFB1C8)

private val LightColors = lightColorScheme(
    primary = NekoPink,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFFCE4EC),
    onSecondaryContainer = Color(0xFF3E001D),
    background = Color(0xFFF4F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEEF1),
)

private val DarkColors = darkColorScheme(
    primary = NekoPinkLight,
    onPrimary = Color(0xFF66002C),
    secondaryContainer = Color(0xFF4B2531),
    onSecondaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF121316),
    surface = Color(0xFF1B1C20),
    surfaceVariant = Color(0xFF26272C),
)

/**
 * 测试工程主题：沿用前台那份配色（白 / 淡灰 / 粉），深浅色跟随系统。
 *
 * @param content 内容
 */
@Composable
fun NekoPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
