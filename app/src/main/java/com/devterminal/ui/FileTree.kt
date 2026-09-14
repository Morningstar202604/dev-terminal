package com.devterminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devterminal.project.FileNode

/**
 * 左侧项目文件树。目录可展开/折叠，点击文件打开编辑。
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
        Text(
            "项目",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 8.dp)
        )
        if (root == null) {
            Text(
                "加载中…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // 顶层是项目目录，默认展开，不再单独显示根名
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
    val isOpen = expanded[node.path] ?: false
    val isSelected = node.path == selectedPath

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .combinedClickable(
                    onClick = {
                        if (node.isDirectory) expanded[node.path] = !isOpen
                        else onFileClick(node)
                    },
                    onLongClick = { onFileLongPress(node) }
                )
                .padding(start = (8 + depth * 14).dp, top = 7.dp, bottom = 7.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = if (isOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                node.name,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        if (node.isDirectory && isOpen) {
            node.children.forEach { child ->
                TreeNode(child, depth + 1, expanded, selectedPath, onFileClick, onFileLongPress)
            }
        }
    }
}
