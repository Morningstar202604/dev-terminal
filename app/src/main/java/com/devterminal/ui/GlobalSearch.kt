package com.devterminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 全局搜索命中项 */
data class SearchHit(
    val filePath: String,
    val fileName: String,
    val line: Int,
    val text: String
)

/**
 * 全局搜索（跨文件）：对标 Acode / Spck 的 project-wide search。
 * 移动端场景常用「这个函数在哪定义的」，全局搜索是刚需。
 */
@Composable
fun GlobalSearchDialog(
    query: String,
    results: List<SearchHit>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (SearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    // 输入防抖 400ms 后自动搜索，避免每个字符都跑一遍文件树
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(400)
        onSearch(query)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局搜索") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("在所有项目文件中搜索…", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    when {
                        searching -> Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("搜索中…", fontSize = 13.sp)
                        }
                        query.isBlank() -> Text(
                            "输入关键词，回车开始搜索",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        results.isEmpty() -> Text(
                            "没有匹配结果",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        else -> LazyColumn {
                            items(results, key = { "${it.filePath}:${it.line}" }) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onOpen(hit) }
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${hit.fileName} · 行 ${hit.line}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        hit.text,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "共 ${results.size} 处",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 8.dp, top = 12.dp)
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
