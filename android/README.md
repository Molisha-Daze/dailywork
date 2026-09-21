# 未竟（原「每日习惯打卡」）Android App (Kotlin + Jetpack Compose)

基于 **Kotlin + Jetpack Compose + Material 3** 构建的原生 Android 习惯打卡与拍照留证工具。

## 核心功能与技术规范

- **目标平台**: Android 10 (API 29) 至 Android 15 (API 35)。
- **界面层**: 100% Jetpack Compose + Material 3，适配系统深色/浅色模式，全中文界面，字号严格保证不小于 12sp。
- **本地存储**: Room 数据库（`Habit` 习惯表、`CheckIn` 打卡表），`HabitStats` 采用动态查询实时算，零冗余字段。
- **连续天数算法**: 本地时区自然日（00:00 为界），计算当前连续打卡天数与历史最长纪录，断卡后当前连续天数正确归零。
- **拍照与相册凭证**: 采用 Android 官方最新标准 `ActivityResultContracts.PickVisualMedia`（Photo Picker），向下兼容至 Android 10，无需申请危险的 `READ_EXTERNAL_STORAGE` 权限。
- **照片持久化保护**: 从 Photo Picker 获取的临时 Uri 流式复制存储至 App 私有存储目录（`getExternalFilesDir` / `filesDir`），保证手机重启与沙盒生命周期内文件永久有效。
- **本地定时提醒**: 使用 `AlarmManager.setExactAndAllowWhileIdle` 精确定时；注册 `BOOT_COMPLETED` 广播接收器在开机后自动重新排期所有提醒；通知渠道配置完整，Android 13+ 运行时动态请求 `POST_NOTIFICATIONS` 权限，通知附带一键打卡 Action。
- **历史回顾**: 月历视图查看任意一天的排期与完成情况，下方为倒序打卡流水列表，支持点击缩略图全屏查看大图凭证。
- **每日格言**: 今日页「头部信息卡」下方的一句话，**优先联网**取自公开语录接口，**无网络时回退**内置的 64 条格言库（`data/DailyQuotes.kt`，先秦典籍 / 诗词 / 近现代与外国）；每天最多请求一次，取回的结果按天缓存，当天不再变。详见下节。

## 每日格言与网络边界

这是本 App **唯一**的联网行为。写在这里，是为了它不被后来者无意改动。

**权限**：只声明 `INTERNET`。刻意**不声明** `ACCESS_NETWORK_STATE` —— 不预判网络状态，直接发请求、失败即降级（无网络时 `UnknownHostException` 是立刻抛出的，不需要先探测）。少一个权限就少一份解释成本。

**请求内容**：发往 `https://card.gudong.site/api/random-note` 的单向 `GET`。不带设备标识、不带用户标识、不带 Cookie，**不上传任何打卡记录 / 照片 / 备注 / 备份**。

**降级链**（任何一环失败都安静地往后走，界面不会出现加载态或空白）：

1. **当天已有远程缓存** → 直接用，不发请求（`AppSettings.cachedRemoteQuote`）；
2. **没有缓存** → 异步拉一次，3 秒连接 / 3 秒读取超时；内容通过 `QuotePolicy` 校验才落盘并刷新界面；
3. **无网络 / 超时 / 接口挂掉 / 内容不合格** → 保持内置库当天的句子（`DailyQuotes.forDate`）。

**首帧不会等网络**：格言的初值就是内置库当天的句子，远程结果回来后才替换。接口实测首包要 4 秒，等它渲染等于让用户对着空白发呆。

**为什么每天只请求一次**：该接口是「随机漫步」，每调一次换一句。不做按天缓存的话，同一天不同时刻看到的句子会不一样 —— 那就不叫「每日格言」了；顺带也不会去白刷别人的免费服务。

**已知风险（接入前请知悉）**：

- 接口由个人开发者维护，**无任何可用性承诺**，可能改协议、限流或下线 —— 所以它只是锦上添花，内置库才是保底；
- 接口**不接受任何查询参数**（官方文档确认），无法按合集 / 日期 / 种子取句。实测 8 次里 3 次返回佛学内容，因此在 `QuotePolicy` 里按 `collectionId` / `tags` 做了客户端过滤（`buddhism` 等宗教内容一律不用）；
- 远程句子与内置库样式完全一致，用户不会因为「这次是联网拿的」看到不同的卡片。

**想彻底关掉联网**：删掉 `AndroidManifest.xml` 里的 `INTERNET` 权限、以及 `TodayScreen` 里那次 `refreshTodayQuote` 调用即可 —— 其余代码会自然退回内置库，无需其他改动。

## 在 Android Studio 中运行

1. 打开 Android Studio (推荐 Hedgehog / Iguana / Ladybug 或更高版本)。
2. 选择 **File -> Open...**，定位到本工程的 `android` 目录。
3. 等待 Gradle 同步依赖完成（使用 Gradle 8.7+ 和 JDK 17）。
4. 连接 Android 10 ~ 15 设备或模拟器，点击 **Run 'app'** 即可直接编译运行。

## 图标集：为什么不用 material-icons-extended

界面图标与习惯图标全部自绘在
`app/src/main/java/io/github/molishadaze/weijing/ui/components/HabitIcons.kt`。

原因：`androidx.compose.material:material-icons-extended` 打包了 **2000+ 图标 / 35.7 MB**，
而本工程只用得到三十来个。它作为 jar 依赖，在不开 R8 时会**整体**进 dex，
曾把 APK 顶到 **58.97 MB**；移除后 release 产物降到 **20.90 MB**。

- `UiIcons` —— 19 个界面通用图标（Tab、按钮、状态指示）。
- `HabitIcons` —— 16 个习惯可选图标，同时是 `getIconVector()` 与 `PRESET_ICONS` 的唯一来源。
- 只保留 `material-icons-core`（49 个基础图标，约 0.8 MB），供 `Icons.Default.Add / Delete / Edit / …` 这类通用符号使用。

路径数据取自官方 Material Design Icons（Apache-2.0），经 `addPathNodes` 直接解析 SVG 的 `d` 串，
**没有人工转写**，因此不存在「路径抄错导致图形畸变」的问题。新增图标：
到 <https://fonts.google.com/icons> 选一个，把 `<svg>` 里的 `d` 串贴进 `HabitIcons.kt` 即可。

⚠️ **习惯图标的 `key` 是两端唯一的纽带**：安卓 `getIconVector`、网页版
`getHabitIconComponent`、数据库 `Habit.iconName` 三者必须对齐。
新增或改名时三处都要改，否则备份文件跨端导入会静默回退成 `Star`（表现为「图标全变成星星」）。

## 正式签名与发版

### 签名材料（不在仓库里）

`.gitignore` 已排除 `*.keystore` / `*.jks` / `keystore.properties`：

| 文件 | 作用 |
|---|---|
| `android/release.keystore` | 私钥本体（PKCS12，RSA 2048，有效期 10000 天） |
| `android/keystore.properties` | 密码与别名，构建脚本从这里读 |

> 🔴 **这两样丢了就永远无法再给已安装用户发新版** —— 签名不一致会被系统拒绝覆盖安装，
> 用户只能卸载重装、数据全丢。请离线备份到两处，密码另存密码管理器。

### 构建发布包

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。
`keystore.properties` 缺失时 release 构建会在**签名阶段直接报错** —— 这是刻意设计，
避免悄悄产出一个「装不上」的未签名 APK。

### 包名与版本

| 项目 | 值 | 说明 |
|---|---|---|
| `applicationId` | `io.github.molishadaze.weijing` | 系统眼中的安装身份。**改它 = 换一个新 App**，老用户数据不跟随 |
| `namespace` | 同上 | Java 包名 / 源码目录结构 |
| `versionCode` / `versionName` | `10201` / `1.2.1` | versionCode 必须单调递增，否则老用户装不上新版。编码规则 `major*10000 + minor*100 + patch`，**不要**用「与 versionName 末段对齐」的写法（1.0.1 与 1.1 会撞成同一个数）。只改 versionName 不改 versionCode = 没发新版 |

> ⚠️ 本版本同时换了 **签名证书**（debug → 正式）和 **包名**，因此：
> 装过旧版的人必须**卸载重装**，数据不会自动迁移 —— 请先让他们在「管理中心 → 数据备份」导出备份。

### 想进一步瘦身？（可选）

`app/build.gradle.kts` 的 `buildTypes.release` 里把 `isMinifyEnabled` / `isShrinkResources`
改成 `true`，实测可把产物压到 **3 MB 量级**。代价是崩溃栈变成混淆名，
**必须归档 `app/build/outputs/mapping/release/mapping.txt`**（按版本号存），否则线上崩溃无法定位。
当前默认关闭 —— 20.90 MB 已经够用，不值得为此引入这份排查成本。
