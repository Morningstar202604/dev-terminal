# 更新日志（Changelog）

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [0.7.0] — 2026-09-22

### 新增
- **数据科学栈离线内置**：随 APK 打包 numpy 1.26.4 / pandas 2.2.0 官方 Pyodide wheel
  （含 python-dateutil / pytz / six，共约 37MB，sha256 与 lock 完全一致）。
  运行器在执行用户代码前用 `loadPackagesFromImports` 按 import 自动装载——
  写 `import pandas` 即用，全程零网络；看门狗在装载完成后才启动，不侵占用户超时。
  > 端到端验证：无头 Chromium（与 Android WebView 同内核）实测本地装载 0.7s，
  > DataFrame 统计与 numpy 线性代数结果正确。
- **Git 能力回归（JGit）**：引入 `org.eclipse.jgit`（纯 Java，EDL-1.0），
  重写 `GitManager`：init（main 分支）/ status（分支、变更、ahead/behind、最近提交）/
  commitAll / push / pull 全部在应用进程内完成，依旧离线；远程 URL 支持内嵌 token，
  结果回显只显示主机名，**不回显 token**。UI 层零改动。
- 环境自检补齐「数据科学库」「Git 操作」两项能力标注。

### 修复
- **AI「已配置」判定恒为真**：`aiConfigured` 原为「端点非空」，而默认值是占位的
  本机 Ollama 地址（恒非空），导致「未配置」引导永不出现、AI 动作直接发请求必败。
  改为显式持久化标志（`KEY_AI_CONFIGURED`），仅在设置页点「保存 AI 配置」时置真；
  未配置时 AI 面板禁用输入与「发送」，占位文案给出引导。
- 关于页过时文案：删除「内置 Python / Java 工具链」「Termux packages」，
  改为 Pyodide / CPython 实况，许可列表同步更新。

### 变更
- 版本号 0.7.0（versionCode 8）；APK 体积约 32MB → 约 44MB
  （37MB wheel 在 APK 内压缩后约 11MB 增量 + JGit 约 1MB）。

### 测试
- 新增 `GitManagerTest`（6 例，纯 JVM）：真实建仓 / 提交 / 状态读取；
  顺带修出真 bug——刚 init 未提交时 `log()` 抛 NoHeadException 导致 status 误报
  「不是仓库」。全部 23 例通过。

## [0.6.0] — 2026-09-22

### 重构（核心：把「离线」从宣传变成事实）
- **执行引擎整体替换为 Pyodide（WebAssembly）**：新增 `PyodideEngine.kt`，
  以内嵌 WebView + `WebViewAssetLoader` 承载 WASM 版 CPython，
  运行时（`pyodide.asm.wasm` + `python_stdlib.zip`，约 13MB）随 APK 打包，**装完即离线**。
- **删除旧的原生工具链方案**：移除 `TermuxEngine.kt` 与 `EnvironmentInstaller` 中
  的 `usrtar.zip` 解压逻辑、Termux 环境变量模拟等全部死代码。
  > 旧方案要求一个 441MB 的 `assets/usrtar.zip`，该文件不入库、必须联网下载，
  > 导致 clone 源码构建出的 APK 根本跑不了代码——「离线」名不副实。
- **新增运行器页面** `app/src/main/assets/python-runner.html`：
  移植自 `design/pyworker.js` 的成熟逻辑（流式 UTF-8 解码、traceback 内部帧清洗、
  setup 与用户代码分离以避免行号偏移、中断缓冲超时）。

### 新增
- **`input()` 运行中交互**：JS 侧输入队列 + `pushInput` 桥，程序阻塞在 `input()` 时
  可被 Kotlin 侧实时唤醒，不再是"仅支持预喂"。
- **环境自检改为校验运行时完整性**：`EnvDiagnostics` 现在逐个检查 APK 内
  Pyodide 资源是否齐备，并明确标注各项能力（Python 可用 / Java 不可用）。
- **构建约束固化**：`build.gradle.kts` 增加 `noCompress += listOf("wasm", "zip")`，
  避免 AAPT 压缩导致 WebView 无法加载 WASM；新增 `androidx.webkit` 依赖。

### 变更（能力边界，诚实标注）
- **Java 执行下线**：JVM 无法运行于 WebAssembly 沙箱。保留编辑与语法高亮，
  运行时给出明确说明而非报错崩溃。
- **Git 操作下线**：原依赖工具链内的 git 二进制。保留 UI 与接口，
  后续可通过 JGit（纯 Java 实现）恢复。
- README 全面校正：删除"Python + Java 全程零网络""本机 Ollama 离线 AI"
  等与实现不符的宣称，新增「能力边界」章节。

### 说明
- Java 与 Git 的恢复属于后续独立阶段的工作，本轮聚焦「让 Python 真正离线可跑」。

## [0.5.0] — 2026-09-17

### 新增
- **离线真实 Python 运行引擎（设计稿验证版）**：`design/mockup.html` 接入 Pyodide 0.26.4
  （本地 vendor 到 `design/pyodide/full/`，Web Worker 架构），实现真实 stdout/stderr 捕获、
  完整 traceback、`input()` 预喂、多文件 `import`、超时中断。
- **内置题库（11 道真实可运行题目）**：素数筛 / 递归栈溢出 / 语法错误 / 除零 / KeyError /
  多文件模块导入 / 5000 行长输出 / 死循环超时 / 文件读写 / 中文 Emoji / 交互式问答。
- **报错行点击跳转**：真实 traceback 进入输出面板即自动变为可点击行，点击后编辑器滚动并高亮
  对应源码行；与 Kotlin 端 `parseErrorLine`（`app/.../OutputPanel.kt`）正则逐字符一致。
- **端到端测试脚手架**：`tools/serve_design.py`（多线程静态服务）、`tools/test_suite.py`
  （Playwright 驱动逐题真实运行 + 断言 + 截图 + 录屏）、`tools/test_ui.py`（UI 交互覆盖）、
  `tools/build_promo.py`（宣传片生成）。

### 修复
- **Pyodide worker 超时提示字符串拼接错误**：原 `'⏱ 已超时取消（" + sec + 's）…'` 拼接语法错误，
  `sec` 不被插值；改为正确拼接。
- **traceback 内部帧清洗**：过滤 Pyodide 注入的 `_pyodide/_base.py`、`CodeRunner` 等栈帧，
  避免被 `parseErrorLine` 误判为可跳转行。
- **中文/Emoji 乱码**：`raw` 回调返回 UTF-8 字节流，改用 `TextDecoder('utf-8')` 流式解码。
- **多文件 import 失败**：`runPythonAsync` 不自动把 cwd 加入 `sys.path`，setup 阶段补 `sys.path.insert`。
- **报错行号偏移**：setup 与用户代码分离为两次 `runPythonAsync`，避免 setup 撑偏行号。

### 测试
- 端到端全量测试 **11/11 通过**（约 44s，含首次解释器预热）；
  逐题验证真实输出、报错行可跳转、中文无乱码、死循环 3s 安全终止。
- 交付：宣传片 + 题库遍历录屏 + UI 交互录屏 + 27 张截图 + 结构化测试报告（见 `test_results/`）。

### 构建
- versionCode 5 → 6，versionName 0.4.1 → 0.5.0。
- 说明：本轮核心改动落在 `design/` 设计稿与测试工具链，**原生 APK 未集成 Pyodide**
  （APK 仍走 `usrtar.zip` + `TermuxEngine` 的本地 Linux 用户空间方案）。

## [0.4.1] — 2026-09-16

### 新增
- **横屏两栏布局**：编辑器在左（64%）、输出面板在右（36%），中间细分隔线；
  内容抽成局部 Composable 复用，拖拽把手仅在竖屏显示。
  设计稿的 grid 方案（design/mockup.html）先行验证过收益：横屏下代码可见高度从 0 恢复到 180dp
- **JUnit 单元测试**：`ParseErrorLineTest` 17 个用例锁定报错行解析
  （Python traceback / javac / flake8 / 路径前缀 / 版本号与时间戳误报 / Int 溢出等），
  与 `tools/tests/test_parse_error_line.py` 用例一一对应，跑 `./gradlew :app:testDebugUnitTest`

### 构建
- versionCode 4 → 5（0.4.0 的 APK 无横屏两栏，避免同版本号两种内容）
- 排查：多个 Gradle daemon 并存时会复发 usrtar.zip 的 MD5 哈希竞态
  （本次发生在 `packageDebug`），`./gradlew --stop` 后单实例构建即恢复稳定

## [0.4.0] — 2026-09-16

### 构建（技术栈全面升级）
- **Kotlin 2.0.21（K2 编译器）**：Compose 编译器改为独立插件 `org.jetbrains.kotlin.plugin.compose`，
  移除 `kotlinCompilerExtensionVersion`
- **AGP 8.7.3 / Gradle 8.11.1 / compileSdk & targetSdk 35**（Android 15）
- **Compose BOM 2024.12.01**（material3 1.3.1）、SoraEditor 0.23.6、
  core-ktx 1.15.0 / activity-compose 1.9.3 / lifecycle 2.8.7、desugar 2.1.4
- **Version Catalog**（`gradle/libs.versions.toml`）：全部依赖版本集中管理
- `org.gradle.parallel=false`：规避 Gradle 8.11 并行执行对 461MB usrtar.zip 的哈希读取竞态

### 新增（使用逻辑）
- **报错行可点击跳转**：输出面板解析 `line N` 与 `file.ext:N` 两类报错行（0 基换算、
  跳过 💡 提示与 —— 分隔线），点击后编辑器光标直达对应行；
  `ScrollToLineRequest` 用 seq 自增保证连点同一行也响应
- **首启引导空态**：无项目时展示「写代码，不用联网」+ 新建 / 导入双入口；
  有项目未开文件时改为「选一个文件开始」
- **弹窗状态收敛为 `Overlay` 密封接口**：替换 9 个独立布尔值，
  编译期保证同一时刻至多一个面板
- **返回键三层处理**：搜索面板 → 抽屉 → 弹窗，逐层退出

### 变更（前端）
- **Motion 动效体系**（`theme/Motion.kt`）：统一时长（120/220/320ms）与缓动曲线
  （Emphasized Decelerate / Accelerate），拖动跟随用 NoBouncy spring
- **触觉反馈**（`components/Haptics.kt`）：运行结束按退出码区分成功（CONFIRM，
  API<30 回退 VIRTUAL_KEY）/ 失败（LONG_PRESS）
- **触控尺寸**：图标按钮最小 48dp，符号栏键帽 32→38dp
- 输出面板高度上限改为按可用高度 62% 动态计算（横屏 / 小屏不再挤没编辑器）

### 文件管理
- 文件树默认展开第一层，折叠目录显示子项数量
- 抽屉标题栏提示「长按可重命名」

### 设计
- `design/mockup.html` 与 App 行为对齐：首启引导可视化、报错行点击跳转演示
  （utils.py 第二次运行）、浅色主题（色值逐项对齐 `Theme.kt` 的 QuietLight）
- 修复设计稿 4 处缺陷：跳转重建 DOM 冲掉语法高亮、行号高亮残留、
  引导页盖住状态栏、行内高度压过媒体查询
- 补齐响应式媒体查询：矮屏压缩输出面板、横屏两栏网格
- 新增 21 张高清截图（深色 15 + 浅色 6）、33.6s 交互录屏 `design/demo.mp4`

## [0.3.0] — 2026-09-14

### 新增
- **命令面板 ⌘K**：顶栏命令图标唤出，搜索直达所有功能（运行 / Git / AI / 片段 / 设置），Cursor 范式
- **Git 面板**：状态 / 初始化 / 提交 / 推送 / 拉取，直接调用内置 git 二进制（离线可用）；
  报错翻译（403→令牌、rejected→先拉取）；Git 身份与远程 URL 配置
- **AI 助手（可选）**：OpenAI 兼容协议，默认端点本机 Ollama（本地推理 = 离线可用）；
  快捷动作：解释此文件 / 修复运行错误 / 生成测试 / 加注释；
  「修复运行错误」自动携带最近运行的报错输出与当前文件上下文
- **全局搜索**：跨文件搜索（防抖 400ms、上限 200 条），命中行点击直达
- **代码片段库**：12 个骨架模板（Python 主入口 / 类 / dataclass / 装饰器，
  Java POJO / Stream / 正则等），从命令面板插入光标处
- **双指缩放**：编辑器字号随捏合手势调整（10–24sp，实时持久化）
- **输出分享**：系统 ShareSheet 分享运行结果
- 设置页新增 AI 端点配置（URL / 模型 / Key）

### 变更
- Manifest 增加 `INTERNET` 权限：仅 AI 可选用途，未配置端点时 App 不发起任何网络请求

## [0.2.0] — 2026-09-14

### 新增
- **多文件 Tab 条**：一键切换，关闭自动切相邻（对标 VS Code / Acode）
- **快捷符号栏**：26 编程符号 + Tab / 退格，插入走编辑器撤销栈（对标 Pydroid 3 / Spck）
- **查找 / 替换**：实时匹配计数、跳转滚动定位、单个 / 全部替换
- **会话恢复**：重启回到上次项目 / Tab 列表 / 编辑文件
- **输出面板拖拽调高**（120–560dp），高度持久化
- **底部状态栏**：Ln/Col、语言、字符数、保存状态

### 构建（v0.3.0 实测产出 APK）
- **真实构建成功**：纯 Linux 沙盒（无 Android SDK、无 Google 网络）内 `assembleDebug` 出包，
  产物 47MB debug APK（com.devterminal 0.3.0 / minSdk 24 / targetSdk 34）
- **内置离线工具链 usrtar.zip**：从 Termux F-Droid 官方 bootstrap（arm64 v1022，sha256 校验）
  + termux-main 仓库 BFS 解析 17 个依赖包合并而成；Python 3.14.6、bash、coreutils、curl、
  tar、sqlite 等共 1745 个文件，symlink 全部物化、ELF 依赖静态校验通过
- **内置语法高亮资源 textmate/**：18 种语言（py/java/kt/js/ts/html/css/json/md/sh/c/cpp/go/
  rs/sql/yaml/xml/lua）29 个扩展名条目，含 darcula 主题
- 开启 core library desugaring（language-textmate 0.23.5 要求）
- 构建镜像化：Gradle 8.9 → 腾讯云；Maven 依赖 → 阿里云；SDK 组件 → 腾讯云 AndroidSDK 镜像

### 修复
- 输出面板由固定 260dp 改为可调，长输出不再挤压

## [0.1.0] — 2026-09-14

### 首个版本
- Kotlin + Jetpack Compose + Material 3 完整工程
- 离线执行引擎：ProcessBuilder + termux 环境变量模拟，工具链预打包零网络
- Python 交互式 stdin（`input()`）、Java 多文件编译运行
- 环境自检、友好报错（20+ 高频错误翻译）、运行配置、文件管理、前台服务保活
- SAF 导入导出、设置页、静态补全、崩溃日志
- 高保真可交互 UI 原型（design/mockup.html）与静态自检脚本
