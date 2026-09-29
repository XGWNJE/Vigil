# Vigil 上架执行清单

本清单记录需要维护者本人用开发者账号完成的事项。上架文案和权限说明见 [store/README.md](README.md)，隐私政策见 [PRIVACY.md](../PRIVACY.md)，截图见 [screenshots/](../screenshots/)。

商店规则、费用和表单会变化；执行前以各平台当前页面为准。不要从旧文案复制“无联网权限”或“锁屏自动弹出全屏通知”：当前版本会访问 GitHub 检查更新，报警通知需点击进入应用。

## 一、GitHub 自动发版

- 在仓库的 [Actions secrets 页面](https://github.com/XGWNJE/Vigil/settings/secrets/actions)核对 `VIGIL_KEYSTORE_BASE64`、`VIGIL_STORE_PASSWORD`、`VIGIL_KEY_ALIAS` 和 `VIGIL_KEY_PASSWORD`；密钥文件和密码不得提交。
- 发版操作与签名验收以 [AGENTS.md 的发布流程](../AGENTS.md)为准。GitHub Release 由 CI 在推送版本 tag 后创建。

## 二、Google Play

1. 在 [Play Console](https://play.google.com/console)注册并验证开发者账号，查看当前账号费用和身份验证要求。
2. 创建应用，按 [store/README.md](README.md) 填写中英文介绍、权限用途、截图和 Feature Graphic；隐私政策链接使用 `https://github.com/XGWNJE/Vigil/blob/main/PRIVACY.md`。
3. 逐项填写数据安全问卷。关键词匹配在设备本地，但更新检查会请求 GitHub，不能直接照抄“完全离线／不联网”。数据收集与共享的判定以 [Google Play 的 Data safety 指南](https://support.google.com/googleplay/android-developer/answer/10787469) 和待提交 APK 的实际行为为准。
4. 构建用于 Play 的 AAB：`./gradlew bundleRelease`（Windows PowerShell 用 `./gradlew.bat bundleRelease`）；成功后文件位于 `app/build/outputs/bundle/release/app-release.aab`。发布签名配置见 [AGENTS.md](../AGENTS.md#发布)。
5. 若使用 **2023 年 11 月 13 日之后创建的个人账号**，当前 [Google Play 测试要求](https://support.google.com/googleplay/android-developer/answer/14151465) 是至少 12 名测试者连续加入封闭测试 14 天，之后才能申请正式发布权限；以账号内提示为准。
6. 根据 Play Console 的审核结果修订资料和构建产物，提交正式发布。

## 三、国内应用商店

1. 分别查看目标商店对开发者实名认证、软件著作权材料、App 备案和敏感权限说明的当前要求；涉及付费和实名的步骤由维护者本人办理。
2. 准备 release APK、[图标](../app/src/main/ic_launcher-playstore.png)、[截图](../screenshots/)、[商店文案](README.md)与[隐私政策](../PRIVACY.md)。
3. 按各商店的实际表单逐项填报。说明通知使用权只用于本地关键词匹配，同时如实说明 GitHub 更新请求；不要声称应用完全不能联网。
4. 保存各平台的审核反馈、备案与上架状态；被退回时以具体原因和最新构建产物为依据修改。

## 进度记录

| 事项 | 状态 | 已有记录 |
| --- | --- | --- |
| GitHub CI secrets | 曾完成 | 2026-07-30 配置；v1.19.0 的 CI 签名发版已通过，后续发版仍需复核 |
| Play 开发者账号与资料 | 待维护者确认 | 账号状态、费用及表单以 Play Console 为准 |
| Play 测试与正式发布 | 待维护者确认 | 测试门槛按账号创建时间与当前政策核对 |
| 国内平台所需材料与备案 | 待维护者确认 | 按目标商店逐项核对 |
| 各商店上架 | 待维护者确认 | 记录平台、审核结果和版本 |
