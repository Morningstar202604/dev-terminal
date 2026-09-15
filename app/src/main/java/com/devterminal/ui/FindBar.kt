package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.components.QuietIconButton
import com.devterminal.ui.components.QuietTextField
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.muted

/** 一次查找请求：pos 为全局字符偏移，index 为要跳到的第几个匹配（从 0 计），requestId 每次变化触发执行 */
data class FindRequest(
    val pos: Int,
    val length: Int,
    val requestId: Long
)

/**
 * 查找 / 替换条。
 *
 * 视觉重构：整条不再用 surfaceVariant 实心底压在编辑器上方（那会形成一条突兀的色带），
 * 改为与背景同色 + 底部一条发丝线，靠内凹的输入框表达「这是可输入区域」。
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
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(cs.surface)
            .padding(horizontal = Dimens.md, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuietTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = "查找…",
                imeAction = ImeAction.Search,
                onImeAction = onNext
            )
            MonoText(
                matchInfo ?: "—",
                modifier = Modifier.padding(horizontal = 8.dp),
                color = cs.muted,
                fontSize = 11
            )
            QuietIconButton(Icons.Filled.KeyboardArrowUp, "上一个", onPrev, size = 19.dp)
            QuietIconButton(Icons.Filled.KeyboardArrowDown, "下一个", onNext, size = 19.dp)
            QuietIconButton(
                if (replaceMode) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                "切换替换模式", onToggleReplaceMode, size = 19.dp,
                tint = if (replaceMode) cs.primary else cs.onSurface.copy(alpha = 0.72f)
            )
            QuietIconButton(Icons.Filled.Close, "关闭查找", onDismiss, size = 18.dp)
        }
        if (replaceMode) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietTextField(
                    value = replaceWith,
                    onValueChange = onReplaceChange,
                    modifier = Modifier.weight(1f),
                    placeholder = "替换为…"
                )
                TextButton(onClick = onReplaceOne) {
                    Text("替换", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
                TextButton(onClick = onReplaceAll) {
                    Text("全部", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
        }
    }
}
