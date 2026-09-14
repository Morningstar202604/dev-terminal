package com.devterminal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devterminal.engine.EnvDiagnostics
import com.devterminal.engine.EnvironmentInstaller
import com.devterminal.engine.ExecutionService
import com.devterminal.engine.FriendlyError
import com.devterminal.engine.Language
import com.devterminal.engine.RunEvent
import com.devterminal.engine.RunRequest
import com.devterminal.engine.TermuxEngine
import com.devterminal.project.FileNode
import com.devterminal.project.ProjectManager
import com.devterminal.project.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EnvState { PREPARING, READY, ERROR }

data class UiState(
    val envState: EnvState = EnvState.PREPARING,
    val envMessage: String = "正在准备离线运行环境…",
    val tree: FileNode? = null,
    val currentFile: File? = null,
    val editorText: String = "",
    val dirty: Boolean = false,
    val output: List<String> = emptyList(),
    val running: Boolean = false,
    val exitCode: Int? = null,
    val message: String? = null,
    /** 是否显示交互输入行（运行中且进程可接收输入） */
    val inputVisible: Boolean = false,
    /** 用户待发送的输入内容 */
    val inputDraft: String = "",
    /** 环境自检结果（就绪后可查看） */
    val diagnostics: EnvDiagnostics? = null,
    /** 根据报错生成的友好提示 */
    val friendlyHint: String? = null,
    /** 解压进度 0..1，-1 表示不确定 */
    val installProgress: Float = -1f,
    /** 运行配置：命令行参数（空格分隔） */
    val runArgs: String = "",
    /** 运行配置：Java 主类全限定名（留空=自动探测） */
    val mainClass: String = "",
    /** 用户设置 */
    val settings: com.devterminal.settings.AppSettings = com.devterminal.settings.AppSettings(),
    /** 顶部打开的文件 Tab 列表 */
    val openTabs: List<File> = emptyList(),
    // ---------- 查找 / 替换 ----------
    val findVisible: Boolean = false,
    val findQuery: String = "",
    val findReplaceWith: String = "",
    val findReplaceMode: Boolean = false,
    /** 所有匹配的全局字符偏移 */
    val findMatches: List<Int> = emptyList(),
    /** 当前停在的匹配序号（findMatches 下标） */
    val findIndex: Int = 0,
    /** 跳转请求序号，每次自增触发编辑器执行 */
    val findRequestId: Long = 0,
    // ---------- 状态栏 ----------
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    // ---------- 符号栏 ----------
    /** 每次自增插入一次 insertText */
    val insertSignal: Long = 0,
    val insertText: String? = null,
    /** 每次自增退格一次 */
    val backspaceSignal: Long = 0
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val installer = EnvironmentInstaller(context)
    private val engine = TermuxEngine(context)
    private val projects = ProjectManager(installer)
    private val settingsStore = com.devterminal.settings.SettingsStore(context)

    private val _ui = MutableStateFlow(UiState(settings = settingsStore.load()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** 当前项目根目录（默认取第一个项目） */
    private var currentProject: File? = null

    init {
        prepareEnvironment()
    }

    private fun prepareEnvironment() {
        viewModelScope.launch {
            // 解压离线工具链可能耗时，放到 IO 线程并更新进度
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    installer.ensureDirs()
                    engine.prepareEnvironment { done, total ->
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        _ui.value = _ui.value.copy(
                            envMessage = "正在解压离线工具链… $pct%",
                            installProgress = if (total > 0) done.toFloat() / total else -1f
                        )
                    }
                }.isSuccess
            }
            if (!ok) {
                _ui.value = _ui.value.copy(
                    envState = EnvState.ERROR,
                    envMessage = "离线工具链解压失败",
                    message = "请检查 assets/usrtar.zip 是否已打包"
                )
                return@launch
            }
            if (engine.isEnvironmentReady) {
                _ui.value = _ui.value.copy(
                    envState = EnvState.READY,
                    envMessage = "离线环境就绪",
                    installProgress = -1f,
                    output = listOf("[DevTerminal] 离线运行环境已就绪，全程无需联网。")
                )
                restoreSession()
                // 后台跑一次自检，不阻塞用户开始写代码
                runDiagnostics()
            } else {
                _ui.value = _ui.value.copy(
                    envState = EnvState.ERROR,
                    envMessage = "未找到内置工具链",
                    installProgress = -1f,
                    message = "缺少 assets/usrtar.zip，请先按 README 生成离线工具链"
                )
            }
        }
    }

    /** 重新尝试初始化环境（用户在引导页点「重试」时调用） */
    fun retryPrepare() {
        _ui.value = _ui.value.copy(
            envState = EnvState.PREPARING,
            envMessage = "正在重新检查离线环境…",
            installProgress = -1f,
            message = null
        )
        prepareEnvironment()
    }

    /** 后台执行环境自检并把结果推给 UI */
    fun runDiagnostics() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { EnvDiagnostics.run(installer) }
            _ui.value = _ui.value.copy(
                diagnostics = result,
                message = result.summary() + "（" + EnvDiagnostics.formatSize(result.installedSizeBytes) + "）"
            )
        }
    }

    // ---------- 会话恢复：重启后回到上次的编辑现场 ----------

    /**
     * 恢复上次会话：上次项目 → 上次打开的 Tab 列表 → 上次激活的文件。
     * 全部失效时回退到默认项目（首个项目或新建模板项目）。
     */
    private fun restoreSession() {
        viewModelScope.launch {
            val snap = settingsStore.loadSession()
            val defaultProject = withContext(Dispatchers.IO) {
                projects.ensureInitialized()
                projects.listProjects().firstOrNull()
                    ?: projects.createFromTemplate(Templates.PYTHON_HELLO)
            }
            val projectDir = snap.projectPath?.let { File(it) }
                ?.takeIf { it.isDirectory && it.parentFile == projects.root }
                ?: defaultProject
            currentProject = projectDir
            refreshTree()

            // 恢复 Tab 列表（剔除已不存在的文件）
            val tabs = snap.tabPaths.map { File(it) }.filter { it.isFile }
            val active = snap.filePath?.let { File(it) }?.takeIf { it.isFile && tabs.contains(it) }
                ?: tabs.firstOrNull()
            _ui.value = _ui.value.copy(openTabs = tabs)
            if (active != null) {
                doOpenFile(active, restore = true)
            }
        }
    }

    /** 把当前编辑现场写进偏好，App 被杀后也能回来 */
    private fun saveSession() {
        val st = _ui.value
        settingsStore.saveSession(
            projectPath = currentProject?.absolutePath,
            filePath = st.currentFile?.absolutePath,
            tabPaths = st.openTabs.map { it.absolutePath }
        )
    }

    // ---------- 项目 / 文件 ----------

    fun openProject(dir: File) {
        currentProject = dir
        refreshTree()
        saveSession()
    }

    private fun refreshTree() {
        val dir = currentProject ?: return
        // 递归遍历目录属于阻塞 IO，放到后台线程
        viewModelScope.launch {
            val tree = withContext(Dispatchers.IO) { projects.buildTree(dir) }
            _ui.value = _ui.value.copy(tree = tree)
        }
    }

    fun openFile(file: File) {
        if (file.isDirectory) return
        val st = _ui.value
        // 自动保存：切走前先落盘，避免改动丢失
        if (st.settings.autoSave && st.dirty && st.currentFile != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    projects.write(st.currentFile!!.absolutePath, st.editorText)
                }
                doOpenFile(file)
            }
        } else {
            doOpenFile(file)
        }
    }

    private fun doOpenFile(file: File, restore: Boolean = false) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { projects.read(file.absolutePath) }
            _ui.value = _ui.value.copy(
                currentFile = file,
                editorText = content,
                dirty = false,
                // 打开新文件自动挂到 Tab 条上（已打开的不重复加）
                openTabs = _ui.value.openTabs.let { tabs ->
                    if (tabs.any { it.absolutePath == file.absolutePath }) tabs else tabs + file
                }
            )
            if (!restore) saveSession()
        }
    }

    /** 关闭一个 Tab；若关的是当前文件则自动切到相邻 Tab */
    fun closeTab(file: File) {
        val st = _ui.value
        val idx = st.openTabs.indexOfFirst { it.absolutePath == file.absolutePath }
        if (idx < 0) return
        val newTabs = st.openTabs.filterNot { it.absolutePath == file.absolutePath }
        if (st.currentFile?.absolutePath == file.absolutePath) {
            // 保存后切到相邻 Tab（优先右边，其次左边），没有就清空编辑器
            val next = newTabs.getOrNull(idx) ?: newTabs.getOrNull(idx - 1)
            _ui.value = st.copy(openTabs = newTabs)
            if (next != null) {
                doOpenFile(next)
            } else {
                _ui.value = _ui.value.copy(currentFile = null, editorText = "", dirty = false)
                saveSession()
            }
        } else {
            _ui.value = st.copy(openTabs = newTabs)
            saveSession()
        }
    }

    fun onEditorChanged(newText: String) {
        val st = _ui.value
        if (st.editorText == newText) return
        _ui.value = st.copy(editorText = newText, dirty = true)
    }

    fun save() {
        val st = _ui.value
        val file = st.currentFile ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { projects.write(file.absolutePath, st.editorText) }
            _ui.value = st.copy(dirty = false, message = "已保存 ${file.name}")
        }
    }

    // ---------- 运行 ----------

    fun runCurrent() {
        val st = _ui.value
        // 防止重复点击导致多个进程并发、输出互相穿插
        if (st.running) return
        val file = st.currentFile ?: run {
            _ui.value = st.copy(message = "请先打开一个文件")
            return
        }
        // 运行前自动保存，保证跑的是最新代码
        if (st.dirty) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { projects.write(file.absolutePath, st.editorText) }
                _ui.value = _ui.value.copy(dirty = false)
                launchRun(file)
            }
        } else {
            launchRun(file)
        }
    }

    private fun launchRun(file: File) {
        val language = inferLanguage(file)
        val projectDir = currentProject ?: file.parentFile ?: file
        _ui.value = _ui.value.copy(
            running = true, output = emptyList(), exitCode = null,
            inputVisible = true, inputDraft = "", friendlyHint = null,
            message = "运行 ${file.name} …"
        )
        // 提升为前台服务，避免 Android 12+ 在后台把进程杀掉
        runCatching { ExecutionService.start(context, "正在运行 ${file.name}") }
        viewModelScope.launch {
            engine.run(
                RunRequest(
                    scriptPath = file.absolutePath,
                    workingDir = projectDir.absolutePath,
                    language = language,
                    // 支持带引号的参数，如: --name "hello world"
                    args = parseArgs(_ui.value.runArgs),
                    mainClass = _ui.value.mainClass.takeIf { it.isNotBlank() }
                ),
                // 超时时长来自用户设置
                timeoutMs = _ui.value.settings.timeoutSeconds * 1000L
            ).collect { event ->
                val st = _ui.value
                _ui.value = when (event) {
                    is RunEvent.Started -> st.copy(
                        output = st.output + "[执行] ${event.command}"
                    )
                    is RunEvent.Stdout -> st.copy(output = st.output + event.line)
                    is RunEvent.Stderr -> st.copy(output = st.output + "[err] ${event.line}")
                    is RunEvent.Finished -> {
                        // 非零退出时，尝试把报错翻译成人话
                        val hint = if (event.exitCode != 0) FriendlyError.hint(st.output) else null
                        st.copy(
                            running = false,
                            exitCode = event.exitCode,
                            inputVisible = false,
                            friendlyHint = hint,
                            output = st.output +
                                "—— 进程结束，退出码 ${event.exitCode}（${event.durationMs} ms）——" +
                                (hint?.let { listOf("", "💡 $it") } ?: emptyList()),
                            message = if (event.exitCode == 0) "运行成功" else "运行出错，见下方提示"
                        )
                    }
                    is RunEvent.Failed -> {
                        val hint = FriendlyError.hint(listOf(event.message))
                        st.copy(
                            running = false,
                            inputVisible = false,
                            output = st.output + "[启动失败] ${event.message}" +
                                (hint?.let { listOf("", "💡 $it") } ?: emptyList()),
                            friendlyHint = hint,
                            message = event.message
                        )
                    }
                }
            }
            // 运行结束，撤掉前台服务（避免通知栏常驻）
            runCatching { ExecutionService.stop(context) }
        }
    }

    /** 向运行中的进程发送一行输入（支持 Python 的 input() 交互） */
    fun sendInput(text: String) {
        val st = _ui.value
        if (!st.running) return
        if (engine.writeStdin(text)) {
            // 把用户输入回显到输出，模仿终端行为
            _ui.value = st.copy(output = st.output + text, inputDraft = "")
        } else {
            _ui.value = st.copy(message = "当前进程不接受输入")
        }
    }

    /** 发送 EOF（等价 Ctrl-D） */
    fun sendEof() {
        if (!_ui.value.running) return
        engine.closeStdin()
        _ui.value = _ui.value.copy(message = "已发送 EOF")
    }

    fun stopRun() {
        engine.stop()
        runCatching { ExecutionService.stop(context) }
        _ui.value = _ui.value.copy(running = false, inputVisible = false, message = "已停止运行")
    }

    fun onInputDraftChanged(text: String) {
        _ui.value = _ui.value.copy(inputDraft = text)
    }

    fun onRunArgsChanged(text: String) {
        _ui.value = _ui.value.copy(runArgs = text)
    }

    fun onMainClassChanged(text: String) {
        _ui.value = _ui.value.copy(mainClass = text)
    }

    /**
     * 解析命令行参数。支持双引号包裹含空格的参数：
     *   --name "hello world" -v  →  ["--name", "hello world", "-v"]
     */
    private fun parseArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        raw.forEach { c ->
            when {
                c == '"' -> inQuote = !inQuote
                c == ' ' && !inQuote -> {
                    if (sb.isNotEmpty()) { result.add(sb.toString()); sb.clear() }
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }

    fun newProject(templateId: String) {
        val tpl = Templates.all.firstOrNull { it.id == templateId } ?: Templates.PYTHON_HELLO
        viewModelScope.launch {
            val dir = withContext(Dispatchers.IO) { projects.createFromTemplate(tpl) }
            openProject(dir)
            _ui.value = _ui.value.copy(
                openTabs = emptyList(),
                message = "已创建项目 ${dir.name}"
            )
            // 新项目自动打开主文件
            val main = withContext(Dispatchers.IO) {
                dir.walkTopDown().filter { it.isFile && it.extension in listOf("py", "java") }.firstOrNull()
            }
            if (main != null) doOpenFile(main)
        }
    }

    fun newFile(fileName: String) {
        val dir = currentProject ?: return
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { projects.createFile(dir.absolutePath, fileName) }
            refreshTree()
            openFile(f)
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { projects.delete(file) }
            val st = _ui.value
            if (st.currentFile?.path == file.path) {
                _ui.value = st.copy(currentFile = null, editorText = "")
            }
            // 从 Tab 条同步移除
            _ui.value = _ui.value.copy(
                openTabs = _ui.value.openTabs.filterNot { it.absolutePath == file.absolutePath }
            )
            refreshTree()
            saveSession()
            _ui.value = _ui.value.copy(message = "已删除 ${file.name}")
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { projects.rename(file, newName) }
            if (renamed == null) {
                _ui.value = _ui.value.copy(message = "重命名失败：名称已存在或非法")
            } else {
                val st = _ui.value
                if (st.currentFile?.path == file.path) {
                    doOpenFile(renamed)
                }
                // 同步 Tab 列表里的旧路径
                _ui.value = _ui.value.copy(
                    openTabs = _ui.value.openTabs.map {
                        if (it.absolutePath == file.absolutePath) renamed else it
                    }
                )
                refreshTree()
                saveSession()
                _ui.value = _ui.value.copy(message = "已重命名为 ${renamed.name}")
            }
        }
    }

    fun clearOutput() { _ui.value = _ui.value.copy(output = emptyList(), exitCode = null, friendlyHint = null) }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }

    /** 更新设置并持久化 */
    fun updateSettings(newSettings: com.devterminal.settings.AppSettings) {
        settingsStore.save(newSettings)
        _ui.value = _ui.value.copy(settings = newSettings)
    }

    /** 拖拽结束后记录输出面板高度（拖动过程走 UI 本地状态，结束才落盘） */
    fun persistOutputHeight(dp: Int) {
        val s = _ui.value.settings
        if (s.outputHeightDp != dp) updateSettings(s.copy(outputHeightDp = dp))
    }

    // ---------- 状态栏 ----------

    /** 编辑器光标变化（行、列，从 0 计） */
    fun onCursorChanged(line: Int, column: Int) {
        _ui.value = _ui.value.copy(cursorLine = line + 1, cursorColumn = column + 1)
    }

    // ---------- 符号栏 ----------

    /** 在光标处插入一段文本（符号 / Tab） */
    fun insertSymbol(text: String) {
        val st = _ui.value
        _ui.value = st.copy(insertSignal = st.insertSignal + 1, insertText = text)
    }

    /** 光标处退格 */
    fun backspaceSymbol() {
        val st = _ui.value
        _ui.value = st.copy(backspaceSignal = st.backspaceSignal + 1)
    }

    // ---------- 查找 / 替换 ----------

    fun showFind(show: Boolean) {
        _ui.value = _ui.value.copy(findVisible = show)
        if (!show) {
            _ui.value = _ui.value.copy(
                findVisible = false, findMatches = emptyList(), findIndex = 0, findQuery = ""
            )
        }
    }

    fun onFindQueryChanged(query: String) {
        val st = _ui.value
        val matches = computeMatches(st.editorText, query)
        _ui.value = st.copy(
            findQuery = query,
            findMatches = matches,
            findIndex = 0,
            findRequestId = if (matches.isNotEmpty()) st.findRequestId + 1 else st.findRequestId
        )
    }

    fun onFindReplaceChanged(text: String) {
        _ui.value = _ui.value.copy(findReplaceWith = text)
    }

    fun toggleFindReplaceMode() {
        _ui.value = _ui.value.copy(findReplaceMode = !_ui.value.findReplaceMode)
    }

    fun findNext() = stepFind(1)
    fun findPrev() = stepFind(-1)

    private fun stepFind(delta: Int) {
        val st = _ui.value
        if (st.findMatches.isEmpty()) return
        val idx = ((st.findIndex + delta) % st.findMatches.size + st.findMatches.size) % st.findMatches.size
        _ui.value = st.copy(findIndex = idx, findRequestId = st.findRequestId + 1)
    }

    fun replaceOne() {
        val st = _ui.value
        val content = st.editorText
        val pos = st.findMatches.getOrNull(st.findIndex) ?: return
        val end = pos + st.findQuery.length
        if (end > content.length) return
        val newText = content.substring(0, pos) + st.findReplaceWith + content.substring(end)
        val matches = computeMatches(newText, st.findQuery)
        _ui.value = st.copy(
            editorText = newText,
            dirty = true,
            findMatches = matches,
            findIndex = if (matches.isEmpty()) 0 else (st.findIndex % matches.size)
        )
    }

    fun replaceAll() {
        val st = _ui.value
        if (st.findQuery.isEmpty()) return
        val newText = st.editorText.replace(st.findQuery, st.findReplaceWith)
        _ui.value = st.copy(
            editorText = newText,
            dirty = true,
            findMatches = computeMatches(newText, st.findQuery),
            findIndex = 0
        )
    }

    /** 当前查找状态的匹配信息，如「2/5」；无可匹配时为 null */
    fun findMatchInfo(st: UiState): String? =
        if (st.findMatches.isEmpty()) null
        else "${st.findIndex + 1}/${st.findMatches.size}"

    private fun computeMatches(content: String, query: String): List<Int> {
        if (query.isEmpty() || content.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        var idx = content.indexOf(query)
        while (idx >= 0 && result.size < 500) {
            result.add(idx)
            idx = content.indexOf(query, idx + query.length)
        }
        return result
    }

    // ---------- 导入 / 导出（SAF） ----------

    /**
     * 把用户通过 SAF 选中的文件导入到当前项目。
     * @param uri SAF 返回的内容 URI
     */
    fun importFile(uri: android.net.Uri) {
        val dir = currentProject ?: run {
            _ui.value = _ui.value.copy(message = "没有打开的项目")
            return
        }
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    // 从 URI 推断原始文件名
                    val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.txt"
                    val target = File(dir, displayName)
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("无法读取所选文件")
                    displayName
                }.getOrElse { e ->
                    _ui.value = _ui.value.copy(message = "导入失败：${e.message}")
                    return@withContext null
                }
            }
            if (name != null) {
                refreshTree()
                // 自动打开刚导入的文件
                File(dir, name).takeIf { it.exists() }?.let { openFile(it) }
                _ui.value = _ui.value.copy(message = "已导入 $name")
            }
        }
    }

    /**
     * 把当前文件内容写入用户通过 SAF 选择的目标位置。
     * @param uri 用户选择的目标文件 URI
     */
    fun exportCurrentFile(uri: android.net.Uri) {
        val st = _ui.value
        val file = st.currentFile ?: run {
            _ui.value = st.copy(message = "没有打开的文件")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    // 先落盘，保证导出的是最新内容
                    if (st.dirty) projects.write(file.absolutePath, st.editorText)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(st.editorText.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入目标位置")
                }.onSuccess {
                    _ui.value = _ui.value.copy(dirty = false, message = "已导出 ${file.name}")
                }.onFailure { e ->
                    _ui.value = _ui.value.copy(message = "导出失败：${e.message}")
                }
            }
        }
    }

    /** 从 SAF 的 content URI 查询显示文件名 */
    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    /** 导出的默认文件名（供 UI 预填） */
    fun suggestedExportName(): String =
        _ui.value.currentFile?.name ?: "untitled.py"

    private fun inferLanguage(file: File): Language =
        if (file.extension == "java") Language.JAVA else Language.PYTHON
}
