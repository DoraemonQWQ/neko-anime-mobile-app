package top.nekoanime.mobile.app.page.history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.nekoanime.mobile.app.model.history.HistoryDateContext
import top.nekoanime.mobile.app.model.history.historyDateContext
import java.util.Calendar

/**
 * 历史标题不能只在进入页面时计算：系统校时、时区切换和跨午夜都会改变「今天/昨天」。
 * 广播负责即时更新，前台恢复补上后台期间的变化，计时器保证页面一直打开时也能跨日更新。
 */
@Composable
internal fun rememberHistoryDateContext(): HistoryDateContext {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var dates by remember { mutableStateOf(historyDateContext()) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                dates = historyDateContext()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dates = historyDateContext()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            dates = historyDateContext(now)
            // 下一次本地午夜用 Calendar 算，覆盖夏令时的 23/25 小时日；最长一分钟再核对一次。
            val midnight = Calendar.getInstance(dates.timeZone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            delay((midnight - now).coerceIn(1L, 60_000L))
        }
    }
    return dates
}
