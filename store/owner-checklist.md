# Vigil 上架执行清单（需 owner 本人操作）

> 本文档列出只有 owner 本人能完成的上架步骤（涉及付费、实名、账号）。
> 项目侧物料已全部备好：文案与权限说明见 `store/README.md`，Feature Graphic 见 `store/feature-graphic.png`，隐私政策见仓库根目录 `PRIVACY.md`，截图用 `screenshots/` 目录（`main.png` / `alert.png` / `app-filter.png`）。
>
> **建议执行顺序：三 → 二 → 一**（国内软著周期最长，最先启动；GitHub secrets 最快，随手先做）。

---

## 一、GitHub：配置 CI 发版 secrets（约 5 分钟）

让 `v*` tag 触发的自动发版（`.github/workflows/release.yml`）能正常签名。

1. 打开 https://github.com/XGWNJE/Vigil/settings/secrets/actions
2. 点 **New repository secret**，逐个添加 4 个：

| Name | Value 怎么来 |
|---|---|
| `VIGIL_KEYSTORE_BASE64` | 本地执行（Git Bash，在仓库根目录）：`base64 -w0 keystore/vigil.keystore`，把输出的整串复制为值 |
| `VIGIL_STORE_PASSWORD` | 打开 `keystore.properties` 复制 `storePassword` 的值 |
| `VIGIL_KEY_ALIAS` | `keystore.properties` 里 `keyAlias` 的值（应为 `vigil`） |
| `VIGIL_KEY_PASSWORD` | `keystore.properties` 里 `keyPassword` 的值 |

3. 验证：之后任意一次 `git tag v* && git push origin v*` 后，在 Actions 页看到 Release 工作流跑绿、Releases 页出现带 APK 的新版本，即配置成功。

> 注意：secrets 只会显示一次，粘贴后无法回看；`keystore.properties` 与 `keystore/` 目录已被 gitignore，永远不会进仓库，不要手动添加它们。

---

## 二、Google Play（费用 $25，硬门槛：12 人 × 14 天封闭测试）

### 步骤 1：注册开发者账号（约 30 分钟 + 谷歌审核 1~2 天）

1. 打开 https://play.google.com/console ，用 Google 账号登录。
2. 选择账号类型：**个人**（Personal）。
3. 支付一次性注册费 **$25**（需支持国际支付的信用卡/借记卡）。
4. 完成实名验证：上传身份证件（按页面指引，可能要求身份证或护照）+ 验证手机号与邮箱。
5. 等待谷歌审核通过（通常几小时到 2 天）。

### 步骤 2：创建应用并填资料（约 1~2 小时）

1. Console 首页 → **创建应用**：
   - 名称：`Vigil`
   - 默认语言：中文（简体）
   - 应用类型：应用 / 免费
2. 按左侧清单逐项完成（每项旁边有指引）：
   - **应用内容 → 隐私政策**：填 `https://github.com/XGWNJE/Vigil/blob/main/PRIVACY.md`
   - **应用内容 → 广告**：选「不含广告」
   - **应用内容 → 内容分级**：填问卷，全选「否」（无暴力/赌博/社交），结果应为「适合所有人」
   - **应用内容 → 目标受众**：选 18 岁以上，且勾选「非儿童向」（避免家庭政策额外审核）
   - **应用内容 → Data safety（数据安全）**：全部选「不收集、不共享任何数据」
   - **应用内容 → 新闻应用/COVID 等**：均选「否」
3. **敏感权限声明**（本项目会触发，照抄 `store/README.md` 第五节模板）：
   - 通知监听（Notification Listener）用途说明
   - 前台服务 specialUse（FOREGROUND_SERVICE_SPECIAL_USE）用途说明（英文）
4. **商店资料（商品详情）**：
   - 简短说明：抄 `store/README.md` 中文短描述
   - 完整说明：抄 `store/README.md` 中文完整描述
   - 应用图标：用 `app/src/main/ic_launcher-playstore.png`（512×512）
   - 置顶大图（Feature Graphic）：上传 `store/feature-graphic.png`
   - 手机截图：至少 2 张，用 `screenshots/main.png`（主界面）、`screenshots/alert.png`（报警界面）、`screenshots/app-filter.png`（应用过滤）
   - 分类：工具（Tools）
   - 联系邮箱：填你的邮箱；官网填 `https://github.com/XGWNJE/Vigil`

### 步骤 3：封闭测试（硬性门槛，14 天）

> 谷歌 2023 年 11 月起的新规：**个人开发者账号**发布正式版前，必须完成「至少 12 名测试者连续参与 14 天」的封闭测试。

1. Console → **测试 → 封闭测试** → 创建轨道（如 `closed-beta`）。
2. 创建发布版本：上传 **AAB 包**（不是 APK）。本地构建命令：
   ```bash
   ./gradlew bundleRelease
   # 产物：app/build/outputs/bundle/release/app-release.aab
   ```
3. 测试者列表：创建「邮箱列表」，填入至少 **12 个真实 Google 账号**（亲友、同事、同学皆可）。
4. 把测试链接发给测试者，请他们：点击链接加入 → 从 Play 商店安装 → **安装后保留 14 天别卸载**，期间偶尔打开用一下（谷歌会检测活跃）。
5. 满 14 天且人数达标后，Console 会出现「申请正式发布」入口，按提示提交（通常还要回答几个关于测试反馈的问题）。

### 步骤 4：正式发布

- 封闭测试达标后申请正式版，谷歌审核约 1~7 天。
- 审核期间如被打回，按邮件/Console 里的具体原因修改重新提交即可（最常见是权限用途说明不够具体，把 `store/README.md` 模板再补细节）。

---

## 三、国内平台（周期最长：软著 30~60 个工作日）

### 步骤 1：软件著作权登记（最先启动）

> 小米、华为、OPPO、vivo、应用宝等商店上架 APP 基本都要求软著证书。

1. 打开中国版权保护中心官网 https://www.ccopyright.com.cn/ ，注册账号并实名认证（登记系统入口：https://register.ccopyright.com.cn/login.html ）。
2. 在线填报「计算机软件著作权登记申请」：
   - 软件名称：`Vigil 关键词通知报警软件`（简称 `Vigil`）
   - 开发完成日期：填首次发布的日期（2026 年 7 月，以首个 release 为准）
   - 发表状态：已发表（GitHub 开源链接可作为发表证明）
3. 上传材料：
   - **源代码**：前后各连续 30 页（共 60 页），从本仓库导出（Android Studio 里把主要 .kt 文件复制到 Word，每页 50 行左右）
   - **说明书**：用户手册或设计说明书（可把 `README.md` 扩写成 Word 文档，配截图）
4. 提交申请。**官费已停征（自 2017 年 4 月起免费）**，只有材料打印 + 邮寄费（也可预约后到版权保护中心现场提交省邮费）；嫌麻烦可找代理机构代办，服务费几百元。
5. 等待下证：普通流程 **30~60 个工作日**，期间留意补正通知（材料格式问题会要求补正）。

### 步骤 2：App 备案（工信部要求，免费）

> 2024 年起国内上架 APP 必须完成备案，通过商店接入系统提交。

1. 准备：软著证书（步骤 1）、身份证、域名（如有，可用 GitHub Pages 或直接说明无）、服务器信息（本应用无服务器，勾选「不使用服务器/纯本地应用」按各商店指引填写）。
2. 在第一家准备上架的商店开放平台里找「App 备案」入口（如小米开放平台的备案通道），按指引提交。
3. 备案审核约 **1~20 个工作日**，拿到备案号（格式如「京ICP备xxxx号」）后，其他商店复用该备案号。

### 步骤 3：各厂商开发者实名认证 + 上架（每家 1~2 小时）

按优先级建议顺序（用户量）：**华为 → 小米 → OPPO → vivo → 应用宝**。

每家流程相同：
1. 注册开发者账号并实名认证（身份证 + 人脸/银行卡验证，个人开发者免费）：
   - 华为：https://developer.huawei.com
   - 小米：https://dev.mi.com
   - OPPO：https://open.oppomobile.com
   - vivo：https://dev.vivo.com.cn
   - 应用宝：https://open.qq.com （腾讯开放平台）
2. 创建应用，上传 **APK**（release 包，`app-release.apk`）。
3. 填资料：名称、分类（实用工具）、简介（抄 `store/README.md` 中文文案）、截图、隐私政策链接。
4. **权限用途说明**：逐项抄 `store/README.md` 第四节的权限用途表。
5. 上传软著证书 + 填备案号。
6. 提交审核，通常 **1~3 个工作日**；被打回按原因改（国内审核常抠权限用途措辞，往「仅本地处理、不联网」上强调）。

---

## 进度记录

| 事项 | 状态 | 完成日期 | 备注 |
|---|---|---|---|
| GitHub secrets 配置 | ☐ | | |
| Play 开发者账号注册 | ☐ | | $25 |
| Play 应用资料填写 | ☐ | | |
| Play 封闭测试（12 人 × 14 天） | ☐ | | 开始日：____ 结束日：____ |
| Play 正式发布 | ☐ | | |
| 软著申请 | ☐ | | 30~60 工作日 |
| App 备案 | ☐ | | 需先有软著 |
| 华为上架 | ☐ | | |
| 小米上架 | ☐ | | |
| OPPO 上架 | ☐ | | |
| vivo 上架 | ☐ | | |
| 应用宝上架 | ☐ | | |
