package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 一次查找请求：query 为查询词，index 为要跳到的第几个匹配（从 0 计），requestId 每次变化触发执行 */
data class FindRequest(
    val pos: Int,
    val length: Int,
    val requestId: Long
)

/**
 * 查找 / 替换条：对标 Acode 的 Search & Replace 与 VS Code 的查找面板。
 *
 * 实现策略：跳转走 SoraEditor 的选区 API（精确、可滚动定位），
 * 替换走字符串层（替换后整段文本回流，编辑器 setText 保持光标），零高风险 API。
 */
@Composable
fun FindBar(
    query: String,
    replaceWith: String,
    matchInfo: String?,
    replaceMode: Boolean,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onToggleReplaceMode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("查找…", fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Text(
                matchInfo ?: "–",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "上一个", Modifier.size(20.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "下一个", Modifier.size(20.dp))
            }
            IconButton(onClick = onToggleReplaceMode, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.SwapVert, "切换替换模式", Modifier.size(18.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, "关闭查找", Modifier.size(18.dp))
            }
        }
        if (replaceMode) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replaceWith,
                    onValueChange = onReplaceChange,
                    modifier = Modifier.weight(1f).height(52.dp),
                    placeholder = { Text("替换为…", fontSize = 12.sp) },
                    textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
                TextButton(onClick = onReplaceOne) { Text("替换", fontSize = 12.sp) }
                TextButton(onClick = onReplaceAll) { Text("全部", fontSize = 12.sp) }
            }
        }
    }
}
