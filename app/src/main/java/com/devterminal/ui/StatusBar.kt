package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.components.StatusDot
import com.devterminal.ui.theme.faint
import com.devterminal.ui.theme.muted

/**
 * 底部状态条。
 *
 * 重构要点：去掉原来的「色块 chip」——四五个带底色的小药丸在小屏幕上非常噪。
 * 现在一律是等宽灰字 + `·` 分隔，只有「未保存」用一个色点提示，
 * 因为那是唯一需要用户立刻注意的信息。
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
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(cs.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonoText("Ln $line, Col $column", color = cs.muted, fontSize = 10)
        Separator()
        MonoText(language, color = cs.muted, fontSize = 10)
        Separator()
        MonoText("$charCount 字符", color = cs.muted, fontSize = 10)
        Spacer(Modifier.weight(1f))
        if (dirty) {
            StatusDot(cs.secondary)
            MonoText(
                " 未保存",
                color = cs.secondary,
                fontSize = 10
            )
        } else {
            MonoText("已保存", color = cs.faint, fontSize = 10)
        }
    }
}

@Composable
private fun Separator() {
    MonoText("  ·  ", color = MaterialTheme.colorScheme.faint, fontSize = 10)
}
