# 第三方组件声明（NOTICE）

本产品包含或依赖以下第三方开源组件，在此致谢并声明其许可证：

## 1. SoraEditor（代码编辑器组件）

- 项目地址：https://github.com/Rosemoe/sora-editor
- 许可证：**LGPL-2.1**
- 使用方式：通过 Maven（`io.github.Rosemoe.sora-editor:editor / language-java / language-textmate`）
  以公开 API 动态链接。
- 合规说明：本项目源码已按 Apache-2.0 开源，符合 LGPL 对动态链接的要求；
  用户可自行替换该库。若你 fork 本项目后闭源分发，请保证最终用户能获得
  或替换该库，并保留本声明。

## 2. Pyodide（Python 运行时，WebAssembly）

- 项目地址：https://github.com/pyodide/pyodide
- 许可证：**MPL-2.0**（Pyodide 自身）；其内置的 CPython 为 **PSF License**，
  标准库与预编译包各自遵循上游许可证。
- 使用方式：运行时资源（`pyodide.asm.wasm`、`python_stdlib.zip`、`pyodide.mjs` 等，
  约 13MB）随本仓库提供，位于 `app/src/main/assets/pyodide/`，由 AAPT 打进 APK。
- 合规说明：Pyodide 以 MPL-2.0 分发，本项目的源码形态已随仓库完整提供；
  Pyodide 及其捆绑组件以**未修改的原始形式**再分发。
  若你分发 APK，请保留本声明，并在 App 内提供「开源许可」入口（建议）。
- 说明：Python 由 WebAssembly 沙箱执行，**不包含** Termux 或任何原生 Linux 用户空间二进制。

## 3. 预打包 Python 库（numpy / pandas 及其依赖）

- 组件与许可证：**numpy（BSD-3）**、**pandas（BSD-3）**、
  python-dateutil（Apache-2.0 / PSF 双许可）、pytz（MIT）、six（MIT）。
- 使用方式：官方 Pyodide 发行版的预编译 wheel（含 wasm32 二进制），位于
  `app/src/main/assets/pyodide/`，随 APK 分发；运行时按用户代码的 import
  自动装载（`loadPackagesFromImports`），全程零网络。
- 合规说明：wheel 以**未修改的原始形式**（sha256 与 Pyodide 0.26.4 lock 完全一致）再分发。

## 4. JGit（Git 实现，纯 Java）

- 项目地址：https://www.eclipse.org/jgit/
- 许可证：**EDL 1.0**（Eclipse Distribution License，即 BSD-3-Clause）。
- 使用方式：通过 Maven（`org.eclipse.jgit:org.eclipse.jgit`）以公开 API 动态链接，
  提供 init / status / commit / push / pull 能力，无外部二进制依赖。
- 附带组件：SLF4J（`slf4j-api` / `slf4j-nop`，MIT）。

## 5. AndroidX / Jetpack Compose / Kotlin / androidx.webkit

- 许可证：Apache-2.0
- 通过 Maven Central 分发的官方 Android 组件。

---

本项目自身代码以 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。
