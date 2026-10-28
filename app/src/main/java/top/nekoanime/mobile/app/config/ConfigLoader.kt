package top.nekoanime.mobile.app.config

import android.content.Context
import top.nekoanime.mobile.app.logging.AppLog as Log
import kotlinx.serialization.json.Json
import top.nekoanime.mobile.app.BuildConfig

/**
 * 配置加载器。
 *
 * @description 从 assets 里读 {@link BuildConfig#CONFIG_FILE}（debug 是 config/config.debug.json，
 * release 是 config/config.release.json），解析成 {@link AppConfig} 后全局共用。
 *
 * 读失败（文件缺失、json 写错）不会让 App 起不来：打一条 error 日志并用默认值兜底。
 * 注意：需要在 Activity 启动时先调用一次 {@link init}。
 */
object ConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private var loaded: AppConfig? = null

    /**
     * 当前配置。没初始化过（或读失败）时返回默认值，不会抛异常。
     */
    val config: AppConfig
        get() = loaded ?: AppConfig()

    /**
     * 读取配置文件。
     *
     * @param context 上下文（内部只用 applicationContext）
     */
    fun init(context: Context) {
        if (loaded != null) {
            return
        }

        loaded = runCatching {
            val text = context.applicationContext.assets
                .open(BuildConfig.CONFIG_FILE)
                .bufferedReader()
                .use { it.readText() }

            json.decodeFromString(AppConfig.serializer(), text)
        }.getOrElse { error ->
            Log.e("NekoConfig", "读取 ${BuildConfig.CONFIG_FILE} 失败，使用默认配置", error)
            AppConfig()
        }

        Log.i(
            "NekoConfig",
            "配置已加载（${BuildConfig.CONFIG_FILE}）：首页题材=${config.home.recommendTags}",
        )
    }
}
