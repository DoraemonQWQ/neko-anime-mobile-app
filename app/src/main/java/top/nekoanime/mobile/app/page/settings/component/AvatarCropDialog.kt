package top.nekoanime.mobile.app.page.settings.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.nekoanime.mobile.app.component.theme.NekoDialogShape
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 解码原图时的最大边长。
 *
 * @description 手机相册里动辄 4000×3000 的照片，直接全尺寸解码很容易一次性吃掉几十 MB 内存；
 * 头像最终也只会存 1000px 上下，所以按这个上限降采样，兼顾内存与清晰度。
 */
private const val MaxSourceSize = 2048

/**
 * 导出成品的最大边长。
 *
 * @description 图片被放得很小时裁剪框覆盖的原图像素会很大，这里封顶避免画超大画布。
 */
private const val MaxOutputSize = 1024

/** 缩放范围：1 = 刚好铺满圆形裁剪框 */
private const val MinZoom = 1f
private const val MaxZoom = 3f

/**
 * 头像裁剪弹窗（圆形裁剪 + 缩放 + 拖动）。
 *
 * @description 跟 web 端的 {@code AvatarCropDialog} 行为对齐：
 * - 中间是**圆形预览框**，初始状态图片居中铺满（也就是默认取图片中央）；
 * - 双指捏合缩放、单指拖动平移，图片边缘不会被拖进圆里（始终铺满，不露空白）；
 * - 点确定后按**圆形**裁剪：圆外像素置为透明，导出 PNG（显示时各处本来就按圆形展示，
 *   透明角在方形场景下也不会出现白块）。
 *
 * @param uri 相册选中的原图地址
 * @param onCancel 取消
 * @param onConfirm 确认，回传裁剪好的 PNG 字节
 * @param busy 是否正在上传（上传期间禁用按钮）
 */
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
    busy: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var frameSize by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(MinZoom) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var exporting by remember { mutableStateOf(false) }

    // 读图放在 IO 线程：大图解码会卡住主线程，界面会僵一下
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodeSourceBitmap(context, uri) }
    }

    /**
     * 把平移量夹在合法范围内。
     *
     * @param x 目标横向偏移（预览像素）
     * @param y 目标纵向偏移（预览像素）
     * @returns 夹紧后的偏移
     */
    fun clamp(x: Float, y: Float): Pair<Float, Float> {
        // 预览里图片是按 ContentScale.Crop 铺满裁剪框的，所以缩放后的显示尺寸 = 框边长 × zoom
        val maxOffset = frameSize * (zoom - MinZoom) / 2f

        return x.coerceIn(-maxOffset, maxOffset) to y.coerceIn(-maxOffset, maxOffset)
    }

    Dialog(
        onDismissRequest = { if (!busy && !exporting) onCancel() },
        // 用系统默认宽度会太窄，裁剪框放不下
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            // 圆角统一 8dp
            shape = NekoDialogShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "裁剪头像",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "双指缩放、拖动调整位置，圆内就是头像成品（圆外会被裁掉）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val source = bitmap

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        // 量出裁剪框的真实像素边长：缩放与导出的换算全依赖它
                        .onSizeChanged { frameSize = it.width }
                        .pointerInput(source?.let { it.width to it.height }) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val nextZoom = (zoom * gestureZoom).coerceIn(MinZoom, MaxZoom)
                                val zoomRatio = if (zoom <= 0f) 1f else nextZoom / zoom

                                // 缩放时按同一个比例放大已有偏移，视觉上才是"以圆心为原点"缩放
                                val nextX = offsetX * zoomRatio + pan.x
                                val nextY = offsetY * zoomRatio + pan.y

                                zoom = nextZoom
                                val (clampedX, clampedY) = clamp(nextX, nextY)
                                offsetX = clampedX
                                offsetY = clampedY
                            }
                        },
                ) {
                    if (source != null) {
                        Image(
                            bitmap = source.asImageBitmap(),
                            contentDescription = "待裁剪的头像",
                            // Crop：初始状态正好铺满裁剪框（等价于 web 的 baseScale）
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                        )
                    }
                }

                if (source == null || exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        enabled = !busy && !exporting,
                        onClick = onCancel,
                    ) {
                        Text("取消")
                    }

                    TextButton(
                        enabled = !busy && !exporting && source != null && frameSize > 0,
                        onClick = {
                            val current = bitmap ?: return@TextButton

                            scope.launch {
                                exporting = true

                                val bytes = withContext(Dispatchers.IO) {
                                    cropCirclePng(
                                        source = current,
                                        frameSize = frameSize.toFloat(),
                                        zoom = zoom,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                    )
                                }

                                exporting = false

                                if (bytes != null) {
                                    onConfirm(bytes)
                                }
                            }
                        },
                    ) {
                        Text(if (busy) "上传中…" else "确定")
                    }
                }
            }
        }
    }
}

/**
 * 读取相册图片并按上限降采样。
 *
 * @param context 上下文
 * @param uri 相册返回的图片地址
 * @returns 解码后的位图；读取失败返回 null
 */
private fun decodeSourceBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    // 采样率只能是 2 的幂：一直翻倍直到最长边落到上限以内
    var sampleSize = 1

    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MaxSourceSize) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }

    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}.getOrNull()

/**
 * 按圆形裁剪并导出 PNG。
 *
 * @description 预览与导出共用同一套「缩放 + 平移」参数，区别只在画布尺寸：
 * 导出按**原图分辨率**取裁剪框覆盖的那一块（图被缩得越小，导出的那块越大），
 * 再封顶到 [MaxOutputSize]。圆外用 DST_IN 遮罩置成透明，所以是带透明角的圆形 PNG。
 *
 * @param source 原图（已降采样）
 * @param frameSize 预览裁剪框的像素边长
 * @param zoom 用户缩放倍数（1 = 铺满裁剪框）
 * @param offsetX 横向平移量（预览像素）
 * @param offsetY 纵向平移量（预览像素）
 * @returns PNG 字节；尺寸不合法时返回 null
 */
private fun cropCirclePng(
    source: Bitmap,
    frameSize: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): ByteArray? {
    if (frameSize <= 0f || source.width <= 0 || source.height <= 0) {
        return null
    }

    val width = source.width.toFloat()
    val height = source.height.toFloat()

    // 预览里 ContentScale.Crop 的基础缩放：宽高两个方向里更"放大"的那个
    val baseScale = max(frameSize / width, frameSize / height)
    val scale = baseScale * zoom.coerceAtLeast(MinZoom)

    // 裁剪框覆盖的原图像素边长
    val side = frameSize / scale

    // 裁剪中心在原图坐标系里的位置：图片中心相对裁剪框中心偏移了 offset
    val centerX = width / 2f - offsetX / scale
    val centerY = height / 2f - offsetY / scale

    val outputSize = side.roundToInt().coerceIn(1, MaxOutputSize)
    val ratio = outputSize / side
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val matrix = Matrix().apply {
        setScale(ratio, ratio)
        postTranslate(outputSize / 2f - centerX * ratio, outputSize / 2f - centerY * ratio)
    }

    canvas.drawBitmap(source, matrix, paint)

    // 圆形遮罩：先画一张「圆内白色、圆外透明」的蒙版位图，再用 DST_IN 盖到成品上——
    // 只有蒙版不透明的地方才保留，圆外就变成透明。
    // 注意：直接给 Canvas 画圆设置 xfermode 在部分设备（实测小米）上不生效，四角仍是不透明的，
    // 用「蒙版位图 + drawBitmap」这种写法在所有 API 上都稳。
    val mask = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(mask)
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    circlePaint.color = Color.WHITE
    maskCanvas.drawCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, circlePaint)

    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    canvas.drawBitmap(mask, 0f, 0f, maskPaint)
    mask.recycle()

    val bytes = ByteArrayOutputStream().use { output_stream ->
        output.compress(Bitmap.CompressFormat.PNG, 100, output_stream)
        output_stream.toByteArray()
    }

    output.recycle()

    return bytes
}
