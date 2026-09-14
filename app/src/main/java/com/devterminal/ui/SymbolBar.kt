package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 快捷符号栏：键盘上方一排编程高频符号，点一下直接插入光标处。
 *
 * 为什么需要它：手机键盘输入 {}[]<>;" 这些符号要切两三层键盘，
 * 这是移动端编程的第一痛点（Pydroid / Spck / Acode 都内置了同等功能）。
 * 横向可滚动，首尾「Tab」与「⌫」是高频操作所以固定在两端。
 */
@Composable
fun SymbolBar(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 编程高频符号，顺序按使用频率排列
    val symbols = listOf(
        "(", ")", "[", "]", "{", "}", ":", ";", "\"", "'",
        "=", "<", ">", "+", "-", "*", "/", "%", "_", "#",
        "&", "|", "!", ".", ",", "$", "\\"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolKey("⇥", weight = FontWeight.Bold) { onInsert("\t") }
        symbols.forEach { s ->
            SymbolKey(s) { onInsert(s) }
        }
        SymbolKey("⌫", weight = FontWeight.Bold) { onBackspace() }
    }
}

@Composable
private fun SymbolKey(
    label: String,
    weight: FontWeight = FontWeight.Normal,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .height(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
