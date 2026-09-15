package com.devterminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devterminal.project.FileNode
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.components.QuietHint
import com.devterminal.ui.components.SectionLabel
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.faint
import com.devterminal.ui.theme.muted

/**
 * 左侧项目文件树。
 *
 * 视觉重构：
 *  - 图标换成 **Outlined 线性风格**，比原来的实心图标轻一档，长时间看不累；
 *  - 选中态用「左侧 2dp 竖条 + 极低透明度底色」——竖条比整行高亮更能精确指向，
 *    也不会在文件多时形成一片色块；
 *  - 目录不再显示第二个文件夹图标，箭头 + 文件夹各司其职。
 */
@Composable
fun FileTree(
    root: FileNode?,
    selectedPath: String?,
    onFileClick: (FileNode) -> Unit,
    onFileLongPress: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxHeight()) {
        SectionLabel(
            "项目",
            modifier = Modifier.padding(
                start = Dimens.gutter, top = Dimens.lg, bottom = Dimens.sm
            )
        )
        if (root == null) {
            QuietHint("加载中…", modifier = Modifier.padding(Dimens.gutter))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val topChildren = root.children.ifEmpty { listOf(root) }
                items(topChildren, key = { it.path }) { node ->
                    TreeNode(
                        node = node,
                        depth = 0,
                        expanded = expanded,
                        selectedPath = selectedPath,
                        onFileClick = onFileClick,
                        onFileLongPress = onFileLongPress
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeNode(
    node: FileNode,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    selectedPath: String?,
    onFileClick: (FileNode) -> Unit,
    onFileLongPress: (FileNode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isOpen = expanded[node.path] ?: false
    val isSelected = node.path == selectedPath

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.sm, end = Dimens.sm, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) cs.primary.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (node.isDirectory) expanded[node.path] = !isOpen
                    else onFileClick(node)
                },
                onLongClick = { onFileLongPress(node) }
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选中指示竖条
        Box(
            Modifier
                .width(2.dp)
                .height(16.dp)
                .background(if (isSelected) cs.primary else Color.Transparent, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width((6 + depth * 14).dp))
        if (node.isDirectory) {
            Icon(
                imageVector = if (isOpen) Icons.Filled.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.faint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (isOpen) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (isSelected) cs.primary else cs.muted,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = if (isSelected) cs.primary else cs.faint,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        MonoText(
            node.name,
            modifier = Modifier.weight(1f),
            color = if (isSelected) cs.primary else cs.onSurface.copy(alpha = 0.86f),
            fontSize = 12,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (node.isDirectory && isOpen) {
        node.children.forEach { child ->
            TreeNode(child, depth + 1, expanded, selectedPath, onFileClick, onFileLongPress)
        }
    }
}
