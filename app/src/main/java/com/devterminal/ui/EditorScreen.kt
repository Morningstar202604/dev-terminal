package com.devterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devterminal.project.Templates
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showNewProject by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showRunConfig by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var showGit by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    /** 长按文件树后的操作目标 */
    var actionTarget by remember { mutableStateOf<File?>(null) }
    /** 全局搜索关键词 */
    var gsearchQuery by remember { mutableStateOf("") }

    // SAF：导入任意文件到项目
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importFile(it) } }

    // SAF：把当前文件导出到用户选择的位置
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { vm.exportCurrentFile(it) } }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // 切后台 / 系统回收前自动保存：任何已发布编辑器的数据安全底线
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.saveOnBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 返回键：先关查找条/抽屉，而不是直接退出 App
    androidx.activity.compose.BackHandler(enabled = ui.findVisible || drawerState.isOpen) {
        when {
            ui.findVisible -> vm.showFind(false)
            drawerState.isOpen -> scope.launch { drawerState.close() }
        }
    }

    // 环境准备中：全屏进度
    if (ui.envState == EnvState.PREPARING) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(ui.envMessage, fontSize = 14.sp)
                if (ui.installProgress >= 0f) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { ui.installProgress },
                        modifier = Modifier.width(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "首次启动需解压工具链，请稍候…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
        return
    }

    // 工具链缺失：引导页，告诉用户该怎么修
    if (ui.envState == EnvState.ERROR && ui.diagnostics == null) {
        MissingToolchainScreen(
            message = ui.envMessage,
            detail = ui.message,
            onRetry = { vm.retryPrepare() }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(280.dp)) {
                Column(Modifier.fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DevTerminal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.showFind(true) }) {
                            Icon(Icons.Filled.Search, "在项目中查找",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                    Divider()
                    FileTree(
                        root = ui.tree,
                        selectedPath = ui.currentFile?.absolutePath,
                        onFileClick = { vm.openFile(File(it.path)) },
                        onFileLongPress = { node -> actionTarget = File(node.path) },
                        modifier = Modifier.weight(1f)
                    )
                    Divider()
                    // 底部操作区：分成两行，避免小屏挤压
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = { showNewProject = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("新项目", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FileDownload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("导入", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { exportLauncher.launch(vm.suggestedExportName()) },
                                enabled = ui.currentFile != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FileUpload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("导出", fontSize = 12.sp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = { showDiagnostics = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("自检", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Settings, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("设置", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { showAbout = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("关于", fontSize = 12.sp)
                            }
                        }
                        // 第三行：Git / AI / 全局搜索（v0.3）
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = { showGit = true; vm.refreshGitStatus() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AccountTree, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Git", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { showAi = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(3.dp))
                                Text("AI", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { showGlobalSearch = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("搜索", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                ui.currentFile?.name ?: "未打开文件",
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (ui.dirty) {
                                Text("未保存", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "打开文件树")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPalette = true }) {
                            Icon(Icons.Filled.KeyboardCommandKey, "命令面板",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { vm.showFind(true) }) {
                            Icon(Icons.Filled.Search, "查找 / 替换",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = { showRunConfig = true }) {
                            Icon(Icons.Filled.Tune, "运行配置",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        if (ui.dirty) {
                            IconButton(onClick = { vm.save() }) {
                                Icon(Icons.Filled.Save, "保存")
                            }
                        }
                        if (ui.running) {
                            IconButton(onClick = { vm.stopRun() }) {
                                Icon(Icons.Filled.Stop, "停止",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(
                                onClick = { vm.runCurrent() },
                                enabled = ui.currentFile != null
                            ) {
                                Icon(Icons.Filled.PlayArrow, "运行",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            // 输出面板高度：初始值来自设置，拖拽即时调整，松手才落盘
            var outputHeight by remember(ui.settings.outputHeightDp) {
                mutableStateOf(ui.settings.outputHeightDp.toFloat())
            }
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (ui.envState == EnvState.ERROR) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Text(ui.envMessage, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                // 多文件 Tab 条（对标 VS Code / Acode）
                FileTabs(
                    tabs = ui.openTabs,
                    activePath = ui.currentFile?.absolutePath,
                    onSelect = { vm.openFile(it) },
                    onClose = { vm.closeTab(it) }
                )
                // 查找 / 替换条
                if (ui.findVisible) {
                    FindBar(
                        query = ui.findQuery,
                        replaceWith = ui.findReplaceWith,
                        matchInfo = vm.findMatchInfo(ui),
                        replaceMode = ui.findReplaceMode,
                        onQueryChange = vm::onFindQueryChanged,
                        onReplaceChange = vm::onFindReplaceChanged,
                        onNext = vm::findNext,
                        onPrev = vm::findPrev,
                        onReplaceOne = vm::replaceOne,
                        onReplaceAll = vm::replaceAll,
                        onToggleReplaceMode = vm::toggleFindReplaceMode,
                        onDismiss = { vm.showFind(false) }
                    )
                }
                CodeEditorView(
                    file = ui.currentFile,
                    text = ui.editorText,
                    onTextChange = vm::onEditorChanged,
                    fontSize = ui.settings.editorFontSize,
                    language = if (ui.currentFile?.extension == "java")
                        com.devterminal.engine.Language.JAVA
                    else com.devterminal.engine.Language.PYTHON,
                    onCursorChange = vm::onCursorChanged,
                    insertSignal = ui.insertSignal,
                    insertText = ui.insertText,
                    backspaceSignal = ui.backspaceSignal,
                    findRequest = if (ui.findVisible && ui.findMatches.isNotEmpty()) FindRequest(
                        pos = ui.findMatches.getOrNull(ui.findIndex) ?: 0,
                        length = ui.findQuery.length,
                        requestId = ui.findRequestId
                    ) else null,
                    onPinchZoom = { dir ->
                        // 双指捏合调整字号（10–24 sp），实时持久化
                        val next = (ui.settings.editorFontSize + dir).coerceIn(10, 24)
                        if (next != ui.settings.editorFontSize) {
                            vm.updateSettings(ui.settings.copy(editorFontSize = next))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                // 快捷符号栏：手机键盘打符号太费劲
                SymbolBar(
                    onInsert = { vm.insertSymbol(it) },
                    onBackspace = { vm.backspaceSymbol() }
                )
                Divider()
                // 拖拽把手：上下拖调整输出面板高度
                Box(
                    Modifier.fillMaxWidth().height(16.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { vm.persistOutputHeight(outputHeight.roundToInt()) }
                            ) { _, dragAmount ->
                                outputHeight = (outputHeight + dragAmount)
                                    .coerceIn(120f, 560f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(36.dp).height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                            )
                    )
                }
                OutputPanel(
                    output = ui.output,
                    running = ui.running,
                    exitCode = ui.exitCode,
                    inputVisible = ui.inputVisible,
                    inputDraft = ui.inputDraft,
                    onInputChange = vm::onInputDraftChanged,
                    onInputSend = { vm.sendInput(ui.inputDraft) },
                    onClear = vm::clearOutput,
                    onRerun = vm::runCurrent,
                    onShare = {
                        // 通过系统分享把运行结果发出去（聊天/笔记/Issue 都方便）
                        val text = ui.output.joinToString("\n")
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        runCatching {
                            context.startActivity(android.content.Intent.createChooser(intent, "分享运行结果"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(outputHeight.dp)
                )
                // 底部状态栏：光标位置 / 语言 / 保存状态
                StatusBar(
                    line = ui.cursorLine,
                    column = ui.cursorColumn,
                    language = if (ui.currentFile?.extension == "java") "Java" else "Python",
                    charCount = ui.editorText.length,
                    dirty = ui.dirty
                )
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate = { id -> vm.newProject(id); showNewProject = false }
        )
    }

    // ---------- 命令面板：所有动作的搜索入口（v0.3） ----------
    if (showPalette) {
        CommandPalette(
            commands = buildCommands(vm, ui,
                // 触发对话框的动作要在面板关闭后生效（面板点击后已自动 onDismiss）
                onCommand = { cmd -> when (cmd) {
                    "diag" -> showDiagnostics = true
                    "settings" -> showSettings = true
                    "about" -> showAbout = true
                    "newproj" -> showNewProject = true
                    "git" -> { showGit = true; vm.refreshGitStatus() }
                    "ai" -> showAi = true
                    "gsearch" -> showGlobalSearch = true
                    "runconfig" -> showRunConfig = true
                    "export" -> exportLauncher.launch(vm.suggestedExportName())
                } }
            ),
            onDismiss = { showPalette = false }
        )
    }

    if (showGit) {
        GitPanelDialog(
            status = ui.gitStatus,
            busy = ui.gitBusy,
            lastResult = ui.gitLastResult,
            gitInstalled = remember { com.devterminal.engine.GitManager(
                com.devterminal.engine.EnvironmentInstaller(context)
            ).isGitInstalled() },
            gitName = ui.settings.gitUserName,
            gitEmail = ui.settings.gitUserEmail,
            remoteUrl = ui.settings.gitRemoteUrl,
            onConfigChange = vm::onGitConfigChanged,
            onRefresh = vm::refreshGitStatus,
            onInit = vm::gitInit,
            onCommit = vm::gitCommit,
            onPush = vm::gitPush,
            onPull = vm::gitPull,
            onDismiss = { showGit = false }
        )
    }

    if (showAi) {
        AiPanelDialog(
            messages = ui.aiMessages,
            busy = ui.aiBusy,
            configured = ui.settings.aiConfigured,
            inputDraft = ui.aiInputDraft,
            onInputChange = vm::onAiInputChanged,
            onSend = vm::sendAiInput,
            onQuick = vm::aiQuick,
            onDismiss = { showAi = false }
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDialog(
            query = gsearchQuery,
            results = ui.globalResults,
            searching = ui.globalSearching,
            onQueryChange = { gsearchQuery = it },
            onSearch = vm::searchAll,
            onOpen = { hit -> vm.openSearchHit(hit); showGlobalSearch = false },
            onDismiss = { showGlobalSearch = false }
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            diagnostics = ui.diagnostics,
            onRefresh = { vm.runDiagnostics() },
            onDismiss = { showDiagnostics = false }
        )
    }

    if (showRunConfig) {
        RunConfigDialog(
            args = ui.runArgs,
            mainClass = ui.mainClass,
            onArgsChange = vm::onRunArgsChanged,
            onMainClassChange = vm::onMainClassChanged,
            onDismiss = { showRunConfig = false }
        )
    }

    actionTarget?.let { target ->
        FileActionDialog(
            target = target,
            onRename = { newName -> vm.renameFile(target, newName); actionTarget = null },
            onDelete = { vm.deleteFile(target); actionTarget = null },
            onDismiss = { actionTarget = null }
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = ui.settings,
            onToggleDark = { vm.updateSettings(ui.settings.copy(darkTheme = it)) },
            onFontSizeChange = { vm.updateSettings(ui.settings.copy(editorFontSize = it)) },
            onAutoSaveChange = { vm.updateSettings(ui.settings.copy(autoSave = it)) },
            onTimeoutChange = { vm.updateSettings(ui.settings.copy(timeoutSeconds = it)) },
            onAiConfigChange = { url, key, model ->
                vm.updateSettings(ui.settings.copy(aiBaseUrl = url, aiApiKey = key, aiModel = model))
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** 设置页 */
@Composable
private fun SettingsDialog(
    settings: com.devterminal.settings.AppSettings,
    onToggleDark: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onAiConfigChange: (url: String, key: String, model: String) -> Unit,
    onDismiss: () -> Unit
) {
    var aiUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var aiKey by remember { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember { mutableStateOf(settings.aiModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                // 深色主题
                SettingSwitch(
                    label = "深色主题",
                    hint = "关闭后使用浅色配色",
                    checked = settings.darkTheme,
                    onChange = onToggleDark
                )
                Spacer(Modifier.height(12.dp))
                // 自动保存
                SettingSwitch(
                    label = "切换文件时自动保存",
                    hint = "避免忘记保存导致改动丢失",
                    checked = settings.autoSave,
                    onChange = onAutoSaveChange
                )
                Spacer(Modifier.height(16.dp))
                // 编辑器字号
                Text(
                    "编辑器字号：${settings.editorFontSize} sp",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.editorFontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.roundToInt()) },
                    valueRange = 10f..24f,
                    steps = 13
                )
                Spacer(Modifier.height(8.dp))
                // 运行超时
                Text(
                    "运行超时：${settings.timeoutSeconds} 秒",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.timeoutSeconds.toFloat(),
                    onValueChange = { onTimeoutChange(it.roundToInt()) },
                    valueRange = 10f..600f,
                    steps = 58
                )
                Text(
                    "超时后进程会被强制结束，防止死循环耗尽电量。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(16.dp))
                // AI 助手（可选，BYOK）
                Text("AI 助手（可选）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "默认指向本机 Ollama，本地推理即离线可用；不配置不影响其他功能。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = aiUrl,
                    onValueChange = { aiUrl = it },
                    label = { Text("端点 URL（OpenAI 兼容，/v1 结尾）", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = { aiModel = it },
                    label = { Text("模型名", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = aiKey,
                    onValueChange = { aiKey = it },
                    label = { Text("API Key（本地端点可留空）", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
                TextButton(
                    onClick = { onAiConfigChange(aiUrl.trim(), aiKey.trim(), aiModel.trim()) },
                    enabled = aiUrl.isNotBlank()
                ) { Text("保存 AI 配置", fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 关于页：版本与开源许可 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 DevTerminal") },
        text = {
            Column {
                Text("版本 1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "完全离线的安卓编程终端。内置 Python / Java 工具链，" +
                        "无需联网即可编写、运行、调试代码。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(16.dp))
                Text("开源许可", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "SoraEditor — LGPL-2.1（代码编辑器）",
                    "Termux packages — GPL 等（离线工具链）",
                    "Jetpack Compose — Apache-2.0",
                    "AndroidX — Apache-2.0"
                ).forEach {
                    Text(
                        "· $it",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "本应用的离线工具链来自 Termux 项目，遵循其相应许可；" +
                        "SoraEditor 以 LGPL 授权，用户可自行替换该库。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 运行配置：命令行参数 + Java 主类 */
@Composable
private fun RunConfigDialog(
    args: String,
    mainClass: String,
    onArgsChange: (String) -> Unit,
    onMainClassChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行配置") },
        text = {
            Column {
                Text("命令行参数", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = args,
                    onValueChange = onArgsChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如: --name \"hello world\" -v", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                Text("Java 主类（留空自动探测）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = mainClass,
                    onValueChange = onMainClassChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如: com.example.Main", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "参数按空格切分，含空格的参数用双引号包起来。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** 文件操作：重命名 / 删除 */
@Composable
private fun FileActionDialog(
    target: File,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(target.name) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.name, fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
        text = {
            Column {
                Text("重命名", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true
                )
                if (confirmDelete) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "⚠️ 确认删除？此操作不可恢复（目录会连同内容一起删除）。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name != target.name
            ) { Text("重命名") }
        },
        dismissButton = {
            if (confirmDelete) {
                TextButton(onClick = onDelete) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

/** 环境自检结果对话框：展示每个工具是否可用 */
@Composable
private fun DiagnosticsDialog(
    diagnostics: com.devterminal.engine.EnvReport?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("离线环境自检") },
        text = {
            if (diagnostics == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("检测中…", fontSize = 13.sp)
                }
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (diagnostics.prefixReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            null,
                            tint = if (diagnostics.prefixReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (diagnostics.prefixReady) "工具链已安装" else "工具链缺失",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "占用空间：" + com.devterminal.engine.EnvDiagnostics
                            .formatSize(diagnostics.installedSizeBytes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    diagnostics.tools.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                if (t.available) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                null,
                                tint = if (t.available) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(t.name, fontSize = 13.sp)
                                Text(
                                    t.detail,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh) { Text("重新检测") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 工具链缺失时的引导页。
 *
 * 这是用户最可能撞上的失败场景（忘了打包 assets），
 * 与其让他对着报错发呆，不如直接告诉他怎么修。
 */
@Composable
private fun MissingToolchainScreen(
    message: String,
    detail: String?,
    onRetry: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "DevTerminal 完全离线运行，因此 Python / Java 工具链必须提前打进 APK。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(16.dp))
            Text("修复步骤", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            listOf(
                "1. 在手机 Termux 里执行 tools/extract_bootstrap.sh",
                "2. 把生成的 usrtar.zip 拷到开发机",
                "3. 放到 app/src/main/assets/usrtar.zip",
                "4. 重新构建并安装 APK"
            ).forEach { step ->
                Text(
                    step,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            detail?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

/**
 * 组装命令面板的动作列表。
 * 纯 UI 动作直接执行；打开对话框的通过 [onCommand] 回调交给调用方置位状态。
 */
private fun buildCommands(
    vm: EditorViewModel,
    ui: UiState,
    onCommand: (String) -> Unit
): List<Command> = buildList {
    add(Command("run", "运行当前文件", ui.currentFile?.name ?: "未打开文件",
        Icons.Filled.PlayArrow) { vm.runCurrent() })
    add(Command("save", "保存当前文件", "Ctrl-S 的移动版",
        Icons.Filled.Save) { vm.save() })
    add(Command("find", "查找 / 替换（当前文件）", "计数跳转 + 单个/全部替换",
        Icons.Filled.Search) { vm.showFind(true) })
    add(Command("gsearch", "全局搜索", "在所有项目文件中查找",
        Icons.Filled.Search) { onCommand("gsearch") })
    add(Command("git", "Git 仓库", "状态 / 提交 / 推送 / 拉取",
        Icons.Filled.AccountTree) { onCommand("git") })
    add(Command("ai", "AI 助手", "解释代码 / 修复报错 / 生成测试",
        Icons.Filled.AutoAwesome) { onCommand("ai") })
    add(Command("runconfig", "运行配置", "命令行参数 / Java 主类",
        Icons.Filled.Tune) { onCommand("runconfig") })
    add(Command("newproj", "新建项目", "6 个模板",
        Icons.Filled.Add) { onCommand("newproj") })
    add(Command("export", "导出当前文件", "通过 SAF 写到手机任意位置",
        Icons.Filled.FileUpload) { onCommand("export") })
    add(Command("diag", "环境自检", "Python / pip / Java / javac / Git",
        Icons.Filled.Info) { onCommand("diag") })
    add(Command("settings", "设置", "主题 / 字号 / 超时 / AI 端点",
        Icons.Filled.Settings) { onCommand("settings") })
    add(Command("about", "关于 DevTerminal", "版本与开源许可",
        Icons.Filled.Info) { onCommand("about") })
    // 代码片段：手写麻烦、复用率高的骨架代码
    com.devterminal.engine.Snippets.all.forEach { s ->
        add(Command(
            "snip-${s.id}",
            "插入片段：${s.title}",
            s.description,
            Icons.Filled.Code
        ) { vm.insertSymbol(s.code) })
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目") },
        text = {
            Column {
                Templates.all.forEach { tpl ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { onCreate(tpl.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Code, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(tpl.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(tpl.description, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
