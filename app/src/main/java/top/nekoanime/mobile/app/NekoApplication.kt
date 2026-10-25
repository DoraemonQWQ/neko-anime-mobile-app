package top.nekoanime.mobile.app

import android.app.Application
import top.nekoanime.mobile.app.logging.AppLog

/** 在进程启动时建立日志后端，早于配置读取、页面导航与任何网络请求。 */
class NekoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        // 字幕字号在进程启动恢复，全局共享到所有番剧播放页。
        top.nekoanime.mobile.app.data.local.PlayerPreferences.init(this)
        AppLog.i("Application", "应用启动 version=${BuildConfig.VERSION_NAME}")
    }
}
