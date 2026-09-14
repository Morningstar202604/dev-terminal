package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 底部输出面板：实时显示运行结果，带状态徽标、交互输入行和操作按钮。
 */
@Composable
fun OutputPanel(
    output: List<String>,
    running: Boolean,
    exitCode: Int?,
    inputVisible: Boolean,
    inputDraft: String,
    onInputChange: (String) -> Unit,
    onInputSend: () -> Unit,
    onClear: () -> Unit,
    onRerun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val inputFocus = remember { FocusRequester() }
    // 有新输出时自动滚到底部
    LaunchedEffect(output.size) { scroll.animateScrollTo(scroll.maxValue) }
    // 运行开始后自动聚焦输入框，方便直接交互
    LaunchedEffect(inputVisible) {
        if (inputVisible) runCatching { inputFocus.requestFocus() }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "输出",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            DbStatusBadge(running, exitCode)
            Box(Modifier.weight(1f))
            IconButton(onClick = onRerun) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "重新运行",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, contentDescription = "清空输出",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (output.isEmpty()) {
                Text(
                    "点击上方 ▶ 运行当前文件，输出会显示在这里",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            } else {
                Column {
                    output.forEach { line ->
                        val color = when {
                            line.startsWith("[err]") || line.startsWith("[启动失败]") ->
                                MaterialTheme.colorScheme.error
                            line.startsWith("——") ->
                                MaterialTheme.colorScheme.secondary
                            line.startsWith("[DevTerminal]") || line.startsWith("[执行]") ->
                                MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            line,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
        // 交互输入行：运行中显示，用于响应 Python input() 等
        if (inputVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "›",
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                OutlinedTextField(
                    value = inputDraft,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inputFocus),
                    placeholder = { Text("输入后回车发送…", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onInputSend() })
                )
                IconButton(onClick = onInputSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送输入",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DbStatusBadge(running: Boolean, exitCode: Int?) {
    if (!running && exitCode == null) return
    val label: String
    val color: androidx.compose.ui.graphics.Color
    if (running) {
        label = "运行中"; color = MaterialTheme.colorScheme.secondary
    } else if (exitCode == 0) {
        label = "成功"; color = MaterialTheme.colorScheme.primary
    } else {
        label = "退出码 $exitCode"; color = MaterialTheme.colorScheme.error
    }
    Text(
        "  $label  ",
        fontSize = 11.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(color.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
