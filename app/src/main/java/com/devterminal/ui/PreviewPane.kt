package com.devterminal.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.devterminal.ui.components.QuietIconButton
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.muted
import java.io.File

/**
 * 实时预览面板（编辑器分屏的下半区）：Markdown / HTML 渲染结果。
 *
 * 为什么用独立 WebView 而不是复用 Pyodide 引擎的 WebView：
 * 那个 WebView 装着 9MB 解释器与虚拟文件系统，预览只需要渲染静态 HTML，
 * 复用会互相拖累（预览刷新会打断执行状态），各用各的最省心。
 *
 * 刷新策略：外层用 collectLatest + delay 做防抖，这里收到新 html 就整体重载。
 * loadDataWithBaseURL 的 baseURL 传 null——预览不需要相对路径解析能力，
 * 同时断掉页面反查 file:// 的可能。
 */
@Composable
fun PreviewPane(
    html: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // WebView 实例随面板存续，html 变化只走 loadData，不重建（重建会闪白屏）
    val webView = remember(context) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = false   // 静态渲染，不执行脚本
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            isVerticalScrollBarEnabled = true
        }
    }
    val bg = cs.surface.toArgb()

    // 离开预览分屏时销毁 WebView：否则其内核/渲染线程会随 Activity 泄漏
    DisposableEffect(webView) {
        onDispose { runCatching { webView.destroy() } }
    }

    Surface(color = cs.surface, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .padding(start = Dimens.md, end = Dimens.md, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "实时预览",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.muted
                )
                Spacer(Modifier.weight(1f))
                QuietIconButton(
                    icon = Icons.Outlined.OpenInBrowser,
                    contentDescription = "在外部浏览器打开",
                    onClick = {
                        runCatching {
                            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                            val f = File(dir, "preview.html").apply { writeText(html) }
                            val uri: Uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", f
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "text/html")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                    size = 18.dp
                )
            }
            Spacer(Modifier.height(6.dp))
            AndroidView(
                factory = { view ->
                    webView.apply {
                        setBackgroundColor(bg)
                        loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                },
                update = { view ->
                    view.setBackgroundColor(bg)
                    view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
