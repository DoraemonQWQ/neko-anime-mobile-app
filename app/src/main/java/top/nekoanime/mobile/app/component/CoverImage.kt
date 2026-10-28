package top.nekoanime.mobile.app.component

import top.nekoanime.mobile.app.logging.AppLog as Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * 网络封面。
 *
 * @description 就是把 Coil 包一层，顺便把加载结果打到日志里（tag：NekoCover）。
 * 封面不显示这类问题光看界面只能猜：可能是地址错、可能是网络、也可能是解码失败，
 * 有这行日志就能一眼看出是哪一种。正式版可以保留（只在成功/失败时各打一条，不刷屏）。
 *
 * @param url 完整图片地址
 * @param contentDescription 无障碍描述
 * @param modifier 尺寸约束（宽高、圆角、占位底色都由调用方给）
 */
@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            // 淡入：图片下来的瞬间不会"啪"地跳出来
            .crossfade(true)
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = request,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success ->
                    Log.d("NekoCover", "封面加载成功")

                is AsyncImagePainter.State.Error ->
                    Log.e(
                        "NekoCover",
                        "封面加载失败",
                        state.result.throwable,
                    )

                else -> Unit
            }
        },
    )

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
