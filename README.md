<h1 align="center">📱 DevTerminal</h1>

<p align="center">
  <b>真正离线的安卓 Python 终端 — 手机上的现代 IDE。<br>运行时随 APK 打包，装完即离线，零下载、零网络。</b>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square" alt="License" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/platform-Android%208.0%2B-green?style=flat-square" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin%202.0%20%2B%20Compose-7F52FF?style=flat-square" alt="Kotlin" /></a>
  <img src="https://img.shields.io/badge/%E7%A6%BB%E7%BA%BF-100%25%20%E9%9B%B6%E7%BD%91%E7%BB%9C-orange?style=flat-square" alt="Offline" />
  <img src="https://img.shields.io/badge/%E5%B9%BF%E5%91%8A-0%20%E6%9D%A1-red?style=flat-square" alt="No Ads" />
</p>

<p align="center">
  <a href="https://x33834.github.io/dev-terminal/"><img src="https://img.shields.io/badge/%F0%9F%8C%90_Official_Site-Visit-brightgreen?style=flat-square" alt="Official Site" /></a>
  <a href="https://github.com/x33834/dev-terminal/releases/latest"><img src="https://img.shields.io/github/v/release/x33834/dev-terminal?style=flat-square&label=release" alt="Latest Release" /></a>
  <a href="https://github.com/Morningstar202604/dev-terminal"><img src="https://img.shields.io/badge/GitHub-Mirror-24292F?style=flat-square&logo=github" alt="GitHub Mirror" /></a>
  <a href="https://gitcode.com/badhope/dev-terminal/releases"><img src="https://img.shields.io/badge/%E2%AC%87%EF%B8%8F_APK_%E7%9B%B4%E4%B8%8B-GitCode_Release-3A72BE?style=flat-square" alt="APK Download" /></a>
  <a href="https://gitee.com/badhope/dev-terminal"><img src="https://img.shields.io/badge/Gitee-Mirror-C71D23?style=flat-square" alt="Gitee" /></a>
</p>

<p align="center"><b>开源仓库</b>（四平台并列，内容完全一致）：<a href="https://github.com/x33834/dev-terminal">GitHub/x33834</a> · <a href="https://github.com/Morningstar202604/dev-terminal">GitHub/Morningstar202604</a> · <a href="https://gitcode.com/badhope/dev-terminal">GitCode</a> · <a href="https://gitee.com/badhope/dev-terminal">Gitee</a></p>

<p align="center"><b>🌐 官网</b>（GitHub Pages 双号部署，内容一致）：<a href="https://x33834.github.io/dev-terminal/">x33834.github.io/dev-terminal</a> · <a href="https://morningstar202604.github.io/dev-terminal/">morningstar202604.github.io/dev-terminal</a></p>

**一个真正离线的安卓编程终端**：Python 运行时（Pyodide / WebAssembly，约 13MB）随 APK 打包，
安装后无需任何下载即可写、跑、调试代码，界面是现代 IDE 而不是命令行黑框。

> **本版重构说明（v0.6）**
> 此前版本依赖一个 441MB 的原生 Termux 工具链，它不入库、必须联网下载，
> 导致「离线」名不副实——clone 源码构建出的 APK 根本跑不了代码。
> 本版彻底换为 **Pyodide（WASM 版 CPython）**，运行时随包分发，**装完即离线**。
> 代价是 JVM 无法在 WASM 沙箱内运行，**Java 执行暂不可用**（保留编辑与语法高亮），
> Git 功能同理暂时下线。详见文末「能力边界」。

![原型预览](design/preview.png)

## 与市面方案的区别

### 与已发布软件的完整功能对照（2026-09 核实）

| 功能 | Termux | Pydroid 3 | Acode | Spck | **DevTerminal** |
|---|---|---|---|---|---|
| 现代 IDE 界面 | ❌ 命令行 | ⚠️ 较旧 | ✅ | ✅ | ✅ |
| 多语言 | ✅ | ❌ 仅 Python | ✅ | ⚠️ Web 为主 | ⚠️ Python（编辑器支持多语言高亮） |
| 完全离线运行 | ⚠️ 装包要联网 | ⚠️ 部分离线 | ⚠️ | ❌ | ✅ **运行时随包，零网络** |
| 快捷符号栏 | ❌ | ✅ | ✅ | ✅ | ✅ 26 键 + Tab/退格 |
| 多文件 Tab | — | ✅ | ✅ | ✅ | ✅ |
| 查找/替换 | grep | ✅ | ✅ 正则 | ✅ | ✅ 计数跳转 |
| 全局搜索 | — | ❌ | ✅ | ✅ | ✅ |
| Git 集成 | ✅ | ❌ | ✅ | ✅ | ⚠️ 暂不可用（见能力边界） |
| AI 助手 | ❌ | ❌ | ✅ 云端 | ✅ 云端 | ✅ 自配端点（云端/自建均可） |
| 双指缩放字号 | — | ✅ | ✅ | ✅ | ✅ |
| 输出分享 | ✅ | ✅ | ⚠️ | ✅ | ✅ |
| 开源 | ✅ GPLv3 | ❌ | ✅ MIT | ⚠️ 部分 | ✅ Apache-2.0 |
| 价格 | 免费 | 免费+广告 | 免费+广告 | 免费+内购 | **免费无广告** |

> 对标来源：各应用 Google Play / F-Droid 页面与其官方文档（2026-09 状态）。
> 我们的优势组合：**开源 + 无广告 + 装完即离线（运行时随 APK）**；差距项见下方 Roadmap 与能力边界。

### 能力边界（诚实标注）

本版运行于 Pyodide（WebAssembly）架构，部分旧能力因此下线。这不是"待修 bug"，而是架构的固有约束：

| 能力 | 状态 | 说明 |
|---|---|---|
| Python 运行 | ✅ 完整可用 | CPython 标准库全量，完全离线 |
| numpy / pandas | ✅ 预装 | 官方 Pyodide wheel 随 APK 分发，按 `import` 自动装载，零网络 |
| matplotlib | ✅ 预装 | 离线画图 `savefig` PNG，同上（v0.8.0）；WASM 沙箱无窗口，`plt.show()` 不会弹窗，请用 `plt.savefig("x.png")` |
| Markdown / HTML 预览 | ✅ 可用 | 编辑器分屏实时渲染，GFM 表格/删除线（v0.8.0） |
| 编辑器主题 | ✅ 6 套 | Darcula / Quiet Light / Monokai / 明日蓝 / Solarized 暗·亮（v0.8.0） |
| `input()` 交互 | ✅ 可用 | 输出面板下方输入行，运行中可实时喂入 |
| 死循环保护 | ✅ 可用 | 超时通过 WASM 中断缓冲触发 KeyboardInterrupt |
| **Java 运行** | ❌ 不可用 | **JVM 无法运行于 WASM 沙箱**，仅保留编辑与语法高亮 |
| **Git 操作** | ✅ 可用 | JGit（纯 Java）随 APK 打包：init / commit / push / pull，依旧离线 |
| 性能 | ⚠️ 约为原生的 1/10~1/50 | WASM 沙箱的固有代价，脚本/学习场景无感 |

> **AI 助手不属于上表**：它是可选的在设置页配置的**在线服务**（BYOK），
> 与"离线能力"无关 —— 离线指的是代码执行。详见下方「AI 助手」章节。

### Roadmap（主动承认差距）

- [x] 预打包常用 Pyodide wheel（numpy / pandas），离线可直接 import（v0.7.0）
- [x] 以 JGit（纯 Java）恢复 Git 状态 / 提交 / 推送（v0.7.0）
- [x] matplotlib 离线包（v0.8.0）
- [x] 编辑器主题选择（v0.8.0，6 套 TextMate 配色）
- [x] Markdown / HTML 实时预览（v0.8.0，编辑器分屏 + 防抖刷新）
- [ ] 三态主题：跟随系统（当前深浅二选一）
- [ ] 逐字符 pty 输入（方向键 / Tab 补全，需要完整终端模拟）
- [ ] pip 离线包管理器（预装 wheel 缓存）

> **v0.2 对标升级**：UX 对齐 2026 年移动编程工具第一梯队（Pydroid 3 的扩展键盘、
> Spck 的 on-screen coding keys、Acode 的多 Tab 与查找替换、VS Code 的状态栏与会话恢复），
> 每一项都是为「手机上真的写代码」设计的，不是桌面功能的照搬。
>
> **v0.3 对标升级**：补齐 2026 年移动编程的「新四大件」——
> **命令面板**（Cursor 风格 ⌘K 动作搜索）、**Git 面板**（Spck 风格，调内置 git）、
> **AI 助手**（BYOK，设置页自配端点，Replit 风格）、**全局搜索 + 代码片段**（Acode 风格）。

## 架构

```
Compose UI 层        文件树 / 代码编辑器 / 输出面板 / 运行栏
      │
执行引擎层           PyodideEngine — 内嵌 WebView 承载 Pyodide（WASM 版 CPython）
      │              JavascriptInterface 双向通信，实时流式 stdout/stderr，带超时中断
运行时资源层         assets/pyodide/ → 随 APK 打包，零下载、零网络
                     pyodide.asm.wasm（解释器）+ python_stdlib.zip（标准库）
```

**设计取舍**：不自造终端模拟器，也不依赖外置原生工具链。
Python 由 **Pyodide（CPython 编译为 WebAssembly）** 执行 —— 它随 APK 打包，
安装完成即具备完整离线运行能力。用 `WebViewAssetLoader` 以 https 域加载 assets，
避开 `file://` 下 WASM 与 ES module 的跨源限制。

**关键构建约束**：`.wasm` 与 `.zip` 必须配置 `noCompress`，否则 AAPT 再压缩后 WebView 无法加载。

## 目录结构

```
dev-terminal/
├── app/src/main/java/com/devterminal/
│   ├── MainActivity.kt                入口
│   ├── engine/
│   │   ├── PyodideEngine.kt           执行引擎（内嵌 WebView + WASM，真离线）
│   │   ├── EnvironmentInstaller.kt    工作目录管理
│   │   ├── EnvDiagnostics.kt          环境自检（校验 APK 内运行时资源完整性）
│   │   ├── FriendlyError.kt           报错翻译成人话
│   │   ├── CompletionProvider.kt      静态补全词表 + 文档符号提取
│   │   ├── GitManager.kt              Git 操作（JGit 纯 Java，离线）
│   │   ├── AiClient.kt                AI 客户端（OpenAI 兼容，v0.3）
│   │   ├── Snippets.kt                代码片段库（v0.3）
│   │   ├── ExecutionService.kt        前台服务保活（防 Android 12+ 杀进程）
│   │   ├── CrashLogger.kt             全局崩溃日志
│   │   ├── RunModels.kt               Language / RunRequest / RunEvent
│   │   └── SyntaxCheck.kt             开发期自检工具
│   ├── settings/
│   │   └── SettingsStore.kt           设置持久化（SharedPreferences）
│   ├── project/
│   │   ├── ProjectManager.kt          项目目录 / 文件树 / 读写 / 重命名
│   │   └── Templates.kt               6 个项目模板
│   └── ui/
│       ├── EditorScreen.kt            主界面 Scaffold + 各对话框
│       ├── EditorViewModel.kt         状态管理（Tab/查找/会话恢复）
│       ├── CodeEditorView.kt          SoraEditor 桥接（光标/插入/查找跳转）
│       ├── StaticCompletionLanguage.kt 补全语言包装器（委托高亮 + 静态补全）
│       ├── FileTabs.kt                顶部多文件 Tab 条（v0.2）
│       ├── SymbolBar.kt               快捷符号栏（v0.2）
│       ├── FindBar.kt                 查找 / 替换条（v0.2）
│       ├── StatusBar.kt               底部状态栏（v0.2）
│       ├── FileTree.kt                文件树（长按操作）
│       ├── OutputPanel.kt             输出面板 + 交互输入行
│       ├── CommandPalette.kt          命令面板 ⌘K（v0.3）
│       ├── GlobalSearch.kt            全局搜索（v0.3）
│       ├── GitPanel.kt                Git 面板（v0.3）
│       ├── AiPanel.kt                 AI 助手面板（v0.3）
│       └── theme/Theme.kt             Material 3 主题
├── app/src/main/assets/pyodide/        内置 Python 运行时（WASM，随 APK 打包）
├── app/src/main/assets/python-runner.html  运行器页面（Pyodide 加载 + 流式输出）
├── design/mockup.html                 高保真可交互原型（浏览器直接打开）
├── tools/
│   ├── sync_design_runtime.sh         把 App 的运行时同步给 design 原型
│   ├── serve_design.py                本地起服务预览原型
│   ├── selfcheck.py                   静态自检（无需 Android SDK）
│   └── test_*.py                      端到端测试（Playwright）
└── app/build.gradle.kts
```

## 核心功能

### 1. 交互式 stdin（支持 `input()`）
输出面板下方有输入行，运行中的进程可接收键盘输入。
Python 的 `input()`、`raw_input()` 等阻塞式读取都能正常交互。

> 实现要点：引擎用 `-u` 启动 Python 关闭缓冲，否则输出会憋到进程结束才刷出。

### 2. Java 多文件编译
点「运行」时自动：
1. 递归收集项目内所有 `.java` 源文件
2. `javac -encoding UTF-8 -d build/classes` 一次性编译
3. 自动探测含 `static void main` 的类（支持 `package` 声明，拼出全限定名）
4. `java -cp build/classes <主类>` 运行

多个类互相调用、含包声明的项目都能直接跑。

### 3. Python 多模块导入
工作目录设为项目根，所以 `from utils import fib` 这类同目录导入开箱可用。

### 4. 6 个项目模板
| 模板 | 演示内容 |
|---|---|
| Python 空白项目 | 基础语法 |
| Python 交互输入 | `input()` 猜数字（练习 stdin） |
| Python 多模块 | `main.py` 导入 `utils.py` |
| Python 数据处理 | numpy 统计计算 |
| Java 控制台项目 | 单文件 Java |
| Java 多文件项目 | 多类协作 + 编译流程 |

### 5. 环境自检与友好报错

**自检**（抽屉 → 「环境自检」）：逐项显示运行时资源完整性（解释器 / 标准库 / 运行器页面）
与各能力状态（Python 执行、numpy/pandas 预装、Git/JGit、Java）。失败的项会说明原因。

**友好报错**：运行失败时自动把原始报错翻译成可操作的中文提示，覆盖 20+ 高频错误：

| 原始报错 | 提示 |
|---|---|
| `ModuleNotFoundError: No module named 'xxx'` | 内置库仅 numpy / pandas 及其依赖；其它库需按 Roadmap 方式自行扩展 wheel |
| `IndentationError` | 缩进不一致，建议统一 4 空格 |
| `EOFError` | 程序在等输入但读到 EOF，用底部输入行喂入 |
| `KeyboardInterrupt` | 通常是超时保护触发了中断（死循环被安全终止） |
| `ModuleNotFoundError` | 标准库以外的库需随 APK 打包对应 wheel |
| `Killed` / `signal 9` | 被系统杀进程，去设电池无限制 |
| `No space left` | 存储不足，清理项目目录 |

### 6. 运行配置

顶部调音图标可配置：

- **命令行参数**：支持引号，如 `--name "hello world" -v` → `["--name", "hello world", "-v"]`
- **Java 主类**：留空则自动探测含 `main` 方法的类（含 `package` 声明）

### 7. 文件管理

文件树**长按**任一文件/目录，可重命名或删除（删除需二次确认）。

### 8. 前台服务保活

运行代码时自动启动前台服务 + 低优先级通知，规避 Android 12+ 的进程清理；
跑完立即撤掉通知，不常驻状态栏。

### 9. 导入 / 导出（SAF）

抽屉 →「导入」/「导出」：

- **导入**：从手机任意位置选文件，复制进当前项目并自动打开
- **导出**：把当前编辑内容写到手机任意位置（默认用原文件名）

基于 Android Storage Access Framework，不需要存储权限，兼容 Android 10+ 分区存储。

### 10. 设置页

抽屉 →「设置」：

| 项 | 说明 |
|---|---|
| 深色主题 | 关掉用浅色配色，实时生效 |
| 切换文件时自动保存 | 防止忘保存丢改动 |
| 编辑器字号 | 10–24sp 滑杆调节 |
| 运行超时 | 10–600 秒，防止死循环耗电 |

配置存在 SharedPreferences，重启保留。

### 11. 代码补全（静态）

输入 ≥1 个字符即弹出候选，覆盖：

- **Python**：39 个关键字、32 个内置函数、13 个标准库、5 个预装第三方库（numpy/pandas/matplotlib/flask/requests）、模块常用方法，**外加从当前文件提取的函数/类/变量名**
- **Java**：41 个关键字、18 个常用类、各类常用方法，**外加本文件定义的类与方法**

> **为什么不用 LSP**：完整 language server 是独立进程 + JSON-RPC，手机上内存与启动开销都不划算。静态词表 + 文档符号提取覆盖日常场景，零进程开销，契合「离线 + 省电」定位。

### 12. 崩溃日志

全局未捕获异常自动写入 `files/crash.log`（含时间、线程、完整堆栈）。
这是给离线场景设计的：用户在手机上崩了没法连电脑看 logcat，有了它可以直接复制反馈。

### 13. 静态自检脚本

```bash
python3 tools/selfcheck.py
```

无 Android SDK 时也能跑，检查：花括号配平（正确处理字符串模板）、
Compose 图标 import 完整性、package 与目录一致性、未使用的 import。
当前状态：**24 个文件，0 错误 0 警告**。

### 14. 多文件 Tab 条（v0.2）

顶栏下方一排 Tab，点一下切文件，× 关闭——不用再走抽屉。
对标 VS Code / Acode。关闭当前 Tab 自动切到相邻文件。

### 15. 快捷符号栏（v0.2）

编辑器下方固定一排编程高频符号：
`⇥ ( ) [ ] { } : ; " ' = < > + - * / % _ # & | ! . , $ \` + 退格。

横向滚动，点击直接插入光标处（走编辑器撤销栈，可 Ctrl-Z 回退）。
灵感来自 Pydroid 3 的扩展键盘与 Spck 的 on-screen coding keys——
**手机键盘打符号要切两三层键盘，这是移动编程第一痛点**。

### 16. 查找 / 替换（v0.2）

顶栏 🔍 唤出：实时匹配计数（`2/5`）、上一个/下一个跳转（编辑器自动滚动定位）、
展开后支持单个替换与全部替换。对标 Acode 的 Search & Replace。

### 17. 会话恢复（v0.2）

杀掉 App 再打开，自动回到上次的现场：上次项目、打开的 Tab 列表、
正在编辑的文件。不再每次启动都回到默认项目。

### 18. 输出面板拖拽调高（v0.2）

输出面板上沿的把手可以上下拖（120–560dp），看完长报错不用再进输出区滚动；
松手后高度记忆在设置里，重启保留。

### 19. 底部状态栏（v0.2）

`Ln 12, Col 8 · Python · 452 字符 · ● 未保存`——VS Code 的标配信息条，
随时知道光标在哪、文件脏不脏。

### 20. 命令面板 ⌘K（v0.3）

顶栏命令图标唤出，**搜索一切**：运行、保存、Git、AI、全局搜索、设置、12 个代码片段
——所有功能一个输入框直达。移动端放不下 N 层菜单，「搜索式导航」是最省屏幕空间的
交互（Cursor / VS Code 的核心范式）。

### 21. Git 面板（v0.3，v0.7.0 起由 JGit 驱动 ✅）

> **实现**：`GitManager` 基于 **JGit（纯 Java，随 APK 打包）**——init / status /
> commit / push / pull 全部在应用进程内完成，无需任何外部二进制，依旧离线。
> 远程 URL 支持 `https://user:token@host/repo.git` 内嵌凭据；结果回显只显示主机名，不回显 token。

能力：

- 状态：分支名、ahead/behind、变更文件列表、最近提交
- 动作：初始化（main）、全部暂存并提交（含删除）、推送、拉取（自动合并）
- 配置：Git 身份与远程 URL（token 可内嵌）
- 报错翻译：`403`→检查令牌、`non-fast-forward`→先拉取再推送、`who are you`→填身份

### 22. AI 助手（可选，**在线服务**）

**AI 不是离线能力**，它和「Pyodide 离线跑 Python」是两件独立的事 ——
本 App 的离线指的是**代码执行**，AI 则是一个**由你自行配置的在线服务**。

在**设置页**里填入端点与密钥即可接入（BYOK）：

- 端点：任何 **OpenAI 协议兼容**的服务，填 `https://<host>/v1` 形式
  - 云端：OpenAI / DeepSeek / Qwen / GLM / Moonshot 等
  - 自建：llama.cpp server / LM Studio / Ollama 等你自己的部署
- 密钥：填入对应 API Key（自建无鉴权时留空）
- 模型：填服务端可识别的模型名

功能：

- 四个快捷动作：**解释此文件 / 修复运行错误 / 生成测试 / 加注释**
- 「修复运行错误」会把最近一次运行的报错输出自动拼进上下文
- 上下文自动携带当前文件（截断 3500 字符防 token 爆炸）

> **关于默认值**：`aiBaseUrl` 默认指向 `http://127.0.0.1:11434/v1`（本机 Ollama），
> 这只是个占位示例。手机上一般没有本地推理服务，**不配置就用不了**。
> 未配置端点时不会发起任何网络请求，App 其余功能完全离线。
>
> 若你确实想要"离线 AI"，可以自行部署一个 OpenAI 兼容端点并在局域网内指向它，
> 但这属于你的自建部署，不是 App 内置能力。

### 23. 全局搜索（v0.3）

跨文件搜索所有 `.py / .java / .txt / .md / .json / .xml`，显示「文件 · 行号 + 命中行」，
点击直达。输入防抖 400ms，结果上限 200 条防卡顿。对标 Acode 的 project-wide search。

### 24. 代码片段库（v0.3）

12 个「手写麻烦、复用率高」的骨架代码（Python 主入口 / 类 / dataclass / 装饰器，
Java POJO / Stream / 正则等），从命令面板插入到光标处。

## UI 原型

`design/mockup.html` 是一个高保真原型，用纯 HTML/CSS/JS 实现：

- 真机外框 + 模拟状态栏
- 抽屉式文件树（可展开/折叠/切换文件）
- 真实语法高亮的代码编辑器（Python + Java 两套词法分析）
- 打字机效果的输出面板
- 可交互的输入行（真的能玩猜数字）

**运行方式**（原型需要 Pyodide 运行时，但仓库只存一份，需先同步）：

```bash
bash tools/sync_design_runtime.sh   # 从 App assets 同步运行时到 design/
python3 tools/serve_design.py       # 起本地服务，浏览器打开提示的地址
```

> 直接双击 `mockup.html` 无法运行 Python（`file://` 下 ES module 与 WASM 受限），
> 必须通过本地 HTTP 服务访问。

改 UI 前先在这里看效果，确认后再落到 Kotlin 代码。


## 构建步骤

### 1. 无需下载任何运行时

Python 运行时（Pyodide / WebAssembly，约 13MB）**已随仓库提供**，位于
`app/src/main/assets/pyodide/`，构建时会自动打进 APK。clone 后即可离线构建。

> 与旧版的关键差异：此前需要联网下载 441MB 的 `usrtar.zip` 才能让 APK 跑起来，
> 现在这一步已彻底移除——装完即离线。

### 2. 补齐 SoraEditor 的语法配置

从 [sora-editor](https://github.com/Rosemoe/sora-editor) 仓库的 `language-textmate`
模块复制 `languages.json`、语法文件与主题到：

```
app/src/main/assets/textmate/
├── languages.json
├── themes/darcula.json
└── syntaxes/*.json
```

缺失时编辑器退化为纯文本（不崩）。仓库已自带一份，通常无需操作。

### 3. 构建 APK

```bash
cd dev-terminal
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+、Android SDK（`compileSdk 35`）。首次构建会下载 Gradle 与依赖，需联网；
**构建完成后，App 运行时完全不联网**。

> **构建注意**：`.wasm` 与 `.zip` 必须在 `build.gradle.kts` 中配置 `noCompress`，
> 否则 AAPT 压缩后 WebView 无法加载，点运行即失败。

### 4. 真机验证

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动后应看到：环境就绪提示 → 默认 Python 项目 → 点 ▶ 运行 → 输出面板出现结果。
Java 文件仍可打开编辑与高亮，但点运行会提示「Java 运行暂不可用」。

## 里程碑

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1 | Gradle 工程 + Compose 外壳 | ✅ |
| M2 | 离线执行引擎（流式输出） | ✅ |
| M3 | 内置离线 Python 运行时（Pyodide / WASM，随 APK） | ✅ |
| M4 | ~~Java 多文件编译 + 运行~~ → 架构限制，暂不可用 | ⚠️ |
| M5 | 交互式 stdin（`input()` 运行中可喂入） | ✅ |
| M6 | 环境自检 + 友好报错 + 运行配置 + 文件管理 + 前台服务 | ✅ |
| M7 | SAF 导入 / 导出 | ✅ |
| M8 | 设置页 + 主题切换 | ✅ |
| M9 | 静态代码补全 + 崩溃日志 + 自检脚本 | ✅ |
| M10 | 体验对标版：Tab 页 / 符号栏 / 查找替换 / 状态栏 / 会话恢复 / 输出拖高 | ✅ |
| M11 | 2026 全家桶：命令面板 ⌘K / ~~Git 面板~~ / AI 助手（BYOK）/ 全局搜索 / 代码片段 | ⚠️ |
| M12 | **真实构建出 APK**：国内镜像全链路（腾讯 SDK + 阿里云 Maven）+ 内置 13MB Pyodide 运行时 + 18 语言语法，沙盒内 assembleDebug 成功 | ✅ |
| M13 | **架构重构**：执行引擎换为 Pyodide（WASM），彻底移除 441MB 外置工具链依赖，真正做到装完即离线 | ✅ |
| M14 | **数据科学栈**：numpy / pandas 官方 wheel 随 APK 分发，`import` 即用零网络（v0.7.0） | ✅ |
| M15 | **Git 回归**：JGit（纯 Java）驱动 Git 面板全功能，依旧离线（v0.7.0） | ✅ |
| M16 | **可视化与预览**：matplotlib 离线画图 + Markdown/HTML 分屏实时预览 + 6 套编辑器主题（v0.8.0） | ✅ |

## 已知限制

- **APK 体积**：v0.8.0 debug 包实测 **53MB**（13MB Pyodide 运行时 + 数据科学与绘图 wheel
  约 61MB 原始体积压缩后约 20MB 增量 + JGit/主题/语法资源），
  装完即用、无需任何下载。不适合走 Google Play（前台服务 specialUse 声明繁琐），
  建议 F-Droid / GitCode Releases / 官网直下。
- **Java 执行不可用**：JVM 无法运行于 WebAssembly 沙箱。编辑与语法高亮正常保留。
- **性能**：WASM 沙箱执行约为原生的 1/10~1/50，脚本与学习场景无感，重计算场景会慢。
- **输入行为整行提交**：stdin 按行写入（模拟回车），暂不支持逐字符输入、方向键、
  Tab 补全等终端特性——需要完整 pty。
- **补全为静态词表**：不做语义分析，因此不能补全 `obj.` 之后的成员（需要 LSP 或类型推断）。
- **复制 / 粘贴**：编辑器本身依赖系统剪贴板，长按菜单由 SoraEditor 提供。
- **前台服务为 specialUse 类型**：上架 Google Play 需额外声明用途说明。
- ~~沙盒无法出 APK~~：**已解决**。通过国内镜像（腾讯 Gradle/SDK + 阿里云 Maven）
  在纯 Linux 沙盒内真实构建成功，详见下方「镜像构建指南」。

## 许可证

- 本项目代码：**Apache License 2.0**，见 [LICENSE](LICENSE)。
- 第三方组件声明（SoraEditor LGPL-2.1、Pyodide MPL-2.0 等）：见 [NOTICE.md](NOTICE.md)。

## 参与贡献

欢迎 Issue / PR：

1. Fork 本仓库，从 `main` 拉分支
2. 改动 Kotlin 代码后请先跑 `python3 tools/selfcheck.py`（0 错误 0 警告再提交）
3. UI 改动请先在 `design/mockup.html` 原型里验证效果（先跑 `tools/sync_design_runtime.sh`）
4. Commit 信息用祈使句，如 `Fix stdin deadlock in PyodideEngine`

## 镜像构建指南（无 Android SDK 也能出包）

本项目已在 **无 Android SDK 的纯 Linux 沙盒** 内真实构建出 APK（v0.8.0，53MB）。
构建全程使用国内镜像，无需访问 Google 服务器：

| 组件 | 镜像 |
|---|---|
| Gradle 发行包 | `mirrors.cloud.tencent.com/gradle/`（gradle-wrapper.properties 已指向） |
| AGP / AndroidX / SoraEditor | `maven.aliyun.com/repository/google` + `/public`（settings.gradle.kts 已配置） |
| Android SDK platform-35 / build-tools 34 | `mirrors.cloud.tencent.com/AndroidSDK/` |
| aapt2 / d8 / apksigner | build-tools 内置 Linux x86_64 可执行文件，直接可用 |

Python 运行时 `app/src/main/assets/pyodide/`（13MB，WASM 版 CPython 3.12 + 标准库）
与 `assets/textmate/`（18 种语言语法）**已随仓库提供**，
clone 后无需任何额外步骤即可构建。

```bash
# 三行出包（JDK 17+，SDK 位置由 local.properties 的 sdk.dir 指定）
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
```

真机安装后直接可用（运行时随包，零下载零解压），▶ 运行 `print("hello")` 即验证。
