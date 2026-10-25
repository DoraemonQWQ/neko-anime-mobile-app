package top.nekoanime.mobile.app.service

import android.content.Context
import android.content.Intent
import top.nekoanime.mobile.app.logging.AppLog as Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.nekoanime.mobile.app.BuildConfig
import top.nekoanime.mobile.app.config.ConfigLoader
import top.nekoanime.mobile.app.data.api.ApiClient
import top.nekoanime.mobile.app.data.api.ApiRoutes
import top.nekoanime.mobile.app.model.dto.AppUpdateDto
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 检查更新 / 下载更新。
 *
 * @description 流程（跟后端的 AppRelease 接口配合）：
 * 1. **检查**：调后端 `GET /api/app/update?platform=android&version=1.0.0`，
 *    版本比较交给后端（`app_release` 表里的最新记录），客户端只认 `has_update`；
 * 2. **下载**：`GET /api/app/download/{platform}/{version}`，服务端支持 Range，
 *    所以这里做了**断点续传**——中途断网重试时从已下好的字节继续，而不是整包重下；
 * 3. **校验**：下完比对 MD5（后端给的），不一致就丢掉重来；
 * 4. **安装**：通过 FileProvider 交给系统安装界面（Android 不允许 App 静默安装）。
 *
 * 安装包落在**应用自己的外部私有目录**（`Android/data/<包名>/files/update/`），
 * 不需要存储权限，也不会出现在相册里。
 */
object UpdateService {
    /** 安装包放在外部私有目录的哪个子目录 */
    private const val UpdateDirectoryName = "update"

    /**
     * 下载用的 HTTP 客户端。
     *
     * @description 读超时给得比较长：下载 20MB+ 的安装包时，两端之间可能长时间没有新数据。
     */
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 检查更新。
     *
     * @returns 有新版本时返回版本信息；已是最新（或配置里关掉了检查）返回 null
     */
    suspend fun check(): Result<AppUpdateDto?> {
        val config = ConfigLoader.config.update

        if (!config.enabled) {
            Log.i("NekoUpdate", "配置里关闭了检查更新，跳过")
            return Result.success(null)
        }

        val platform = config.platform.ifBlank { "android" }

        return ApiClient.get(
            path = ApiRoutes.APP_UPDATE,
            query = mapOf(
                "platform" to platform,
                // dev / test 包的版本号带 -dev / -test 后缀，服务端按数字段比较时会把
                // 「1.2.16-dev」解析成 1.2.0，导致这些包每次都被判定为"有新版本"。
                // 只把数字段报上去，后缀是本机打包方式，跟服务端版本比大小无关。
                "version" to BuildConfig.VERSION_NAME.substringBefore('-'),
                "channel" to config.channel,
            ),
            serializer = AppUpdateDto.serializer(),
        )
            .map { info -> if (info.hasUpdate) info else null }
            .onSuccess { info ->
                Log.i(
                    "NekoUpdate",
                    if (info == null) {
                        "已是最新版本（本机 ${BuildConfig.VERSION_NAME}）"
                    } else {
                        "发现新版本 ${info.version}（本机 ${BuildConfig.VERSION_NAME}），大小 ${info.fileSize} 字节"
                    },
                )
            }
    }

    /**
     * 下载安装包（支持断点续传）。
     *
     * @param context 上下文
     * @param info 检查更新返回的版本信息（下载地址、大小、MD5 都在里面）
     * @param onProgress 进度回调（0f~1f；拿不到总长度时不回调）
     * @returns 下载好的安装包文件
     */
    suspend fun download(
        context: Context,
        info: AppUpdateDto,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = updateDirectory(context)
            val target = File(directory, buildApkName(info.version))
            val partial = File(directory, "${buildApkName(info.version)}.part")

            // 已经下好过同一个包：大小与 MD5 都对得上就直接复用，避免用户重复等一遍
            if (target.exists() && isComplete(target, info)) {
                Log.i("NekoUpdate", "版本 ${info.version} 安装包已校验通过，直接复用")
                return@runCatching target
            }

            // 旧版本残留先清掉，避免 update 目录越堆越大（.part 不动，它是断点续传的进度）
            directory.listFiles()
                ?.filter { it.isFile && it.name.startsWith("nekoanime-") && !it.name.endsWith(".part") && it != target }
                ?.forEach { it.delete() }

            val url = resolveUrl(info.downloadUrl)
            var startAt = if (partial.exists()) partial.length() else 0L

            /*
             * 残留分片不能无条件续传。
             *
             * 分片只跟"文件名里的版本号"绑定，服务端万一换了包（早先所有版本共用一个下载地址时就
             * 出过这事），从旧分片往后接着下，拼出来的就是一个前半截旧、后半截新的四不像，
             * 大小可能还对，MD5 必然对不上。分片不小于目标包时直接丢弃重下。
             */
            if (info.fileSize > 0 && startAt >= info.fileSize) {
                Log.w(
                    "NekoUpdate",
                    "本地残留分片 $startAt 字节，目标包只有 ${info.fileSize} 字节，丢弃后重新下载",
                )
                partial.delete()
                startAt = 0L
            }

            val request = Request.Builder()
                .url(url)
                .apply { if (startAt > 0) header("Range", "bytes=$startAt-") }
                .get()
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("下载失败：HTTP ${response.code}")
                }

                // 服务端没接受 Range（返回 200 而不是 206）说明要从头下，已下的部分作废
                if (startAt > 0 && response.code != 206) {
                    Log.w("NekoUpdate", "服务端未接受断点续传，从头开始下载")
                    partial.delete()
                    startAt = 0L
                }

                val body = response.body ?: error("下载失败：响应为空")
                val remaining = body.contentLength()
                val total = if (remaining > 0) startAt + remaining else info.fileSize

                Log.i(
                    "NekoUpdate",
                    "GET ${url.toHttpUrlOrNull()?.encodedPath.orEmpty()} 开始下载（HTTP ${response.code}，续传起点 $startAt，总大小 ${if (total > 0) total else -1}）",
                )

                body.byteStream().use { input ->
                    // 断点续传时要追加写入：FileOutputStream 的 append 参数决定
                    FileOutputStream(partial, startAt > 0).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = startAt

                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) {
                                break
                            }

                            output.write(buffer, 0, count)
                            written += count

                            if (total > 0) {
                                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }

                        output.flush()
                    }
                }
            }

            if (!partial.exists() || partial.length() <= 0) {
                error("下载失败：文件为空")
            }

            /*
             * 大小先对上再算 MD5：CDN 半路截断、续传起点错位这些情况看大小最直观，
             * 报"下载不完整"比报"MD5 不一致"更容易定位（后者会被当成服务端文件坏了）。
             */
            if (info.fileSize > 0 && partial.length() != info.fileSize) {
                val actualSize = partial.length()

                partial.delete()
                error("安装包不完整（已下载 $actualSize / ${info.fileSize} 字节），请重新下载")
            }

            if (info.fileMd5.isNotBlank()) {
                val actual = md5Hex(partial)

                if (!actual.equals(info.fileMd5, ignoreCase = true)) {
                    partial.delete()
                    error("安装包校验失败（MD5 不一致），请重新下载")
                }
            }

            // 校验通过才改名成正式包名，中途失败只会留下 .part
            if (target.exists()) {
                target.delete()
            }

            if (!partial.renameTo(target)) {
                error("安装包保存失败")
            }

            Log.i("NekoUpdate", "版本 ${info.version} 安装包下载完成（${target.length()} 字节）")

            target
        }.onFailure { error -> Log.w("NekoUpdate", "下载失败", error) }
    }

    /**
     * 拉起系统安装界面。
     *
     * @param context 上下文
     * @param file 已下载的 apk
     */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }

    /**
     * 找出「已经下载好且校验通过」的安装包。
     *
     * @description 用户下载完可能没装成（取消了系统安装、或者直接杀掉 App），
     * 下次进来不该再下 20MB 一遍：这里按「文件名 = 目标版本 + 大小 / MD5 对得上」判断，
     * 命中就直接拿去安装。
     *
     * @param context 上下文
     * @param info 版本信息
     * @returns 可用的安装包；没有或校验不过返回 null
     */
    suspend fun findDownloaded(context: Context, info: AppUpdateDto): File? = withContext(Dispatchers.IO) {
        val target = File(updateDirectory(context), buildApkName(info.version))

        if (target.exists() && isComplete(target, info)) {
            Log.i("NekoUpdate", "版本 ${info.version} 已存在校验通过的安装包，无需重复下载")
            return@withContext target
        }

        null
    }

    /**
     * 清理更新目录里的残留文件（启动初始化时调用）。
     *
     * @description 两种情况会留下垃圾：
     * 1. 下载中途退出 / 断网：留下 `*.part` 半成品，隔天启动时基本已经失效；
     * 2. 版本已经升过，或服务端下架了该版本：留下不再匹配的旧安装包。
     *
     * 启动时统一按「只保留目标版本」的规则清一遍：`keepVersion` 为空表示哪个都不保留。
     *
     * @param context 上下文
     * @param keepVersion 需要保留的版本号（一般是检查更新返回的最新版本）；null 表示都不保留
     */
    suspend fun cleanup(context: Context, keepVersion: String?) = withContext(Dispatchers.IO) {
        val directory = updateDirectory(context)
        val keepName = keepVersion?.takeIf { it.isNotBlank() }?.let { buildApkName(it) }
        var removed = 0

        directory.listFiles()?.forEach { file ->
            if (!file.isFile) {
                return@forEach
            }

            // *.part 一律清理；安装包只留目标版本
            val stale = file.name.endsWith(".part") || keepName == null || file.name != keepName

            if (stale && file.delete()) {
                removed += 1
            }
        }

        if (removed > 0) {
            Log.i("NekoUpdate", "启动清理更新目录，删除残留文件 $removed 个（保留版本：${keepVersion ?: "无"}）")
        }
    }

    /**
     * 把后端给的下载地址补全成绝对地址。
     *
     * @description 后端默认返回相对地址（`/api/app/download/...`），
     * 这样换域名 / 换环境不用改后端配置；如果配了 PublicBaseUrl 返回的是绝对地址，就原样使用。
     *
     * @param url 后端返回的下载地址
     * @returns 可直接请求的绝对地址
     */
    fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        val baseUrl = ConfigLoader.config.api.baseUrl.trimEnd('/')

        return "$baseUrl/${url.trimStart('/')}"
    }

    /**
     * 判断已经存在的安装包是否还能用（大小对得上、MD5 对得上）。
     *
     * @param file 已存在的安装包
     * @param info 版本信息
     * @returns 可复用返回 true
     */
    private fun isComplete(file: File, info: AppUpdateDto): Boolean {
        if (info.fileSize > 0 && file.length() != info.fileSize) {
            return false
        }

        if (info.fileMd5.isBlank()) {
            return true
        }

        return md5Hex(file).equals(info.fileMd5, ignoreCase = true)
    }

    /**
     * 安装包所在目录（应用外部私有目录，不需要存储权限）。
     *
     * @param context 上下文
     * @returns 目录（已确保存在）
     */
    private fun updateDirectory(context: Context): File =
        File(context.getExternalFilesDir(null), UpdateDirectoryName).apply { mkdirs() }

    /**
     * 安装包文件名（固定前缀 + 版本号，启动清理按它匹配）。
     *
     * @param version 版本号
     * @returns 文件名，例如 nekoanime-1.0.2.apk
     */
    private fun buildApkName(version: String): String = "nekoanime-$version.apk"

    /**
     * 计算文件 MD5（小写十六进制）。
     *
     * @param file 目标文件
     * @returns 小写十六进制 MD5
     */
    private fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")

        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)

            while (true) {
                val count = input.read(buffer)
                if (count <= 0) {
                    break
                }

                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
