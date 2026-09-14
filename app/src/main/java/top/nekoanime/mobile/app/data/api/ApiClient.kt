package top.nekoanime.mobile.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import top.nekoanime.mobile.app.config.ConfigLoader
import java.util.concurrent.TimeUnit

/**
 * 后端接口客户端。
 *
 * @description 所有请求都从这里出去，统一处理：base url、超时、JSON 解析、错误信息。
 * 成功返回 `Result.success(解析后的对象)`，失败返回 `Result.failure(带可读信息的异常)`，
 * 调用方不需要再碰 OkHttp / JSON。
 *
 * 鉴权：登录之后把 JWT 交给 {@link authToken}，之后每个请求自动带 `Authorization: Bearer ...`。
 *
 * base url、超时都来自配置文件（`assets/config/config.<模式>.json` → {@link ConfigLoader}）：
 * debug 连本机后端（配合 `adb reverse tcp:8080 tcp:8080`），release 连线上域名。
 */
object ApiClient {
    /** 未知字段直接忽略、缺字段用默认值：后端加字段时老版本 App 不会因为解析失败而崩 */
    /** 统一 JSON：忽略未知字段、缺字段用默认值（后端加字段不会让老版本解析失败） */
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(ConfigLoader.config.api.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(ConfigLoader.config.api.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    /** 当前登录态的 JWT；null = 未登录（登录功能接上后由 state 层写入） */
    @Volatile
    var authToken: String? = null

    /**
     * 模拟网络延迟（毫秒）。
     *
     * @description 只有配置里 `api.mockDelayMillis` 大于 0 时才生效：
     * 本地开发时能看清「加载中 / 已经到底啦」这些状态，线上配置成 0 即无感。
     */
    private suspend fun applyMockDelay() {
        val mockDelay = ConfigLoader.config.api.mockDelayMillis

        if (mockDelay > 0) {
            delay(mockDelay)
        }
    }

    /**
     * 发一个 GET 请求并解析响应。
     *
     * @param path 以 `/` 开头的路径，例如 `/api/anime/hot`
     * @param query 查询参数
     * @param serializer 响应体的反序列化器
     * @returns 成功是解析后的对象，失败是带可读信息的异常
     */
    suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        serializer: DeserializationStrategy<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            applyMockDelay()
            val url = (ConfigLoader.config.api.baseUrl + path).toHttpUrlOrNull()
                ?: error("接口地址不合法：$path")
            val urlWithQuery = url.newBuilder().apply {
                query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()

            val request = Request.Builder()
                .url(urlWithQuery)
                .apply { authToken?.let { header("Authorization", "Bearer $it") } }
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error("接口 $path 返回 ${response.code}：${body.take(180)}")
                }

                json.decodeFromString(serializer, body)
            }
        }.onFailure { error ->
            // 打一条日志就够了：网络问题在界面上通常只表现为"这块数据没出来"
            Log.w("NekoApi", "请求失败 $path -> ${error.message}")
        }
    }

    /**
     * 直接请求一个绝对地址（不走配置里的 baseUrl）：检查更新的版本 JSON、下载安装包这类用得上。
     *
     * @param url 完整地址
     * @param serializer 响应体反序列化器
     * @returns 解析后的对象
     */
    suspend fun <T> getAbsolute(
        url: String,
        serializer: DeserializationStrategy<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            applyMockDelay()
            val target = url.toHttpUrlOrNull() ?: error("地址不合法：$url")
            val request = Request.Builder().url(target).get().build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("请求 $url 返回 ${response.code}")
                }

                json.decodeFromString(serializer, body)
            }
        }.onFailure { error -> Log.w("NekoApi", "请求失败 $url -> ${error.message}") }
    }

    /**
     * 发一个 POST（JSON body）并解析响应。
     *
     * @param path 以 `/` 开头的路径
     * @param bodyJson 请求体的 JSON 文本
     * @param serializer 响应体的反序列化器
     * @returns 成功是解析后的对象，失败是带可读信息的异常
     */
    suspend fun <B, T> post(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
        serializer: DeserializationStrategy<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            applyMockDelay()
            val url = (ConfigLoader.config.api.baseUrl + path).toHttpUrlOrNull()
                ?: error("接口地址不合法：$path")

            val bodyText = json.encodeToString(bodySerializer, body)
            val request = Request.Builder()
                .url(url)
                .post(bodyText.toRequestBody(JsonMediaType))
                .apply { authToken?.let { header("Authorization", "Bearer $it") } }
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error("接口 $path 返回 ${response.code}：${body.take(180)}")
                }

                json.decodeFromString(serializer, body)
            }
        }.onFailure { error ->
            Log.w("NekoApi", "请求失败 $path -> ${error.message}")
        }
    }

    private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 发一个 POST，并把响应体当作**纯文本**返回。
     *
     * @description 后端有些接口直接返回纯文本而不是 JSON —— 例如登录成功返回的是 JWT 字符串本体
     * （`eyJ...`，没有引号）。这类响应用 JSON 解析会直接抛
     * "Expected quotation mark '"', but had 'e' instead"，所以单独走这个方法。
     *
     * @param path 以 `/` 开头的路径
     * @param body 请求体对象
     * @param bodySerializer 请求体序列化器
     * @returns 成功是响应文本（已去首尾空白），失败是带可读信息的异常
     */
    suspend fun <B> postText(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            applyMockDelay()
            val url = (ConfigLoader.config.api.baseUrl + path).toHttpUrlOrNull()
                ?: error("接口地址不合法：$path")

            val bodyText = json.encodeToString(bodySerializer, body)
            val request = Request.Builder()
                .url(url)
                .post(bodyText.toRequestBody(JsonMediaType))
                .apply { authToken?.let { header("Authorization", "Bearer $it") } }
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error("接口 $path 返回 ${response.code}：${text.take(180)}")
                }

                text.trim()
            }
        }.onFailure { error ->
            Log.w("NekoApi", "请求失败 $path -> ${error.message}")
        }
    }

    /**
     * 发一个 POST，不关心响应体（改密码、提交头像这类写接口返回值没内容）。
     *
     * @param path 接口路径
     * @param body 请求体对象
     * @param bodySerializer 请求体序列化器
     * @returns 只有成功/失败
     */
    suspend fun <B> postUnit(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            applyMockDelay()
            val url = (ConfigLoader.config.api.baseUrl + path).toHttpUrlOrNull()
                ?: error("接口地址不合法：$path")

            val bodyText = json.encodeToString(bodySerializer, body)
            val request = Request.Builder()
                .url(url)
                .post(bodyText.toRequestBody(JsonMediaType))
                .apply { authToken?.let { header("Authorization", "Bearer $it") } }
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("接口 $path 返回 ${response.code}：${text.take(180)}")
                }
            }
        }.onFailure { error -> Log.w("NekoApi", "请求失败 $path -> ${error.message}") }
    }

    /**
     * PUT 一段字节（头像直传：地址是对象存储给的预签名 URL，不带鉴权头）。
     *
     * @param url 预签名直传地址
     * @param bytes 文件内容
     * @param contentType 内容类型（image/jpeg 等）
     * @returns 只有成功/失败
     */
    suspend fun putBytes(url: String, bytes: ByteArray, contentType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = url.toHttpUrlOrNull() ?: error("直传地址不合法")
                val request = Request.Builder()
                    .url(target)
                    .put(bytes.toRequestBody(contentType.toMediaType()))
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("直传失败：${response.code}")
                    }
                }
            }.onFailure { error -> Log.w("NekoApi", "直传失败 -> ${error.message}") }
        }
}
