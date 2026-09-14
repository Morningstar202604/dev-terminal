package com.devterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devterminal.engine.AiClient

/**
 * AI 助手面板：BYOK + 本地端点优先（2026 移动端 AI 编程的主流形态）。
 *
 * 四个快捷动作：解释此文件 / 修复运行错误 / 生成测试 / 添加注释。
 * 未配置端点时显示引导而不是空白。
 */
@Composable
fun AiPanelDialog(
    messages: List<AiClient.ChatMessage>,
    busy: Boolean,
    configured: Boolean,
    inputDraft: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // 新消息到达时滚到底部
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 助手") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (!configured) {
                    Text(
                        "AI 助手未配置。默认端点是本机 Ollama（127.0.0.1:11434），" +
                            "也可以在设置里填任何 OpenAI 兼容端点。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "不配置也不影响其他功能，App 依然完全离线。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    // 对话区
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        if (messages.isEmpty() && !busy) {
                            Column(Modifier.align(Alignment.Center)) {
                                Text(
                                    "问我任何关于当前文件的问题，或点下面的快捷动作",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                            items(messages) { m ->
                                Bubble(m)
                                Spacer(Modifier.height(6.dp))
                            }
                            if (busy) {
                                item {
                                    Text(
                                        "AI 思考中…",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 快捷动作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "解释此文件" to "解释当前文件的代码逻辑",
                            "修复运行错误" to "修复最近的运行报错",
                            "生成测试" to "为当前文件生成单元测试",
                            "加注释" to "给当前文件的函数加上注释"
                        ).forEach { (label, _) ->
                            TextButton(
                                onClick = { onQuick(label) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 输入行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputDraft,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问点什么…", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                    )
                    TextButton(onClick = onSend, enabled = !busy && inputDraft.isNotBlank()) {
                        Text("发送")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun Bubble(m: AiClient.ChatMessage) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 10.dp, topEnd = 10.dp,
                bottomStart = if (isUser) 10.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 10.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                m.content,
                fontSize = 12.sp,
                fontFamily = if (isUser) FontFamily.Default else FontFamily.Monospace,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
