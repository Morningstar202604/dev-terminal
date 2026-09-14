package com.devterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devterminal.engine.CrashLogger
import com.devterminal.ui.EditorScreen
import com.devterminal.ui.EditorViewModel
import com.devterminal.ui.theme.DevTerminalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启全局崩溃捕获，崩溃堆栈写入 App 私有目录便于排查
        CrashLogger.install(this)

        setContent {
            // 主题跟随用户设置（而非仅系统），设置页可实时切换
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            DevTerminalTheme(darkTheme = ui.settings.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditorScreen(viewModel)
                }
            }
        }
    }
}
