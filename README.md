<div align="center">

# 📱 DevTerminal

**真正离线的安卓 Python IDE — 手机上写代码，装完就能跑**

Python 运行时（Pyodide / WebAssembly）随 APK 打包，零下载、零网络、零广告。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin%202.0%20%2B%20Compose-7F52FF?style=flat-square)](https://kotlinlang.org)
[![Offline](https://img.shields.io/badge/%E8%BF%90%E8%A1%8C%E6%97%B6-100%25%20%E9%9A%8F%E5%8C%85-orange?style=flat-square)]()
[![No Ads](https://img.shields.io/badge/%E5%B9%BF%E5%91%8A-0%20%E6%9D%A1-red?style=flat-square)]()

**[下载 APK](https://gitcode.com/badhope/dev-terminal/releases)** · **[官网](https://x33834.github.io/dev-terminal/)** · **[Gitee 镜像](https://gitee.com/badhope/dev-terminal)**

</div>

---

## ✨ 为什么是它

| | Termux | Pydroid 3 | Acode | Spck | **DevTerminal** |
|---|:---:|:---:|:---:|:---:|:---:|
| 装完即离线 | ⚠️ 要装包 | ⚠️ 部分 | ❌ 联网 | ❌ 联网 | ✅ **运行时随包** |
| 现代 IDE 界面 | ❌ 黑框 | ⚠️ 旧 | ✅ | ✅ | ✅ |
| 多文件 Tab / 查找替换 | ❌ | ✅ | ✅ | ✅ | ✅ |
| Git 离线集成 | ✅ | ❌ | ✅ | ✅ | ✅ **JGit** |
| numpy / pandas / matplotlib | ❌ | ✅ | ❌ | ❌ | ✅ **预装** |
| AI 助手（BYOK） | ❌ | ❌ | ✅ 云端 | ✅ 云端 | ✅ **自配端点** |
| 开源无广告 | ✅ GPL | ❌ 付费 | ⚠️ 广告 | ⚠️ 内购 | ✅ **Apache-2.0** |

> 我们的定位：**开源 + 无广告 + 装完即离线 + 数据科学栈预装**。

---

## 🏗 架构

```
┌─────────────────────────────────────────────┐
│  Compose UI 层                               │
│  文件树 · 编辑器(Sora) · 输出面板 · 命令面板    │
└──────────────────┬──────────────────────────┘
                   │ JavascriptInterface
┌──────────────────▼──────────────────────────┐
│  Pyodide Engine（隐藏 WebView）               │
│  CPython 3.12 编译为 WebAssembly             │
│  流式 stdout/stderr · 超时中断 · stdin 交互   │
└──────────────────┬──────────────────────────┘
                   │ https://devterminal.local
┌──────────────────▼──────────────────────────┐
│  assets/pyodide/（随 APK，零下载）            │
│  pyodide.asm.wasm · stdlib.zip · 11 个 wheel │
└─────────────────────────────────────────────┘
```

**核心取舍**：不自造终端模拟器，不依赖外置原生工具链。用 Pyodide 把 CPython 编译成 WASM，随 APK 打包——装完即离线。代价是 WASM 沙箱里跑不了 JVM（Java 仅保留编辑高亮）。

---

## 🎯 功能一览

### 写代码
- **多文件 Tab** · **文件树**（新建/重命名/删除，长按操作）
- **Sora Editor** 代码编辑器 + 18 种语言 TextMate 语法高亮
- **静态补全**：Python 关键字/内置函数 + 当前文件符号提取（零进程开销）
- **查找替换**（计数跳转）· **全局搜索**（跨文件）
- **快捷符号栏** 26 键 · **双指缩放字号** · **自动闭合括号** · **自动换行** · **行号开关**
- **6 套编辑器主题**（Darcula / Monokai / 明日蓝 / Solarized…）

### 跑代码
- **Pyodide WASM CPython**，标准库全量
- **numpy / pandas / matplotlib / pillow** 预装，import 即用
- **流式输出** · **`input()` 交互** · **方向键/Tab 软键盘**
- **死循环保护**（超时中断）· **前台服务保活**
- **matplotlib savefig PNG**，图片文件点开直接看
- **运行配置**（命令行参数）· **友好报错翻译**（20+ 高频错误）

### 管项目
- **多项目切换**（长按删除）· **4 个 Python 模板**
- **JGit Git 集成**：init / commit / push / pull，纯 Java 离线
- **SAF 导入导出**（无需存储权限）
- **离线包管理**：查看内置 wheel / 导入本地 .whl

### 看结果
- **Markdown / HTML 分屏实时预览**，防抖刷新
- **外部浏览器打开** HTML 文件
- **输出面板**：拖拽调高 / 复制全部 / 分享
- **三态主题**（跟随系统 / 浅色 / 深色）

### 提效
- **命令面板 ⌘K**：搜索一切动作 + 代码片段
- **AI 助手**（BYOK，OpenAI 兼容端点）：解释文件 / 修错 / 生成测试
- **会话恢复**：重启回到上次编辑现场
- **崩溃日志**写私有目录，离线可反馈

---

## 🚀 构建

```bash
# JDK 17+，clone 后三行出包
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行时（13MB Pyodide WASM）已随仓库提供，构建完成后 **App 运行零网络**。

> `.wasm` 与 `.zip` 必须配置 `noCompress`，否则 AAPT 压缩后 WebView 加载失败。

---

## 📦 已预装的离线包

`numpy 1.26` · `pandas 2.2` · `matplotlib 3.5` · `pillow 10.2` · `python-dateutil` · `pytz` · `six` · `cycler` · `kiwisolver` · `packaging` · `pyparsing` · `fonttools`

代码里 `import numpy` 即用，无需联网。包管理对话框可查看全部 / 导入额外 wheel。

---

## 🧭 能力边界（诚实标注）

| 能力 | 状态 |
|---|---|
| Python 运行 | ✅ 完整（WASM CPython 3.12） |
| Java 运行 | ❌ JVM 无法在 WASM 沙箱运行（保留编辑高亮） |
| 性能 | ⚠️ 约为原生 1/10–1/50，脚本/学习场景无感 |
| `plt.show()` | ⚠️ 用 `plt.savefig("x.png")`，沙箱无窗口 |
| pip 联网装包 | ❌ 离线定位，额外库需手动导入 wheel |

---

## 📄 许可证

[Apache License 2.0](LICENSE) · 第三方组件声明见 [NOTICE.md](NOTICE.md)

贡献前跑 `python3 tools/selfcheck.py`（0 错误再提交）。
