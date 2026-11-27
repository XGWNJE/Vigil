# 构建与发布手册

本页供维护者在本地执行 CI 校验、签名构建与 GitHub Release 发版；操作前遵守 [AGENTS.md](../AGENTS.md) 的密钥与文档规则。

## 本地调试构建

准备 Git、JDK 17+ 和 Android SDK Platform 35。通过 Android Studio 配置 SDK 路径，或设置 `ANDROID_HOME` 指向 SDK 目录。在仓库根目录运行 `./gradlew assembleDebug`（Windows PowerShell 可用 `.\gradlew.bat assembleDebug`）。成功后 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 本地 CI 与 Git 钩子

- **一键本地 CI**：在仓库根目录运行 `.\scripts\local-ci.ps1`（发版前可加 `-Tag vX.Y.Z`）：
  1. `scripts/check-docs-sync.py`：核对 `README.md` 与 Kotlin 源码常量/UI 文案一致、链接与锚点有效、`ROADMAP.md` 版本号同步，以及仓库未追踪密钥文件。
  2. `scripts/check-version.py --tag vX.Y.Z`（传入 `-Tag` 时）：核对 tag 与 `app/build.gradle.kts` 的 `versionName` 一致，且 `release-notes/vX.Y.Z.md` 存在并符合模板。
  3. `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`（传 `-SkipLint` 可跳过 lint）。
- **Git `pre-push` 钩子**：运行 `.\scripts\install-hooks.ps1` 将仓库钩子目录设为 `scripts/hooks`。推送任意分支前自动执行文档/密钥快检；推送 `v*` 发布 tag 时自动触发完整 `local-ci.ps1 -Tag <tag>`，不通过则阻止推送。

## 本地签名与证书指纹

- **密钥存放**：正式签名采用本地 `keystore/vigil.keystore`（PKCS12，alias `vigil`）及 `keystore.properties`，两者均已加入 `.gitignore`，**严禁提交入库**，维护者须自行离线妥善备份。
- **当前签名证书指纹（2026-11 本地新签）**：
  - SHA-256：`5c4e67f9965db0cc3db472583223d8c110fe326a87b0e9894143593b8f71ffb2`
  - 注意：因 2026-11 起由 GitHub Actions 切换为本地签名证书，从 `v1.23.0` 及更早版本升级时需先卸载旧版一次，后续本地发版均可正常覆盖安装。

## 本地发布标准流程

owner 明确授权发版时，按以下顺序在本地执行（已取消 GitHub Actions 发布 workflow）：

1. **收口待发版改动**：递增 `app/build.gradle.kts` 的 `versionCode` 与 `versionName`；按 `release-notes/TEMPLATE.md` 固定格式编写 `release-notes/vX.Y.Z.md`；同步对齐 [README.md](../README.md)、[ROADMAP.md](../ROADMAP.md) 及相关文档，并提交到本地 `main`。
2. **预演校验（推荐）**：运行 `.\scripts\release-local.ps1 -DryRun`，完整验证本地 CI、`assembleRelease` 签名构建与 `apksigner` 证书指纹比对（不推送也不创建 Release）。
3. **正式发布**：运行 `.\scripts\release-local.ps1`（需保持 `main` 工作区干净且 `gh` 已登录）：
   - 自动执行 `local-ci.ps1 -Tag vX.Y.Z`
   - 本地执行 `.\gradlew.bat :app:assembleRelease` 并用 `apksigner` 核对 APK 证书 SHA-256 与 `keystore/vigil.keystore` 一致
   - 推送 `main` 与 `vX.Y.Z` tag 至远端
   - 调用 `gh release create vX.Y.Z` 上传 `vigil-vX.Y.Z.apk` 并附带 `release-notes/vX.Y.Z.md`，最后校验 Release 资产就绪。

## 商店提交资料

中英文案、权限说明和 Feature Graphic 见[商店素材](../store/README.md)；账号操作见[上架清单](../store/owner-checklist.md)，数据处理说明见[隐私政策](../PRIVACY.md)。
