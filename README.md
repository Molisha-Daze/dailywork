# 未竟 · Weijing

**欢迎使用「未竟」。** 这是一个纯自用的小工具，也是**全部由 AI 生成、没有一行手写**的项目。

它是一个 Android 习惯打卡 App，用 Kotlin + Jetpack Compose + Material 3 + Room 写的，
数据全部存在本地，不需要注册、不联网统计、也不上传任何东西。

当前版本 **1.4.1**（`versionCode 10401`）· 适用于 Android 10 ~ 15（API 29 ~ 35）

<img src="docs/screenshots.png" alt="未竟 · 四个页面" width="100%">

<details>
<summary>暗色主题（墨曜）</summary>

<img src="docs/screenshots-dark.png" alt="未竟 · 暗色主题" width="100%">

</details>

> 名字取自「有始有终」的反面 —— 记下来的事，大多未竟。

---

## 为什么做这个

现成的打卡 App 大体两类：一类太简单，只能记「今天做了没」；一类太重，塞满了社交、
会员和排行榜。我自己想要的东西比较具体：**「今天要做什么」和「这个月做了多少」能一眼看到，
并且不需要翻第二屏。**

做的过程中比较在意的几点：

- **数据只在自己手机里。** 打卡记录、照片、备注都存在本地。App 唯一的联网行为是取一句每日格言（见下），此外不发任何请求。备份就是一份 JSON 文件，想拿去哪儿都行。
- **照片凭证会真正存下来。** 从相册选的照片会复制一份进应用私有目录 —— 相册里那张删了、手机重启了，打卡凭证依然打得开。
- **提醒时间尽量准。** 用 `AlarmManager.setExactAndAllowWhileIdle`，重启后自动重排，通知上可以直接打卡。
- **顺手一点。** 打卡、撤销、计数达标都有各自的触感和提示音。

## 功能一览

一共四个页签。

### 今日
- 顶部进度卡：今天要完成几项、已经完成几项
- 今日排期列表：支持带子任务的大计划（逐项勾选）、计数器型习惯（比如「喝水 3 杯」，点一次加一杯）
- 当天没有排期时不会显示空列表，而是给一张说明卡，外加未来 60 天内即将到来的计划
- 底部一句话每日格言（优先联网，断网自动回退到内置的 64 条库）

### 历史
- 月历视图：每一天用状态点标出「已完成 / 部分完成 / 未完成」，可翻月、可跳回今天
- 点任意一天看当天完整清单，未打卡的可以补打卡
- 下方是倒序的打卡流水，点缩略图全屏查看凭证原图

### 计数器
- **独立计数器**：和习惯分开的纯计数工具（比如「冰箱里的可乐还剩几罐」）
- 可以设置上限、自定义步长与单位
- **自动周期归零**：按天 / 周 / 月 / 每 N 天重置，归零时把上一周期的结果写进历史，记录不会丢

### 管理中心
- 视觉效果：6 套配色（宣纸 / 雾霭 / 青瓷 / 墨曜 / 午夜 / 暖夜）+ 跟随系统
- 计划清单：集中管理所有习惯和计划，支持归档、批量操作
- 数据备份：导出 / 导入 JSON，换手机时用它迁移
- 提醒设置：通知权限引导、系统音量诊断
- 关于：版本信息、手动检查更新

## 技术要点

| 项目 | 值 |
|---|---|
| 语言 / UI | Kotlin · Jetpack Compose · Material 3 |
| 存储 | Room（version 4），无 `fallbackToDestructiveMigration` |
| 最低 / 目标 | minSdk 29 · targetSdk 35 · compileSdk 35 |
| 构建 | Gradle 8.9 · AGP 8.x · JDK 17 |
| 依赖 | 只用 Compose BOM + Room + Coil + `material-icons-core` |
| 代码规模 | 约 13,000 行 Kotlin / 57 个文件 |
| 单元测试 | 9 个测试类 · 113 条用例 |
| APK | release 约 21 MB（未开 R8，原因见下） |

### 几个取舍

**没有引入 `material-icons-extended`。** 那个包有 2000+ 图标、35.7 MB，而这个 App 只用三十来个。
不开 R8 时它会整个打进 dex，曾经把 APK 撑到 **58.97 MB**。改成本地自绘图标
（`ui/components/HabitIcons.kt`，路径数据取自官方 Material Icons 的 SVG，Apache-2.0）之后降到 **20.90 MB**。

**没有开 R8 / 资源压缩。** 开了能压到 3 MB 左右，但崩溃堆栈会变成混淆名，
必须归档 `mapping.txt` 才能查线上崩溃。20 MB 够用了，不想为省这点体积换来长期的排查麻烦。

**没有声明 `ACCESS_NETWORK_STATE` 权限。** 唯一那次联网直接发、失败就降级，
无网络时 `UnknownHostException` 会立刻抛出，不需要先探测一遍。

**数据库迁移禁用了 `fallbackToDestructiveMigration()`。** 每次改表结构都写明确的 `Migration`
并导出 schema JSON。宁可多写一段迁移代码，也不接受「升级一下数据全没了」。

**联网失败都是安静的。** 格言接口挂了就用内置库，更新源挂了就当作没有新版。
没必要为了一个附加功能弹错误框打扰人。

## 联网说明

整个 App 只有这一处联网，单独写出来是为了以后不会被无意扩大。

**请求**：向 `https://card.gudong.site/api/random-note` 发一个 `GET`。
不带设备标识、不带用户标识、不带 Cookie，**不上传任何打卡记录 / 照片 / 备注 / 备份**。

**降级顺序**（任何一步失败都安静往后走，界面不会出现加载中或空白）：

1. 当天已有远程缓存 → 直接用，不发请求
2. 没有缓存 → 异步拉一次（3s 连接 / 3s 读取），内容通过 `QuotePolicy` 校验才落盘
3. 无网络 / 超时 / 接口挂掉 / 内容不合格 → 保持内置库当天的句子

**首帧不等网络**：格言一开始显示的就是内置库里当天的句子，远程结果回来才替换。接口实测首包要 4 秒，等它的话用户就得对着空白等。

**为什么每天只请求一次**：那个接口每次调用都随机换一句。不按天缓存的话，同一天不同时间看到的句子都不一样，就不叫「每日格言」了。

**已知风险**：接口由个人开发者维护，没有可用性承诺，随时可能改协议或下线 —— 所以它只是锦上添花，内置库才是保底。另外接口不接受查询参数，实测 8 次里有 3 次返回佛学内容，所以在客户端按 `collectionId` / `tags` 做了过滤。

<details>
<summary><b>想彻底关掉联网</b></summary>

删掉 `AndroidManifest.xml` 里的 `INTERNET` 权限，以及 `TodayScreen` 里那次 `refreshTodayQuote` 调用就行，其余代码会自动退回内置库。
（注意：这样应用内自更新也会一起失效。）

</details>

## 应用内自更新

App 可以自己在应用内检查、下载并调起安装新版本，不用再往群里丢 APK。更新源有两个：

| 源 | 角色 | 说明 |
|---|---|---|
| **Gitee 发行版** | 主源 | 国内可直连，附件支持匿名下载 |
| **GitHub Releases** | 备源 | Gitee 挂掉或没发版时兜底 |

两个源会**同时查询，取 `versionCode` 大的那个**。这一点比较重要：如果只有一个源，
一旦它出问题，失败是**完全安静**的 —— 用户只会看到「已是最新」，永远收不到更新，也查不到任何报错。

触发时机是**每次冷启动**（用 `AppSettings.lastUpdateCheckAt` 做 24 小时限频）。
发现新版本后会弹出对话框，流程是「下载 → 校验 → 引导开启安装权限 → 调起系统安装器」。

> ⚠️ **做不到「静默自动安装」。** Android 要求用户必须在系统界面点一次「安装」，
> 第一次还要先授权「允许安装未知应用」。能省掉的只是「去群里翻文件」这一步。

> 安装前会校验 APK 的 `longVersionCode` 和发布信息是否**完全一致**，
> 不一致就直接拦住，避免装错版本。

## 构建

```bash
cd android
./gradlew :app:assembleRelease     # 产物：app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest   # 跑 113 条单元测试
```

用 Android Studio 打开时选 `android` 目录（不是仓库根目录）。
需要 JDK 17；Gradle Wrapper 会自己下载对应版本。

如果 `keystore.properties` 不存在，release 构建会**在签名阶段直接失败**。
这是有意为之，免得悄悄产出一个装不上的未签名 APK。

## 发版

1. `app/build.gradle.kts` 里 `versionCode` / `versionName` **一起改**
2. `:app:assembleRelease`
3. 发到 Gitee（脚本会比对版本自洽性 → 校验 APK → 建发行版 → 传附件 → 回读确认）：

   ```bash
   cd android
   python release_gitee.py --notes "本次更新内容"
   python release_gitee.py --dry-run        # 只看会做什么
   python release_gitee.py --prune 3        # 只保留最近 3 个版本
   ```

需要 Gitee 私人令牌，优先读环境变量 `GITEE_TOKEN`。

> 🔴 **`versionCode` 的规则是 `major*10000 + minor*100 + patch`。**
> 只改 `versionName` 不改 `versionCode`，等于没发新版，用户收不到更新。
> 也别用「和 versionName 末段对齐」的写法，那样 1.0.1 和 1.1 会算成同一个数。

> 🔴 **签名密钥不进仓库。** `release.keystore` 和 `keystore.properties` 已经 gitignore。
> 这两样丢了就再也无法给已安装的用户发新版（签名不一致，系统会拒绝覆盖安装，
> 用户只能卸载重装，数据全丢）。请单独离线备份到两处。

> ⚠️ Gitee 免费版限制：单个附件 ≤ 100MB、仓库附件总量 ≤ 1GB。按一个包 21MB 算，
> 大概 47 个版本就满了，用 `--prune` 定期清理，否则新版传不上去。

## 目录结构

```
android/
└── app/src/main/java/io/github/molishadaze/weijing/
    ├── data/           Room 实体、DAO、Repository、备份编解码、更新源
    ├── model/          UI 层数据模型（HabitWithStats / SubTask / UpcomingHabit）
    ├── notification/   通知渠道、闹钟接收器、开机重排、通知栏一键打卡
    ├── ui/
    │   ├── components/ 卡片、弹窗、表单组件、月历、自绘图标
    │   ├── screens/    今日 / 历史 / 计数器 / 管理中心 四个页面
    │   └── theme/      6 套配色，禁用 Material You 动态取色
    ├── util/           排期与连续天数计算、备份、触觉音效、更新策略
    └── viewmodel/      HabitViewModel（唯一的 ViewModel）
```

## 说明

- 代码可以随意阅读、学习、fork 自用，但请不要以本项目的名义再分发。
- 图标路径数据取自官方 Material Design Icons（Apache-2.0）。
- 提 Issue 很欢迎，尤其是**能复现的 bug**（装不上 / 崩溃 / 数据丢失 / 备份导不进来）。
  麻烦带上机型、Android 版本、复现步骤，有 logcat 更好。
- 这是个人的自用小工具，功能方向由开发者自己定，**不接受 PR**。

---

<p align="center"><sub>「有始有终」很难 —— 所以先把「始」记下来。</sub></p>
