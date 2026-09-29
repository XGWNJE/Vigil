# 构建与发布手册

本页供维护者执行签名构建与 GitHub 发版；发布前遵守 [AGENTS.md](../AGENTS.md) 的密钥与文档规则。

## 本地调试构建

准备 Git、JDK 17 和 Android SDK Platform 35。通过 Android Studio 配置 SDK 路径，或设置 `ANDROID_HOME` 指向 SDK 目录。在仓库根目录运行 `./gradlew assembleDebug`（Windows PowerShell 可用 `./gradlew.bat assembleDebug`）。成功后 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；失败时先核对 JDK、Android SDK 和 Gradle 报错。

## 发布

- 默认由 GitHub CI 构建、签名并创建 Release。`VIGIL_KEYSTORE_BASE64` 由 CI 在临时 runner 上解码成 `keystore/vigil.keystore`；仓库和普通本地检出没有密钥文件是正常的，不影响 debug 构建。
- 只有确需本地 release 构建且维护者已从安全备份提供密钥时，才在仓库根目录运行 `./gradlew assembleRelease`。此构建启用代码与资源压缩；签名配置在 `app/build.gradle.kts`，密码从 gitignored 的 `keystore.properties` 或环境变量读取。本地没有密钥时，不应把 release 构建失败当作仓库缺陷。
- 发版动作：递增 `versionCode`/`versionName`（README 已无更新日志板块，无需维护变更记录）。
- CI：`.github/workflows/release.yml` 在推 `v*` tag 时自动构建 release APK 并创建 GitHub Release；依赖仓库 secrets `VIGIL_KEYSTORE_BASE64`（keystore base64）、`VIGIL_STORE_PASSWORD`、`VIGIL_KEY_ALIAS`、`VIGIL_KEY_PASSWORD`。

### GitHub 发布标准流程（owner 明确说"发布到 GitHub"时默认执行，无需逐步再确认）

owner 的发布指令即授权该流程内的全部 git 操作（commit / tag / push）。按序执行：

**铁律：Release 只由 CI 创建**——发版 = 推 tag 触发 workflow，禁止手动 `gh release create` / 网页建 Release / 手动传 APK 资产。手动创建会抢占同名 Release，CI 最后一步必报 `a release with the same tag name already exists`（2026-08-01 v1.11.0 教训：手动抢建导致 CI 失败；处置 = 删手动 Release（保留 tag）→ `gh run rerun` 让 CI 接管）。

1. **收口待发版改动**：按[项目细则第 6 条](project-rules.md)对齐文档，确认 `versionCode`/`versionName` 已递增；按 `release-notes/TEMPLATE.md` 固定格式写 `release-notes/vX.Y.Z.md`（CI 自动用作 Release 描述，缺失时回退 `--generate-notes` 只有 compare 链接）；提交并 `git push origin main`。
2. **检查 CI secrets**：`gh secret list --repo XGWNJE/Vigil` 必须有上述 4 个。缺失则按下方「secrets 重设」补齐（2026-07-30 教训：密钥轮换后 secrets 全缺，v1.8.2/v1.8.3 发版失败，报 `Tag number over 30 is not supported`）。
3. **打 tag 触发**：`git tag vX.Y.Z`（与 versionName 一致）→ `git push origin vX.Y.Z`。
4. **盯运行**：`gh run watch --exit-status` 盯到结束；失败用 `gh run view <id> --log-failed` 定位，修复后删远端 tag 重推（`git push origin :vX.Y.Z && git push origin vX.Y.Z`）或打新 tag。
5. **验收（全部通过才算完成）**：`gh release view vX.Y.Z` 确认 Release 与 APK 资产存在；下载 APK 跑 `apksigner verify --print-certs`，证书 SHA-256 应与可信保存的轮换后签名证书指纹一致。若维护者有安全保管的密钥备份，可用 `keytool -list` 比对；普通本地检出不要求存在密钥文件。

### CI secrets 重设（缺失时）

```bash
# VIGIL_KEYSTORE_FILE 指向维护者安全保管的轮换后密钥备份（仓库外）
# base64 单行编码，上传前本地回环验证（base64 -d 后与源文件 cmp 一致）
base64 -w 0 "$VIGIL_KEYSTORE_FILE" > /tmp/ks.b64
base64 -d /tmp/ks.b64 | cmp "$VIGIL_KEYSTORE_FILE" - && gh secret set VIGIL_KEYSTORE_BASE64 --repo XGWNJE/Vigil < /tmp/ks.b64
rm -f /tmp/ks.b64
# 密码从维护者安全保管的凭据取得，printf 管道传入不回显；alias 固定 vigil
printf '%s' "$pw" | gh secret set VIGIL_STORE_PASSWORD --repo XGWNJE/Vigil
printf '%s' "$kp" | gh secret set VIGIL_KEY_PASSWORD --repo XGWNJE/Vigil
printf '%s' "vigil" | gh secret set VIGIL_KEY_ALIAS --repo XGWNJE/Vigil
```

## 商店提交资料

中英文案、权限说明和 Feature Graphic 见[商店素材](../store/README.md)；账号操作见[上架清单](../store/owner-checklist.md)，数据处理说明见[隐私政策](../PRIVACY.md)。
