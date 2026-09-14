package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 底部状态栏：VS Code 的标配信息条。
 * 显示光标位置、语言、字符数与保存状态，让用户随时知道「我在哪、文件脏不脏」。
 */
@Composable
fun StatusBar(
    line: Int,
    column: Int,
    language: String,
    charCount: Int,
    dirty: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip("Ln $line, Col $column")
        StatusChip(language)
        StatusChip("$charCount 字符")
        Box(Modifier.padding(horizontal = 4.dp))
        StatusChip(
            if (dirty) "● 未保存" else "已保存",
            highlight = dirty
        )
    }
}

@Composable
private fun StatusChip(text: String, highlight: Boolean = false) {
    val bg: Color = if (highlight) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val fg: Color = if (highlight) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Text(
        text,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = fg,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
