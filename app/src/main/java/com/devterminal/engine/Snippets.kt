package com.devterminal.engine

/**
 * 内置代码片段库：命令面板可达，插入到光标处。
 *
 * 只收录「手写麻烦、复用率高」的骨架代码——这是移动端输入效率的杠杆。
 */
object Snippets {

    data class Snippet(
        val id: String,
        val title: String,
        val description: String,
        val language: Language,
        val code: String
    )

    val all: List<Snippet> = listOf(
        // ---------- Python ----------
        Snippet("py-main", "Python 主入口", "if __name__ == \"__main__\" 守卫", Language.PYTHON,
            "def main():\n    pass\n\nif __name__ == \"__main__\":\n    main()\n"),
        Snippet("py-class", "Python 类骨架", "含 __init__ 与 __repr__", Language.PYTHON,
            "class MyClass:\n    def __init__(self, name: str):\n        self.name = name\n\n    def __repr__(self) -> str:\n        return f\"MyClass(name={self.name!r})\"\n"),
        Snippet("py-open", "读文件（with）", "自动关闭文件句柄", Language.PYTHON,
            "with open(\"data.txt\", \"r\", encoding=\"utf-8\") as f:\n    content = f.read()\n"),
        Snippet("py-try", "异常处理", "try / except / else / finally", Language.PYTHON,
            "try:\n    risky()\nexcept ValueError as e:\n    print(f\"非法输入: {e}\")\nexcept Exception as e:\n    print(f\"出错了: {e}\")\nelse:\n    print(\"成功\")\nfinally:\n    cleanup()\n"),
        Snippet("py-deco", "装饰器", "计时装饰器模板", Language.PYTHON,
            "import functools, time\n\ndef timer(func):\n    @functools.wraps(func)\n    def wrapper(*args, **kwargs):\n        start = time.perf_counter()\n        result = func(*args, **kwargs)\n        print(f\"{func.__name__} 耗时 {time.perf_counter() - start:.3f}s\")\n        return result\n    return wrapper\n"),
        Snippet("py-dataclass", "数据类", "dataclass 定义", Language.PYTHON,
            "from dataclasses import dataclass\n\n@dataclass\nclass Point:\n    x: float\n    y: float\n"),
        // ---------- Java ----------
        Snippet("java-main", "Java 主类", "public static void main", Language.JAVA,
            "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n"),
        Snippet("java-pojo", "Java POJO", "字段 + getter/setter", Language.JAVA,
            "public class User {\n    private String name;\n    private int age;\n\n    public User(String name, int age) {\n        this.name = name;\n        this.age = age;\n    }\n\n    public String getName() { return name; }\n    public void setName(String name) { this.name = name; }\n    public int getAge() { return age; }\n    public void setAge(int age) { this.age = age; }\n}\n"),
        Snippet("java-try", "Java 异常处理", "try / catch / finally", Language.JAVA,
            "try {\n    risky();\n} catch (IllegalArgumentException e) {\n    System.out.println(\"非法参数: \" + e.getMessage());\n} catch (Exception e) {\n    e.printStackTrace();\n} finally {\n    // cleanup\n}\n"),
        Snippet("java-list", "集合遍历", "ArrayList + 增强 for", Language.JAVA,
            "import java.util.*;\n\nList<String> items = new ArrayList<>(List.of(\"a\", \"b\", \"c\"));\nfor (String item : items) {\n    System.out.println(item);\n}\n"),
        Snippet("java-stream", "Stream 过滤", "filter + map + collect", Language.JAVA,
            "import java.util.stream.Collectors;\nimport java.util.*;\n\nList<Integer> result = numbers.stream()\n    .filter(n -> n % 2 == 0)\n    .map(n -> n * 2)\n    .collect(Collectors.toList());\n"),
        Snippet("java-regex", "正则匹配", "Pattern / Matcher", Language.JAVA,
            "import java.util.regex.*;\n\nPattern p = Pattern.compile(\"\\\\d+\");\nMatcher m = p.matcher(text);\nwhile (m.find()) {\n    System.out.println(m.group());\n}\n")
    )

    fun forLanguage(language: Language): List<Snippet> =
        all.filter { it.language == language }
}
