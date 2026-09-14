package com.devterminal.engine

/**
 * 静态代码补全数据源。
 *
 * 为什么不接 LSP：完整的 language server 是独立进程 + JSON-RPC，在手机上
 * 内存和启动开销都不划算，而且离线场景下补全需求主要是「关键字 + 常见库符号」。
 * 这里用静态词表覆盖 80% 的日常场景，零进程开销。
 */
object CompletionProvider {

    /** 补全项 */
    data class Item(val text: String, val kind: Kind, val detail: String = "")

    enum class Kind { KEYWORD, BUILTIN, MODULE, CLASS, METHOD }

    private val PY_KEYWORDS = listOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break",
        "continue", "import", "from", "as", "try", "except", "finally", "raise",
        "with", "lambda", "yield", "global", "nonlocal", "pass", "assert", "del",
        "in", "not", "and", "or", "is", "None", "True", "False", "async", "await"
    )

    private val PY_BUILTINS = listOf(
        "print", "len", "range", "int", "str", "float", "list", "dict", "set",
        "tuple", "bool", "input", "open", "enumerate", "zip", "map", "filter",
        "sorted", "sum", "min", "max", "abs", "round", "type", "isinstance",
        "hasattr", "getattr", "setattr", "super", "format", "repr", "id", "hash"
    )

    private val PY_MODULES = listOf(
        "math", "random", "os", "sys", "json", "time", "datetime", "re",
        "collections", "itertools", "functools", "typing"
    )

    /** 常用第三方库（离线工具链预装的） */
    private val PY_THIRD_PARTY = listOf("numpy", "pandas", "matplotlib", "flask", "requests")

    private val PY_MODULE_METHODS = mapOf(
        "math" to listOf("sqrt", "pow", "floor", "ceil", "pi", "e", "sin", "cos", "tan", "log", "exp"),
        "random" to listOf("random", "randint", "choice", "shuffle", "uniform", "seed", "sample"),
        "os" to listOf("path", "getcwd", "listdir", "mkdir", "remove", "environ", "system"),
        "json" to listOf("dumps", "loads", "dump", "load"),
        "time" to listOf("time", "sleep", "strftime", "localtime"),
        "re" to listOf("match", "search", "findall", "sub", "compile", "split")
    )

    private val JAVA_KEYWORDS = listOf(
        "public", "private", "protected", "static", "final", "class", "interface",
        "extends", "implements", "return", "if", "else", "for", "while", "do",
        "switch", "case", "default", "break", "continue", "new", "this", "super",
        "try", "catch", "finally", "throw", "throws", "import", "package",
        "void", "int", "long", "double", "float", "boolean", "char", "String",
        "byte", "short", "null", "true", "false", "abstract", "synchronized"
    )

    private val JAVA_CLASSES = listOf(
        "System", "String", "Integer", "Double", "Math", "List", "ArrayList",
        "Map", "HashMap", "Set", "HashSet", "Scanner", "Arrays", "Collections",
        "StringBuilder", "Object", "Exception", "RuntimeException"
    )

    private val JAVA_METHODS = mapOf(
        "System" to listOf("out.println", "out.print", "out.printf", "err.println", "exit", "currentTimeMillis"),
        "String" to listOf("length()", "charAt()", "substring()", "indexOf()", "split()", "trim()", "toUpperCase()", "toLowerCase()", "equals()", "contains()", "replace()"),
        "Math" to listOf("abs", "max", "min", "pow", "sqrt", "random", "round", "floor", "ceil", "PI", "E"),
        "List" to listOf("add()", "get()", "size()", "remove()", "contains()", "isEmpty()", "clear()"),
        "Map" to listOf("put()", "get()", "containsKey()", "keySet()", "values()", "size()", "remove()")
    )

    /**
     * 获取补全候选。
     * @param language 语言
     * @param prefix 当前已输入的前缀（用于过滤）
     * @param documentText 当前文档全文（用于提取用户自定义符号）
     */
    fun candidates(
        language: Language,
        prefix: String,
        documentText: String
    ): List<Item> {
        val base = when (language) {
            Language.PYTHON -> pyItems() + extractPySymbols(documentText)
            Language.JAVA -> javaItems() + extractJavaSymbols(documentText)
        }
        val p = prefix.trim()
        val filtered = if (p.isEmpty()) base else base.filter { it.text.startsWith(p, ignoreCase = true) }
        // 前缀完全匹配的排前面，其余按字母序
        return filtered
            .sortedWith(compareBy({ !it.text.startsWith(p) }, { it.text.length }, { it.text }))
            .take(60)
    }

    private fun pyItems(): List<Item> = buildList {
        PY_KEYWORDS.forEach { add(Item(it, Kind.KEYWORD, "关键字")) }
        PY_BUILTINS.forEach { add(Item(it, Kind.BUILTIN, "内置函数")) }
        PY_MODULES.forEach { add(Item(it, Kind.MODULE, "标准库")) }
        PY_THIRD_PARTY.forEach { add(Item(it, Kind.MODULE, "第三方库")) }
        PY_MODULE_METHODS.forEach { (mod, methods) ->
            methods.forEach { add(Item(it, Kind.METHOD, "${mod} 模块")) }
        }
    }

    private fun javaItems(): List<Item> = buildList {
        JAVA_KEYWORDS.forEach { add(Item(it, Kind.KEYWORD, "关键字")) }
        JAVA_CLASSES.forEach { add(Item(it, Kind.CLASS, "常用类")) }
        JAVA_METHODS.forEach { (cls, methods) ->
            methods.forEach { add(Item(it, Kind.METHOD, "${cls} 方法")) }
        }
    }

    /** 从 Python 文档里抽取用户定义的函数/类名 */
    private fun extractPySymbols(text: String): List<Item> {
        val items = mutableListOf<Item>()
        Regex("""(?m)^\s*def\s+(\w+)""").findAll(text).forEach {
            items.add(Item(it.groupValues[1], Kind.METHOD, "本文件函数"))
        }
        Regex("""(?m)^\s*class\s+(\w+)""").findAll(text).forEach {
            items.add(Item(it.groupValues[1], Kind.CLASS, "本文件类"))
        }
        Regex("""(?m)^\s*(\w+)\s*=\s*""").findAll(text).take(20).forEach {
            val name = it.groupValues[1]
            if (name.length > 1 && name !in PY_KEYWORDS) {
                items.add(Item(name, Kind.BUILTIN, "变量"))
            }
        }
        return items
    }

    /** 从 Java 文档里抽取类名与方法名 */
    private fun extractJavaSymbols(text: String): List<Item> {
        val items = mutableListOf<Item>()
        Regex("""\bclass\s+(\w+)""").findAll(text).forEach {
            items.add(Item(it.groupValues[1], Kind.CLASS, "本文件类"))
        }
        Regex("""\b(?:public|private|protected)?\s*(?:static\s+)?(?:\w+)\s+(\w+)\s*\(""")
            .findAll(text).take(30).forEach {
                val name = it.groupValues[1]
                if (name !in JAVA_KEYWORDS && name.length > 1) {
                    items.add(Item(name, Kind.METHOD, "本文件方法"))
                }
            }
        return items
    }
}
