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

## 2. 内置离线工具链（usrtar.zip，构建时自行生成）

- 来源：基于 [Termux](https://github.com/termux/termux-packages) 生态的预编译二进制
  （Python / OpenJDK / Git 等，不含 Termux App 代码）。
- 许可证：各组件各自的开源许可证（Python PSF、OpenJDK GPLv2+Classpath、
  Git GPLv2、OpenSSL Apache-2.0 等）。
- 合规说明：`usrtar.zip` 不在本仓库中分发，由使用者按 `tools/` 下脚本自行构建；
  若你分发含工具链的 APK，请随包附上相应许可证文本（建议在 App 内提供
  「开源许可」页面）。

## 3. AndroidX / Jetpack Compose / Kotlin

- 许可证：Apache-2.0
- 通过 Maven Central 分发的官方 Android 组件。

---

本项目自身代码以 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。
