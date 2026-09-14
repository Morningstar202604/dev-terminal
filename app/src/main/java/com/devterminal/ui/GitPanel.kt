package com.devterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devterminal.engine.GitManager

/**
 * Git 面板：仓库状态 / 提交 / 推送 / 拉取，对标 Spck 的 Git 工作流。
 *
 * 状态刷新、init、commit、push、pull 全部调用内置 git 二进制（离线可用）；
 * 远程仓库凭据走 URL 内嵌 token（用户在配置区填写）。
 */
@Composable
fun GitPanelDialog(
    status: GitManager.GitStatus?,
    busy: Boolean,
    lastResult: String?,
    gitInstalled: Boolean,
    gitName: String,
    gitEmail: String,
    remoteUrl: String,
    onConfigChange: (name: String, email: String, remote: String) -> Unit,
    onRefresh: () -> Unit,
    onInit: () -> Unit,
    onCommit: (String) -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onDismiss: () -> Unit
) {
    var commitMsg by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(gitName.isBlank() && remoteUrl.isBlank()) }
    var cfgName by remember { mutableStateOf(gitName) }
    var cfgEmail by remember { mutableStateOf(gitEmail) }
    var cfgRemote by remember { mutableStateOf(remoteUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Git 仓库") },
        text = {
            Column {
                if (!gitInstalled) {
                    Text(
                        "⚠️ 工具链里没有 git 二进制。请重新生成 usrtar.zip（确保 usr/bin/git 存在）。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // ---------- 状态区 ----------
                when {
                    busy -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.padding(start = 10.dp))
                            Text("Git 执行中…", fontSize = 13.sp)
                        }
                    }
                    status == null || !status.isRepo -> {
                        Text(
                            "当前项目还不是 Git 仓库",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onInit) { Text("初始化仓库（main）") }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "分支 ${status.branch}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${status.changes.size} 个变更",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(Modifier.fillMaxWidth().height(if (status.changes.size > 4) 110.dp else 40.dp)) {
                            if (status.changes.isEmpty()) {
                                item { Text("工作区干净 ✓", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary) }
                            }
                            items(status.changes, key = { it.path }) { c ->
                                Text(
                                    "${c.statusCode.padEnd(3)} ${c.path}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = commitMsg,
                            onValueChange = { commitMsg = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("提交信息…", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onCommit(commitMsg); commitMsg = "" },
                                enabled = commitMsg.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("提交", fontSize = 12.sp) }
                            TextButton(onClick = onPush, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudUpload, null, Modifier.size(15.dp))
                                Text(" 推送", fontSize = 12.sp)
                            }
                            TextButton(onClick = onPull, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudDownload, null, Modifier.size(15.dp))
                                Text(" 拉取", fontSize = 12.sp)
                            }
                        }
                        status.recentCommits.take(3).forEach { c ->
                            Text(
                                c,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                lastResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // ---------- 配置区 ----------
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showConfig = !showConfig }) {
                    Text(if (showConfig) "收起配置" else "配置（身份 / 远程）", fontSize = 11.sp)
                }
                if (showConfig) {
                    OutlinedTextField(
                        value = cfgName,
                        onValueChange = { cfgName = it },
                        label = { Text("Git 用户名", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cfgEmail,
                        onValueChange = { cfgEmail = it },
                        label = { Text("Git 邮箱", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cfgRemote,
                        onValueChange = { cfgRemote = it },
                        label = { Text("远程 URL（token 可内嵌）", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            onConfigChange(cfgName.trim(), cfgEmail.trim(), cfgRemote.trim())
                            showConfig = false
                        }
                    ) { Text("保存配置", fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(15.dp))
                Text("刷新")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
