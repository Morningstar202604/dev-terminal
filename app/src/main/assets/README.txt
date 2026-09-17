# 离线工具链目录说明

## 期望文件名
`usrtar.zip`

## ⚠️ 该文件不入库，需自行下载

本包约 **441MB**（含 CPython、openjdk-17、Git 等），超过 GitHub 单文件
100MB 硬限制，也不适合免费版 Git LFS。因此仓库中**不包含**该文件，
通过 Release 附件分发。

### 一键下载（推荐）

```bash
bash tools/fetch_toolchain.sh            # 默认从 GitCode
MIRROR=gitee  bash tools/fetch_toolchain.sh
MIRROR=github bash tools/fetch_toolchain.sh
```

脚本会下载并自动校验 SHA256：

```
7ff7f9bef166401b7aa73a212979a457181693fd995a6c058f890fe477425d06
```

### 手动下载

从任一 Release 下载 `usrtar.zip`，放入本目录即可：

- GitCode: https://gitcode.com/badhope/dev-terminal/releases
- Gitee  : https://gitee.com/badhope/dev-terminal/releases
- GitHub : https://github.com/X33834/dev-terminal/releases

## 结构

解包后对应 Termux `$PREFIX` 布局：

```
bin/python, bin/java, bin/javac, lib/, share/ ...
```

## 自行构建（不做下载时）

- 真机 Termux：`tools/extract_bootstrap.sh`
- CI 交叉编译：`tools/build_offline_bundle.sh`
