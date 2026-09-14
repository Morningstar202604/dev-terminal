package com.devterminal.project

import com.devterminal.engine.EnvironmentInstaller
import java.io.File

/** 文件树节点 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList()
)

/**
 * 项目文件管理。
 *
 * 项目统一放在 App 私有目录 files/projects 下——Android 10+ 分区存储下，
 * 私有目录免授权即可读写，最稳妥；需要访问手机 Download 时再走 SAF。
 */
class ProjectManager(installer: EnvironmentInstaller) {

    val root: File = installer.projectsDir

    /** 确保根目录存在，并在为空时创建一个示例项目。调用方应在 IO 线程执行。 */
    fun ensureInitialized() {
        root.mkdirs()
        if (root.listFiles().isNullOrEmpty()) {
            createFromTemplate(Templates.PYTHON_HELLO)
        }
    }

    fun listProjects(): List<File> =
        root.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()

    /** 以模板新建项目，返回项目根目录 */
    fun createFromTemplate(template: ProjectTemplate, projectName: String? = null): File {
        val name = sanitize(projectName ?: template.id)
        var dir = File(root, name)
        var i = 1
        while (dir.exists()) { dir = File(root, "$name-${i++}") }
        dir.mkdirs()
        template.files.forEach { (rel, content) ->
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            f.writeText(content)
        }
        // 记录语言，便于 UI 选择运行配置
        File(dir, ".devterminal").writeText(template.language.name)
        return dir
    }

    /** 递归构建文件树（隐藏以 . 开头的内部文件） */
    fun buildTree(dir: File): FileNode {
        val children = dir.listFiles()
            ?.filterNot { it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { buildTree(it) }
            ?: emptyList()
        return FileNode(dir.name, dir.absolutePath, dir.isDirectory, children)
    }

    fun read(path: String): String = File(path).takeIf { it.exists() }?.readText() ?: ""

    fun write(path: String, content: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    fun createFile(parentDir: String, fileName: String): File {
        val f = File(parentDir, sanitize(fileName))
        f.parentFile?.mkdirs()
        if (!f.exists()) f.createNewFile()
        return f
    }

    fun delete(target: File): Boolean = target.deleteRecursively()

    /** 重命名文件/目录，返回新文件（失败返回 null） */
    fun rename(target: File, newName: String): File? {
        val safe = sanitize(newName)
        val dest = File(target.parentFile ?: return null, safe)
        if (dest.exists()) return null
        return if (target.renameTo(dest)) dest else null
    }

    /** 项目所用语言（读 .devterminal 标记，缺省按扩展名推断） */
    fun languageOf(projectDir: File): com.devterminal.engine.Language {
        val mark = File(projectDir, ".devterminal")
        if (mark.exists()) {
            runCatching { return com.devterminal.engine.Language.valueOf(mark.readText().trim()) }
        }
        return if (projectDir.walkTopDown().any { it.extension == "java" })
            com.devterminal.engine.Language.JAVA else com.devterminal.engine.Language.PYTHON
    }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]"), "_").ifBlank { "untitled" }
}
