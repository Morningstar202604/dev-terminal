package com.devterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import com.devterminal.settings.ThemeModes
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
    onThemeModeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onEditorThemeChange: (String) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onAutoCloseChange: (Boolean) -> Unit,
    onShowLineNumbersChange: (Boolean) -> Unit,
    onAiConfigChange: (url: String, key: String, model: String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var aiUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var aiKey by remember { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember { mutableStateOf(settings.aiModel) }

    QuietDialog(onDismiss = onDismiss, title = "设置") {
        Spacer(Modifier.height(Dimens.xs))
        SectionLabel("主题外观")
        Spacer(Modifier.height(6.dp))
        Text(
            "「跟随系统」随手机系统深浅自动切换，其余为固定配色。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.sm))
        // 三态选择器：跟随系统 / 浅色 / 深色，与下方「编辑器主题」同款 chip 样式
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            ThemeModes.ALL.forEach { mode ->
                val selected = settings.themeMode == mode
                Surface(
                    onClick = { onThemeModeChange(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        ThemeModes.label(mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) cs.primary else cs.muted,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }
            }
        }

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
        SectionLabel("编辑器主题")
        Spacer(Modifier.height(6.dp))
        Text(
            "「跟随明暗」随系统深浅自动切换，其余为固定配色。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.sm))
        // 两列 chips：一行放不下 7 个选项，竖排又太占高
        EDITOR_THEMES.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                rowThemes.forEach { theme ->
                    val selected = settings.editorTheme == theme
                    Surface(
                        onClick = { onEditorThemeChange(theme) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when (theme) {
                                "auto" -> "跟随明暗"
                                "darcula" -> "Darcula"
                                "monokai" -> "Monokai"
                                "tomorrow-night-blue" -> "明日蓝"
                                "solarized-dark" -> "Solarized 暗"
                                "solarized-light" -> "Solarized 亮"
                                else -> theme
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) cs.primary else cs.muted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                }
                // 最后一行可能只有 1 个，补一个空位保持对齐
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Dimens.sm))
        }

        Spacer(Modifier.height(Dimens.sm))
        QuietSwitchRow(
            label = "自动换行",
            hint = "长行折到下一行，不超出屏幕宽度",
            checked = settings.wordWrap,
            onChange = onWordWrapChange
        )

        Spacer(Modifier.height(Dimens.sm))
        QuietSwitchRow(
            label = "自动闭合括号",
            hint = "输入 ( [ { \" ' 时自动补全闭合符号",
            checked = settings.autoCloseBrackets,
            onChange = onAutoCloseChange
        )

        Spacer(Modifier.height(Dimens.sm))
        QuietSwitchRow(
            label = "行号",
            hint = "编辑器左侧显示行号",
            checked = settings.showLineNumbers,
            onChange = onShowLineNumbersChange
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
            "完全离线的安卓编程终端。内置原生 CPython 3.13.9（PEP 738, arm64-v8a），" +
                "装好即用，无需联网即可编写、运行 Python 代码。",
            style = MaterialTheme.typography.bodySmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.xl))
        SectionLabel("开源许可")
        Spacer(Modifier.height(Dimens.sm))
        listOf(
            "SoraEditor — LGPL-2.1（代码编辑器）",
            "Python 3.13 — PSF License",
            "NativeEngine — MIT",
            "Jetpack Compose — Apache-2.0",
            "AndroidX — Apache-2.0"
        ).forEach {
            MonoText("· $it", color = cs.muted, fontSize = 10,
                modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(Dimens.md))
        Text(
            "离线 Python 能力由原生 CPython 3.13.9 提供，遵循 PSF 许可；" +
                "SoraEditor 以 LGPL 授权，用户可自行替换该库。",
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

/** 新建文件：输入文件名 */
@Composable
fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    QuietDialog(
        onDismiss = onDismiss,
        title = "新建文件",
        confirmLabel = if (name.isNotBlank()) "创建" else null,
        onConfirm = if (name.isNotBlank()) { { onCreate(name.trim()) } } else null
    ) {
        QuietTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "如: main.py"
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            "文件创建在当前项目根目录，创建后自动打开。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.muted
        )
    }
}

/** 新建文件夹：输入文件夹名 */
@Composable
fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    QuietDialog(
        onDismiss = onDismiss,
        title = "新建文件夹",
        confirmLabel = if (name.isNotBlank()) "创建" else null,
        onConfirm = if (name.isNotBlank()) { { onCreate(name.trim()) } } else null
    ) {
        QuietTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "如: utils"
        )
    }
}

/** 切换项目：列出所有已有项目，点一个就切过去；底部可新建项目 */
@Composable
fun SwitchProjectDialog(
    projects: List<java.io.File>,
    currentPath: String?,
    onSelect: (java.io.File) -> Unit,
    onNewProject: () -> Unit,
    onDelete: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    QuietDialog(onDismiss = onDismiss, title = "项目") {
        if (projects.isEmpty()) {
            QuietHint("还没有项目。")
        } else {
            projects.forEach { p ->
                val isCurrent = p.absolutePath == currentPath
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onSelect(p) },
                            onLongClick = { if (!isCurrent) onDelete(p) }
                        )
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder, null,
                        tint = if (isCurrent) cs.primary else cs.muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Dimens.md))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                        if (isCurrent) {
                            Text("当前项目", style = MaterialTheme.typography.labelSmall,
                                color = cs.primary)
                        }
                    }
                    if (isCurrent) StatusDot(cs.primary)
                }
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Hairline()
        Spacer(Modifier.height(Dimens.sm))
        TextButton(onClick = onNewProject) {
            Icon(Icons.Filled.Add, null, tint = cs.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建项目", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}

/** 跳转到行：输入行号 */
@Composable
fun GotoLineDialog(onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    QuietDialog(
        onDismiss = onDismiss,
        title = "跳转到行",
        confirmLabel = if (text.toIntOrNull() != null) "跳转" else null,
        onConfirm = text.toIntOrNull()?.let { { onGo(it) } }
    ) {
        QuietTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "行号（如 12）"
        )
    }
}

/** 离线包管理：列出内置 Pyodide wheel，支持导入外部 .whl */
@Composable
fun PackagesDialog(
    context: android.content.Context,
    onImportWheel: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // 从 assets/pyodide/ 读所有 .whl 文件名，解析包名+版本
    val packages = remember {
        runCatching {
            context.assets.list("pyodide")?.filter { it.endsWith(".whl") }?.map { whl ->
                // numpy-1.26.4-cp312-cp312-pyodide_2024_0_wasm32.whl -> numpy 1.26.4
                val parts = whl.removeSuffix(".whl").split("-")
                val name = parts.getOrElse(0) { whl }
                val version = parts.getOrElse(1) { "" }
                name to version
            }?.sortedBy { it.first } ?: emptyList()
        }.getOrDefault(emptyList())
    }
    QuietDialog(onDismiss = onDismiss, title = "离线 Python 包") {
        Text(
            "以下库已随 APK 内置，代码里直接 import 即可，无需联网。",
            style = MaterialTheme.typography.labelSmall,
            color = cs.muted
        )
        Spacer(Modifier.height(Dimens.md))
        packages.forEach { (name, version) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = cs.primary,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Dimens.md))
                Text(name, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text(version, style = MaterialTheme.typography.labelSmall,
                    color = cs.muted)
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Hairline()
        Spacer(Modifier.height(Dimens.sm))
        TextButton(onClick = onImportWheel) {
            Icon(Icons.Filled.NoteAdd, null, tint = cs.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("导入本地 .whl 文件", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}
