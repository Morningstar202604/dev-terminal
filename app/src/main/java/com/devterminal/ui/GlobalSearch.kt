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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.components.QuietDialog
import com.devterminal.ui.components.QuietHint
import com.devterminal.ui.components.QuietTextField
import com.devterminal.ui.components.SectionLabel
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.faint
import com.devterminal.ui.theme.muted
import kotlinx.coroutines.delay

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
 *
 * 视觉重构：结果列表不再用 primary 色标出文件名（一屏十几条蓝字很吵），
 * 改成「文件名用次级灰、行号用等宽灰」的两级灰阶，命中行才是视觉主体。
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

    QuietDialog(onDismiss = onDismiss, title = "全局搜索") {
        QuietTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "在所有项目文件中搜索…",
            imeAction = ImeAction.Search,
            onImeAction = { if (query.isNotBlank()) onSearch(query) }
        )
        Spacer(Modifier.height(Dimens.md))
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            when {
                searching -> Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.6.dp)
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        "搜索中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.muted
                    )
                }
                query.isBlank() -> QuietHint(
                    "输入关键词自动搜索",
                    modifier = Modifier.align(Alignment.Center)
                )
                results.isEmpty() -> QuietHint(
                    "没有匹配结果",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> Column(Modifier.fillMaxWidth()) {
                    SectionLabel(
                        if (results.size >= 200) "200+ 处匹配" else "${results.size} 处匹配",
                        modifier = Modifier.padding(bottom = Dimens.xs)
                    )
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(results, key = { "${it.filePath}:${it.line}" }) { hit ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpen(hit) }
                                    .padding(horizontal = Dimens.xs, vertical = 7.dp)
                            ) {
                                MonoText(
                                    "${hit.fileName} · ${hit.line}",
                                    fontSize = 11,
                                    color = MaterialTheme.colorScheme.faint
                                )
                                MonoText(
                                    hit.text.trim(),
                                    fontSize = 12,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
