package com.devterminal.project

import com.devterminal.engine.Language

/** 项目模板 */
data class ProjectTemplate(
    val id: String,
    val title: String,
    val description: String,
    val language: Language,
    /** 相对路径 -> 初始内容 */
    val files: Map<String, String>
)

object Templates {

    val PYTHON_HELLO = ProjectTemplate(
        id = "python-hello",
        title = "Python 空白项目",
        description = "一个 main.py，点击运行即可看到输出",
        language = Language.PYTHON,
        files = mapOf(
            "main.py" to """
                # -*- coding: utf-8 -*-
                # DevTerminal 离线 Python 模板
                def main():
                    print("Hello from DevTerminal!")
                    total = sum(i * i for i in range(1, 11))
                    print(f"1^2 + 2^2 + ... + 10^2 = {total}")

                if __name__ == "__main__":
                    main()
            """.trimIndent()
        )
    )

    val PYTHON_DATA = ProjectTemplate(
        id = "python-data",
        title = "Python 数据处理",
        description = "纯标准库 statistics 模块统计示例",
        language = Language.PYTHON,
        files = mapOf(
            "analyze.py" to """
                # -*- coding: utf-8 -*-
                # 纯标准库示例：statistics 模块，离线开箱即用，无需安装第三方库
                import statistics

                def main():
                    data = [52, 48, 55, 61, 49, 53, 58, 47, 60, 54, 57, 50, 62, 46, 59, 51]
                    print(f"样本数: {len(data)}")
                    print(f"均值:   {statistics.mean(data):.2f}")
                    print(f"标准差: {statistics.stdev(data):.2f}")
                    print(f"中位数: {statistics.median(data):.2f}")
                    print(f"最小/最大: {min(data)} / {max(data)}")

                if __name__ == "__main__":
                    main()
            """.trimIndent()
        )
    )

    val JAVA_HELLO = ProjectTemplate(
        id = "java-hello",
        title = "Java 控制台项目",
        description = "标准 Java SE 程序（离线编译 + 运行）",
        language = Language.JAVA,
        files = mapOf(
            "Main.java" to """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello from DevTerminal (Java)!");
                        long sum = 0;
                        for (int i = 1; i <= 100; i++) sum += i;
                        System.out.println("1 + 2 + ... + 100 = " + sum);
                    }
                }
            """.trimIndent()
        )
    )

    val PYTHON_INTERACTIVE = ProjectTemplate(
        id = "python-interactive",
        title = "Python 交互输入",
        description = "演示 input() 交互：猜数字游戏（练习标准输入）",
        language = Language.PYTHON,
        files = mapOf(
            "main.py" to """
                # -*- coding: utf-8 -*-
                # 演示：运行后用底部输入行回答，程序会实时响应
                import random

                def guess_game():
                    secret = random.randint(1, 100)
                    tries = 0
                    print("我在想一个 1-100 的数字，猜猜看？")
                    while True:
                        raw = input("请输入: ")
                        if not raw.strip().lstrip("-").isdigit():
                            print("请输入一个整数")
                            continue
                        guess = int(raw)
                        tries += 1
                        if guess < secret:
                            print("太小了！")
                        elif guess > secret:
                            print("太大了！")
                        else:
                            print(f"猜对了！用了 {tries} 次")
                            break

                if __name__ == "__main__":
                    guess_game()
            """.trimIndent()
        )
    )

    val PYTHON_MULTI = ProjectTemplate(
        id = "python-multi",
        title = "Python 多模块",
        description = "演示多文件协作：main.py 导入同目录的 utils.py",
        language = Language.PYTHON,
        files = mapOf(
            "utils.py" to """
                # -*- coding: utf-8 -*-
                def fib(n: int) -> list:
                    seq = [0, 1]
                    while len(seq) < n:
                        seq.append(seq[-1] + seq[-2])
                    return seq[:n]

                def is_prime(n: int) -> bool:
                    if n < 2:
                        return False
                    for i in range(2, int(n ** 0.5) + 1):
                        if n % i == 0:
                            return False
                    return True
            """.trimIndent(),
            "main.py" to """
                # -*- coding: utf-8 -*-
                # 演示多文件：直接 import 同目录模块（离线可用）
                from utils import fib, is_prime

                def main():
                    print("前 10 个斐波那契数:", fib(10))
                    primes = [n for n in range(2, 50) if is_prime(n)]
                    print("50 以内的素数:", primes)

                if __name__ == "__main__":
                    main()
            """.trimIndent()
        )
    )

    val JAVA_MULTI = ProjectTemplate(
        id = "java-multi",
        title = "Java 多文件项目",
        description = "演示多文件编译：Main 调用 Calculator，含 package 声明",
        language = Language.JAVA,
        files = mapOf(
            "Main.java" to """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Java 多文件项目演示");
                        Calculator calc = new Calculator();
                        System.out.println("3 + 4 = " + calc.add(3, 4));
                        System.out.println("10! = " + calc.factorial(10));
                    }
                }
            """.trimIndent(),
            "Calculator.java" to """
                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }

                    public long factorial(int n) {
                        long r = 1;
                        for (int i = 2; i <= n; i++) r *= i;
                        return r;
                    }
                }
            """.trimIndent()
        )
    )

    // Java 模板（JAVA_HELLO / JAVA_MULTI）保留定义备查，但不进 all——
    // 当前唯一运行时是 NativeEngine（原生 CPython，只 execFile .py），未实现 Java
    // 编译/运行链路，放出来用户选了跑不了。待未来接入原生 JDK/Java 运行时再恢复。
    val all: List<ProjectTemplate> = listOf(
        PYTHON_HELLO, PYTHON_INTERACTIVE, PYTHON_MULTI, PYTHON_DATA
    )
}
