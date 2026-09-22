package com.devterminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devterminal.BuildConfig
import com.devterminal.project.Templates
import com.devterminal.settings.AppSettings
import com.devterminal.engine.EnvReport
import com.devterminal.engine.EnvDiagnostics
import com.devterminal.ui.components.Hairline
import com.devterminal.ui.components.MonoText
import com.devterminal.ui.components.QuietDialog
import com.devterminal.ui.components.QuietSwitchRow
import com.devterminal.ui.components.QuietTextField
import com.devterminal.ui.components.SectionLabel
import com.devterminal.ui.components.StatusDot
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.faint
import com.devterminal.ui.theme.hairline
import com.devterminal.ui.theme.muted
import java.io.File
import kotlin.math.roundToInt

/*
 * 所有对话框集中在这里，主界面只负责调度。
 *
 * 统一规则：
 *  - 用 QuietDialog（26dp 圆角、左对齐标题、右对齐按钮），不再各自为政；
 *  - 分区用 SectionLabel + 留白，不再画分割线；
 *  - 危险操作（删除）必须先点一次再确认，且用 error 色但不加背景块。
 */

/** 设置页 */
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onToggleDark: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onAiConfigChange: (url: String, key: String, model: String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var aiUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var aiKey by remember { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember { mutableStateOf(settings.aiModel) }

    QuietDialog(onDismiss = onDismiss, title = "设置") {
        QuietSwitchRow(
            label = "深色主题",
            hint = "关闭后使用浅色配色",
            checked = settings.darkTheme,
            onChange = onToggleDark
        )
        Spacer(Modifier.height(Dimens.md))
        QuietSwitchRow(
            label = "切换文件时自动保存",
            hint = "避免忘记保存导致改动丢失",
            checked = settings.autoSave,
            onChange = onAutoSaveChange
        )

        Spacer(Modifier.height(Dimens.xl))
        SectionLabel("编辑器字号 · ${settings.editorFontSize} sp")
        Slider(
            value = settings.editorFontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.roundToInt()) },
            valueRange = 10f..24f,
            steps = 13,
            colors = SliderDefaults.colors(
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.hairline,
                thumbColor = cs.primary
            )
        )

        Spacer(Modifier.height(Dimens.lg))
        SectionLabel("运行超时 · ${settings.timeoutSeconds} 秒")
        Slider(
            value = settings.timeoutSeconds.toFloat(),
            onValueChange = { onTimeoutChange(it.roundToInt()) },
            valueRange = 10f..600f,
            steps = 58,
            colors = SliderDefaults.colors(
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.hairline,
                thumbColor = cs.primary
            )
        )
        Text(
            "超时后进程会被强制结束，防止死循环耗尽电量。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )

        Spacer(Modifier.height(Dimens.xl))
        SectionLabel("AI 助手（可选）")
        Spacer(Modifier.height(6.dp))
        Text(
            "填写你自己的 OpenAI 兼容端点与密钥（BYOK）。默认值为占位示例，需改成实际可用的地址；不配置不影响其他功能。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = aiUrl,
            onValueChange = { aiUrl = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "端点 URL（OpenAI 兼容，/v1 结尾）"
        )
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = aiModel,
            onValueChange = { aiModel = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "模型名"
        )
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = aiKey,
            onValueChange = { aiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "API Key（本地端点可留空）"
        )
        Spacer(Modifier.height(Dimens.sm))
        TextButton(
            onClick = { onAiConfigChange(aiUrl.trim(), aiKey.trim(), aiModel.trim()) },
            enabled = aiUrl.isNotBlank()
        ) {
            Text("保存 AI 配置", style = MaterialTheme.typography.labelLarge,
                color = if (aiUrl.isNotBlank()) cs.primary else cs.faint)
        }
    }
}

/** 关于页：版本与开源许可 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    QuietDialog(onDismiss = onDismiss, title = "关于") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, tint = cs.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Dimens.sm))
            Column {
                Text("DevTerminal", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                MonoText("版本 ${BuildConfig.VERSION_NAME}", color = cs.muted, fontSize = 10)
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Text(
            "完全离线的安卓编程终端。内置 Python / Java 工具链，无需联网即可编写、运行、调试代码。",
            style = MaterialTheme.typography.bodySmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.xl))
        SectionLabel("开源许可")
        Spacer(Modifier.height(Dimens.sm))
        listOf(
            "SoraEditor — LGPL-2.1（代码编辑器）",
            "Termux packages — GPL 等（离线工具链）",
            "Jetpack Compose — Apache-2.0",
            "AndroidX — Apache-2.0"
        ).forEach {
            MonoText("· $it", color = cs.muted, fontSize = 10,
                modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(Dimens.md))
        Text(
            "本应用的离线工具链来自 Termux 项目，遵循其相应许可；SoraEditor 以 LGPL 授权，用户可自行替换该库。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.faint
        )
    }
}

/** 运行配置：命令行参数 + Java 主类 */
@Composable
fun RunConfigDialog(
    args: String,
    mainClass: String,
    onArgsChange: (String) -> Unit,
    onMainClassChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    QuietDialog(onDismiss = onDismiss, title = "运行配置") {
        SectionLabel("命令行参数")
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = args,
            onValueChange = onArgsChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "如: --name \"hello world\" -v"
        )
        Spacer(Modifier.height(Dimens.lg))
        SectionLabel("Java 主类（留空自动探测）")
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = mainClass,
            onValueChange = onMainClassChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "如: com.example.Main"
        )
        Spacer(Modifier.height(Dimens.md))
        Text(
            "参数按空格切分，含空格的参数用双引号包起来。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )
    }
}

/** 环境自检结果对话框：展示每个工具是否可用 */
@Composable
fun DiagnosticsDialog(
    diagnostics: EnvReport?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    QuietDialog(
        onDismiss = onDismiss,
        title = "离线环境自检",
        confirmLabel = "重新检测",
        onConfirm = onRefresh
    ) {
        if (diagnostics == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                Spacer(Modifier.width(Dimens.sm))
                Text("检测中…", style = MaterialTheme.typography.bodySmall, color = cs.muted)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (diagnostics.prefixReady) cs.primary else cs.error)
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    if (diagnostics.prefixReady) "工具链已安装" else "工具链缺失",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            MonoText(
                "占用空间：" + EnvDiagnostics.formatSize(diagnostics.installedSizeBytes),
                color = cs.muted, fontSize = 11
            )
            Spacer(Modifier.height(Dimens.md))
            diagnostics.tools.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (t.available) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                        null,
                        tint = if (t.available) cs.primary else cs.error,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 3.dp)
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Column {
                        Text(t.name, style = MaterialTheme.typography.bodySmall)
                        MonoText(t.detail, color = cs.faint, fontSize = 10)
                    }
                }
            }
        }
    }
}

/** 新建项目：模板列表 */
@Composable
fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    QuietDialog(onDismiss = onDismiss, title = "新建项目") {
        Templates.all.forEach { tpl ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCreate(tpl.id) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Code, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.md))
                Column {
                    Text(tpl.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Text(tpl.description, style = MaterialTheme.typography.labelSmall,
                        color = cs.muted)
                }
            }
        }
    }
}

/** 文件操作：重命名 / 删除（删除需二次确认） */
@Composable
fun FileActionDialog(
    target: File,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(target.name) }
    var confirmDelete by remember { mutableStateOf(false) }

    QuietDialog(
        onDismiss = onDismiss,
        title = target.name,
        confirmLabel = if (name.isNotBlank() && name != target.name) "重命名" else null,
        onConfirm = if (name.isNotBlank() && name != target.name) {
            { onRename(name) }
        } else null
    ) {
        SectionLabel("重命名")
        Spacer(Modifier.height(Dimens.sm))
        QuietTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Dimens.lg))
        Hairline()
        Spacer(Modifier.height(Dimens.md))
        if (confirmDelete) {
            Text(
                "确认删除？此操作不可恢复（目录会连同内容一起删除）。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.error
            )
            Spacer(Modifier.height(Dimens.sm))
            TextButton(onClick = onDelete) {
                Text("确认删除", style = MaterialTheme.typography.labelLarge, color = cs.error)
            }
        } else {
            TextButton(onClick = { confirmDelete = true }) {
                Text("删除此文件", style = MaterialTheme.typography.labelLarge, color = cs.error)
            }
        }
    }
}
