package top.nekoanime.mobile.app.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * 版本更新提示的本机偏好。
 *
 * @description 用户在更新弹窗里勾了「7 天内不再提示」之后，这里记下「提示到什么时候为止」。
 * 强制更新不吃这个设置——强制包该弹还是弹（放在 service 层判断）。
 *
 * @param context 上下文（内部只拿 applicationContext）
 */
class UpdatePreference(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /**
     * 记录「N 天内不再提示」。
     *
     * @param days 天数
     * @param version 当时看到的新版本号（只用于排查问题，提示与否不看它）
     */
    fun snooze(days: Long, version: String) {
        val until = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000

        prefs.edit {
            putLong(KeySnoozeUntil, until)
            putString(KeySnoozeVersion, version)
        }
    }

    /**
     * 当前是否处在「不再提示」期内。
     *
     * @returns 还在期限内返回 true
     */
    fun isSnoozed(): Boolean = System.currentTimeMillis() < prefs.getLong(KeySnoozeUntil, 0L)

    /**
     * 剩余不再提示的天数（向上取整，用于提示文案）。
     *
     * @returns 剩余天数；不在期限内返回 0
     */
    fun remainingDays(): Long {
        val left = prefs.getLong(KeySnoozeUntil, 0L) - System.currentTimeMillis()

        if (left <= 0) {
            return 0
        }

        return (left + 24 * 60 * 60 * 1000 - 1) / (24 * 60 * 60 * 1000)
    }

    /**
     * 清掉不再提示的记录（例如用户自己点了「检查更新」想看看有没有新版）。
     */
    fun clearSnooze() {
        prefs.edit {
            remove(KeySnoozeUntil)
            remove(KeySnoozeVersion)
        }
    }

    private companion object {
        /** 配置文件名 */
        const val PrefsName = "neko_update_preference"

        /** 不再提示的截止时间戳（毫秒） */
        const val KeySnoozeUntil = "snooze_until"

        /** 勾选时看到的新版本号 */
        const val KeySnoozeVersion = "snooze_version"
    }
}
