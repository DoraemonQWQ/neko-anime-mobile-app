package top.nekoanime.mobile.app.state

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.nekoanime.mobile.app.data.local.PlayerPreferences

/** 使用真实 SharedPreferences 验证字号持久化与边界，不把番剧 ID 写入偏好键。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlayerPreferencesTest {
    @Test
    fun restoresGlobalSubtitleSizeAndClampsBounds() {
        val context = RuntimeEnvironment.getApplication()
        val store = context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)
        store.edit().clear().commit()
        val field = PlayerPreferences::class.java.getDeclaredField("preferences").apply { isAccessible = true }
        field.set(PlayerPreferences, null)
        try {
            // 首次默认 16；写入后模拟新进程重建偏好对象，确认从磁盘恢复而不是只看内存状态。
            PlayerPreferences.init(context)
            assertEquals(16, PlayerPreferences.subtitleSizeSp)
            PlayerPreferences.setSubtitleSize(24)
            assertEquals(24, store.getInt("subtitle_size_sp", 0))
            field.set(PlayerPreferences, null)
            PlayerPreferences.init(context)
            assertEquals(24, PlayerPreferences.subtitleSizeSp)
            // 不合法字号不能越过步进器边界，也不能将越界值持久化。
            PlayerPreferences.setSubtitleSize(100)
            assertEquals(32, PlayerPreferences.subtitleSizeSp)
            PlayerPreferences.setSubtitleSize(-1)
            assertEquals(12, store.getInt("subtitle_size_sp", 0))
        } finally {
            PlayerPreferences.setSubtitleSize(16)
            store.edit().clear().commit()
            field.set(PlayerPreferences, null)
        }
    }
}
