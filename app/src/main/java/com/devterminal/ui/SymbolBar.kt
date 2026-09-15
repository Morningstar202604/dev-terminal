package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.theme.hairline

/**
 * 快捷符号栏。
 *
 * 为什么需要它：手机键盘输入 {}[]<>;" 这些符号要切两三层键盘，
 * 这是移动端编程的第一痛点（Pydroid / Spck / Acode 都内置了同等功能）。
 *
 * 视觉重构：原来的键帽是「实心色块 + 反色文字」，一整排下来像计算器，很吵。
 * 现在改成**描边键帽**（1px 淡边框 + 透明底），只有 ⇥ 和 ⌫ 用强调色描边以示高频。
 */
@Composable
fun SymbolBar(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolKey("⇥", accent = true) { onInsert("\t") }
        SYMBOLS.forEach { s -> SymbolKey(s) { onInsert(s) } }
        SymbolKey("⌫", accent = true) { onBackspace() }
    }
}

/** 编程高频符号（顶层常量，避免每次重组都重建列表） */
private val SYMBOLS = listOf(
    "(", ")", "[", "]", "{", "}", ":", ";", "\"", "'",
    "=", "<", ">", "+", "-", "*", "/", "%", "_", "#",
    "&", "|", "!", ".", ",", "$", "\\"
)

@Composable
private fun SymbolKey(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fg: Color = if (accent) cs.primary else cs.onSurface.copy(alpha = 0.76f)
    val border: Color = if (accent) cs.primary.copy(alpha = 0.42f) else cs.hairline
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) cs.primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp)
            .height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        MonoText(
            label,
            color = fg,
            fontSize = 14
        )
    }
}
