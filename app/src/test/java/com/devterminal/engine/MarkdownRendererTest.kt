package com.devterminal.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** MarkdownRenderer：GFM 渲染与页面壳的纯 JVM 测试 */
class MarkdownRendererTest {

    @Test
    fun `标题与加粗正确转成 HTML`() {
        val html = MarkdownRenderer.renderMarkdown("# 标题\n\n**重点**内容")
        assertTrue(html.contains("<h1"))
        assertTrue(html.contains("标题"))
        assertTrue(html.contains("<strong>重点</strong>"))
    }

    @Test
    fun `GFM 表格被解析为 table`() {
        val html = MarkdownRenderer.renderMarkdown("| a | b |\n|---|---|\n| 1 | 2 |")
        assertTrue(html.contains("<table"))
        assertTrue(html.contains("<td>2</td>"))
    }

    @Test
    fun `代码块原样保留且被转义`() {
        val html = MarkdownRenderer.renderMarkdown("```\nval x = \"<script>\"\n```")
        assertTrue(html.contains("<pre"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `页面壳包含移动端 viewport 与暗色背景`() {
        val page = MarkdownRenderer.pageForMarkdown("# hi", dark = true)
        assertTrue(page.contains("viewport"))
        assertTrue(page.contains("#1e1f22"))
        assertTrue(page.contains("<h1"))
    }

    @Test
    fun `HTML 文件直通渲染`() {
        val page = MarkdownRenderer.pageForHtml("<p>原始 <b>HTML</b></p>", dark = false)
        assertTrue(page.contains("原始 <b>HTML</b>"))
        assertTrue(page.contains("#fafafa"))
    }

    @Test
    fun `畸形输入不抛异常且不产生空页面`() {
        val page = MarkdownRenderer.pageForMarkdown("\u0000\u0001 ###未闭合 [链接(", dark = true)
        assertTrue(page.contains("<body>"))
    }
}
