package com.devterminal.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.devterminal.engine.Language as DevLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

/**
 * 基于 SoraEditor 的代码编辑器（经 AndroidView 桥接进 Compose）。
 *
 * SoraEditor 为 LGPL-2.1：本项目通过其公开 API 调用，源码开源即可满足合规；
 * 若将来闭源分发，需保证用户可替换该库（动态链接 + 提供可重链接的目标文件）。
 *
 * 补全实现要点：sora-editor 的补全由 **Language** 提供，
 * 因此用 [StaticCompletionLanguage] 包一层 TextMate 语言（保留高亮 + 静态补全）。
 *
 * v0.2 新增：
 * - [onCursorChange] 光标行列回调（供状态栏显示）
 * - [insertSignal]/[insertText] 符号栏插入（走 Content.insert，进撤销栈）
 * - [backspaceSignal] 符号栏退格（模拟 KEYCODE_DEL，兼容 IME）
 * - [findRequest] 查找跳转（setSelection + ensureSelectionVisible）
 */
@Composable
fun CodeEditorView(
    file: File?,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    fontSize: Int = 14,
    language: DevLanguage = DevLanguage.PYTHON,
    onCursorChange: (Int, Int) -> Unit = { _, _ -> },
    /** 每次自增触发一次插入，insertText 为要插入的内容 */
    insertSignal: Long = 0,
    insertText: String? = null,
    /** 每次自增触发一次退格 */
    backspaceSignal: Long = 0,
    /** 查找跳转请求：pos 为全局字符偏移，requestId 变化即执行 */
    findRequest: FindRequest? = null
) {
    val context = LocalContext.current

    if (file == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "从左侧选择一个文件开始编辑",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val editor = remember { CodeEditor(context) }
    // 全局只需初始化一次语法/主题注册表
    remember { initTextMate(context.assets) }
    // 让补全能读到当前文档；用可变引用避免闭包捕获旧值
    val docRef = remember { java.util.concurrent.atomic.AtomicReference(text) }
    val cursorCallback = remember { onCursorChange }

    // 符号栏插入：直接走编辑器内部 Content.insert，能正确进撤销栈
    LaunchedEffect(insertSignal) {
        if (insertSignal <= 0) return@LaunchedEffect
        val toInsert = insertText ?: return@LaunchedEffect
        runCatching {
            val cursor = editor.cursor
            editor.text.insert(cursor.leftLine, cursor.leftColumn, toInsert)
        }
    }

    // 符号栏退格：模拟删除键事件，编辑器自己处理选中/删除逻辑
    LaunchedEffect(backspaceSignal) {
        if (backspaceSignal <= 0) return@LaunchedEffect
        runCatching {
            editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }
    }

    // 查找跳转：把全局偏移换算成行列后移动光标并滚动到可见
    LaunchedEffect(findRequest?.requestId) {
        val req = findRequest ?: return@LaunchedEffect
        runCatching {
            val content = editor.text?.toString() ?: return@LaunchedEffect
            val pos = req.pos.coerceIn(0, content.length)
            val line = content.substring(0, pos).count { it == '\n' }
            val col = if (line == 0) pos
                      else pos - (content.lastIndexOf('\n', pos - 1) + 1)
            runCatching { editor.setSelection(line, col) }
            runCatching {
                editor.ensureSelectionVisible()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            editor.apply {
                setText(text)
                isEditable = !readOnly
                applyTheme()
                setTextSize(fontSize.toFloat())

                // 装配语言：TextMate 高亮 + 静态补全
                runCatching {
                    val textMate = TextMateLanguage.create(file.extension)
                    val wrapped = StaticCompletionLanguage(
                        delegate = textMate,
                        devLanguage = language,
                        documentSupplier = { docRef.get() }
                    )
                    setEditorLanguage(wrapped)
                }

                // 启用补全组件（getter 是 getComponent）
                runCatching {
                    getComponent(EditorAutoCompletion::class.java).isEnabled = true
                }

                subscribeEvent(object : ContentChangeEvent.Subscriber() {
                    override fun onEvent(
                        event: ContentChangeEvent,
                        dispatcher: io.github.rosemoe.sora.event.EventDispatcher
                    ) {
                        val current = editor.text?.toString() ?: return
                        docRef.set(current)
                        onTextChange(current)
                    }
                })

                // 光标/选区变化 → 状态栏的行列显示
                subscribeEvent(object : SelectionChangeEvent.Subscriber() {
                    override fun onEvent(
                        event: SelectionChangeEvent,
                        dispatcher: io.github.rosemoe.sora.event.EventDispatcher
                    ) {
                        val line = runCatching { event.leftLine }.getOrDefault(0)
                        val column = runCatching { event.leftColumn }.getOrDefault(0)
                        cursorCallback(line, column)
                    }
                })
            }
        },
        update = { view ->
            docRef.set(text)
            // 字号实时跟随设置
            if (view.textSize != fontSize.toFloat()) view.setTextSize(fontSize.toFloat())
            // 仅当外部内容真的变化时才 setText，避免打断输入
            if (view.text?.toString() != text) {
                val cursor = runCatching { view.cursor.left }.getOrDefault(0)
                view.setText(text)
                runCatching { view.setSelection(cursor.coerceIn(0, text.length)) }
            }
        },
        onRelease = { it.release() }
    )
}

private var textMateReady = false

private fun initTextMate(assets: android.content.res.AssetManager) {
    if (textMateReady) return
    runCatching {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        ThemeRegistry.getInstance().loadTheme(ThemeModel(assets, "textmate/themes/darcula.json"))
        textMateReady = true
    }
}

private fun CodeEditor.applyTheme() {
    runCatching {
        val scheme = ThemeRegistry.getInstance().theme
            ?.let { TextMateColorScheme.create(ThemeRegistry.getInstance()) }
        colorScheme = scheme ?: EditorColorScheme()
    }.onFailure { colorScheme = EditorColorScheme() }
}
