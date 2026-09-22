package com.devterminal.engine

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown / HTML 预览渲染（纯 JVM，离线）。
 *
 * 为什么选 org.jetbrains:markdown：IntelliJ 平台同款解析器，纯 Kotlin 无 Android 依赖，
 * 自带 GFM 方言（表格 / 删除线 / 任务列表），一个 jar 约 500KB——
 * 相比在 JS 侧 vendor 一个 marked.js，依赖更少且能直接进单元测试。
 */
object MarkdownRenderer {

    /** 超大文件的渲染保护：只渲染前 500K 字符，其余以提示代替 */
    private const val MAX_CHARS = 500_000

    private val flavour = GFMFlavourDescriptor()

    /** Markdown → HTML 片段（GFM：表格、删除线、任务列表、自动链接） */
    fun renderMarkdown(md: String): String {
        val text = if (md.length > MAX_CHARS) {
            md.take(MAX_CHARS) + "\n\n> ⚠ 文件过大，仅渲染前 ${MAX_CHARS / 1000}K 字符"
        } else md
        return runCatching {
            val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
            HtmlGenerator(text, tree, flavour).generateHtml()
        }.getOrElse {
            // 极端情况（解析器对畸形输入抛错）也不至于白屏
            "<pre>${xmlEscape(text.take(2000))}</pre>"
        }
    }

    /** 完整预览页：Markdown 文件入口 */
    fun pageForMarkdown(md: String, dark: Boolean): String =
        wrapBody(renderMarkdown(md).replace("</p>\n<p>", "</p>\n<p>\n"), dark)

    /** 完整预览页：HTML 文件直接展示原文（注入自适应样式，不执行用户脚本以外的注入） */
    fun pageForHtml(html: String, dark: Boolean): String {
        val body = if (html.length > MAX_CHARS) {
            html.take(MAX_CHARS) + "<p>⚠ 文件过大，仅渲染前 ${MAX_CHARS / 1000}K 字符</p>"
        } else html
        return wrapBody(body, dark, styleExtra = false)
    }

    /** 统一的页面壳：适配手机宽度与明暗背景，代码块/表格可读性 */
    private fun wrapBody(inner: String, dark: Boolean, styleExtra: Boolean = true): String {
        val bg = if (dark) "#1e1f22" else "#fafafa"
        val fg = if (dark) "#d4d4d4" else "#24292f"
        val codeBg = if (dark) "#2a2b2f" else "#f0f1f3"
        val border = if (dark) "#3a3b3f" else "#d8dadc"
        val muted = if (dark) "#9aa0a6" else "#656d76"
        val extra = if (styleExtra) {
            // Markdown 页：图片与代码块自适应 + 引用块样式
            """img{max-width:100%;height:auto}
               pre{background:$codeBg;padding:10px 12px;border-radius:8px;overflow-x:auto}
               code{background:$codeBg;padding:1px 5px;border-radius:4px;font-size:0.9em}
               pre code{background:none;padding:0}
               blockquote{border-left:3px solid $border;margin:0.6em 0;padding:0.1em 0 0.1em 0.8em;color:$muted}
               table{border-collapse:collapse}th,td{border:1px solid $border;padding:5px 9px}
               hr{border:none;border-top:1px solid $border;margin:1.2em 0}"""
        } else {
            "img{max-width:100%;height:auto}"
        }
        return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body{background:$bg;color:$fg;font-family:-apple-system,"Segoe UI",Roboto,"Noto Sans SC",sans-serif;
       font-size:15px;line-height:1.65;margin:0;padding:14px 16px;word-break:break-word}
  a{color:#4d9fff}
  h1,h2,h3,h4{line-height:1.3;margin:1.1em 0 0.5em}
  $extra
</style></head>
<body>$inner</body></html>"""
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
