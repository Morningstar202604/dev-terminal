package com.devterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 顶部文件 Tab 条：对标 VS Code / Acode 的多文件切换。
 *
 * 单文件切换要走抽屉，来回跳转是移动端第二痛点；
 * Tab 条让「看输出 → 回代码 → 换文件」变成一次点击。
 */
@Composable
fun FileTabs(
    tabs: List<File>,
    activePath: String?,
    onSelect: (File) -> Unit,
    onClose: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom
    ) {
        tabs.forEach { tab ->
            val active = tab.absolutePath == activePath
            val bg = if (active) MaterialTheme.colorScheme.background
                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            Row(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tab.name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onClose(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭 ${tab.name}",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}
