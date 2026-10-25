package top.nekoanime.mobile.app.logging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v.log.inspector.LogViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.nekoanime.mobile.app.component.theme.NekoPlayerTheme
import java.io.File

/** 适配 VLog 只扫描默认目录的问题，文件列表读取实际私有目录，内容仍使用 VLog 的解析与查看器。 */
class LogFilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NekoPlayerTheme {
                var files by remember { mutableStateOf<List<File>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                // 先执行抽象层的保留期检查和刷盘，再在 IO 线程列出七日窗口中的日志文件。
                LaunchedEffect(Unit) {
                    AppLog.pruneAsync()
                    AppLog.sizeBytes()
                    files = withContext(Dispatchers.IO) {
                        File(filesDir, "logs").listFiles()?.filter { it.isFile && it.extension == "log" }
                            ?.sortedByDescending { it.name }.orEmpty()
                    }
                    loading = false
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                        TextButton(onClick = { finish() }) { Text("返回设置") }
                        Text("日志记录", style = MaterialTheme.typography.headlineSmall)
                        Text("保留最近 7 天的日志", modifier = Modifier.padding(vertical = 12.dp))
                        when {
                            loading -> CircularProgressIndicator()
                            files.isEmpty() -> Text("暂无日志记录")
                            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(files, key = { it.name }) { file ->
                                    // 只允许点击本目录已枚举的文件，不接受外部传入任意文件路径。
                                    Card(Modifier.fillMaxWidth().clickable {
                                        startActivity(LogViewerActivity.createIntent(this@LogFilesActivity, file.absolutePath))
                                    }) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(file.name.removeSuffix(".log"), style = MaterialTheme.typography.titleMedium)
                                            Text("${file.length()} 字节", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
