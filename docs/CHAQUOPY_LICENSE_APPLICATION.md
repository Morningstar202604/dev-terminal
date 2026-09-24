# Chaquopy 开源 License 申请（阶段 1 前置）

> 状态：待用户发送申请邮件。收到 Chaquo 回复并激活 appId 后，本仓库才能用 Chaquopy 完成构建。

## 为什么需要申请

Chaquopy（原生 CPython 的 Android 嵌入方案）对**开源 App 永久免费**，
但首次构建前必须让 Chaquo 团队激活你的 applicationId（免费，通常 1–3 天回复）。
不激活也能开发调试，但正式出包会被构建插件拦截。

## 需要的信息（已从本仓库确认）

| 字段 | 值 |
|---|---|
| App ID（applicationId） | `com.devterminal` |
| 分发渠道 | GitCode（公开仓库） |
| 源码地址 | https://gitcode.com/badhope/dev-terminal |
| 开源协议 | Apache-2.0（仓库内 LICENSE） |

## 申请邮件模板

收件人：`support@chaquo.com`

主题：`Open-source license activation request for com.devterminal`

正文：

```
Hello Chaquo team,

We would like to activate the open-source license for our app:

- App ID: com.devterminal
- Distribution: GitCode (public repository), self-distributed APK
- Source code: https://gitcode.com/badhope/dev-terminal
- License: Apache-2.0

The app is an offline Android Python IDE. We plan to embed a native
CPython runtime (via Chaquopy) to replace our current Pyodide/WASM
backend, so that input(), interruption and real file I/O work as
expected on device.

Please activate the license for this app ID. Thank you!

Best regards,
DevTerminal maintainers
```

## 激活后的操作

收到确认后，在 `gradle/libs.versions.toml` 加入 Chaquopy 插件
（国内镜像已确认可拉取，最新 17.0.0 / 稳定 16.1.0），
然后按 REFACTOR_SPEC 阶段 1 清单执行迁移。
