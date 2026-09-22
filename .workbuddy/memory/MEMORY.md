# 项目约定 — dailywork「未竟」（Kotlin/Compose/Room Android）

> 唯一实现 `android/`；网页版 `src/` 2026-09-20 已删（存档 `E:\anzhuo\weijing-web-archive-1.0-20260920.zip`）。
> ⚠️ HEAD 常落后、工作区长期大片未提交：恢复文件只 `git checkout HEAD -- <精确路径>`，**绝不** `git checkout -- .`。

## 构建 / 验证 / 模拟器 → 见技能

工具链 `E:\anzhuo\toolchain`（jdk-17 / gradle-8.9 / android-sdk platform-35 + build-tools 35.0.0），无 `ANDROID_HOME`、无 `gradlew`。
- 完整命令与环境变量：skill **`android-apk-offline-build`**（含 `GRADLE_USER_HOME`、dexBuilder 沙箱/文件锁、APK 核验）。
- MuMu 实测：skill **`android-ui-verify-mumu`**（`127.0.0.1:16384`；⚠️ 会切掉用户正在玩的游戏，**动之前先问**）。
- Room 迁移发版：skill **`android-room-migration-release`**；启动图标：**`android-adaptive-launcher-icon`**。
- 本仓库特有：Bash 里必须先 `export PATH=".../PortableGit/versions/1.2.0/usr/bin:$PATH"` 否则 `ls/grep` 都没有；
  输出重定向到文件再读（别用管道）；别从 Bash 调 `cmd`。
- 🚨 **BUILD SUCCESSFUL ≠ 测试跑了** → 必解析 `app/build/test-results/testDebugUnitTest/*.xml` 数用例。
- 离线无新依赖；无 ui-test / robolectric / androidx.test。

## 发版

- 应用名**未竟**；namespace=applicationId=`io.github.molishadaze.weijing`；minSdk 29 / target 35。
- 当前 **1.4.0 / versionCode 10400**。`versionCode = major*10000+minor*100+patch`（1.3.0→10300）。
  **只改 name 不改 code = 没发新版**。
- 发版跑 **`android/release_gitee.py`**（纯标准库）：它从 `AppUpdateSource.kt` 读仓库路径、
  从 `build.gradle.kts` 读版本，用 App 同一套 `TAG_PATTERN` 校验后建发行版 + 传 APK + **回读校验**。
  令牌走环境变量 `GITEE_TOKEN`（优先）或 `android/.gitee-token`（已 gitignore）。
  `--dry-run` 只做本地检查，`--prune N` 清理旧版本附件防撞 1GB 上限。
- 同步清单：`build.gradle.kts`、`README.md` 版本表、`strings.xml:app_name`、通知渠道 description。
- 签名 `android/release.keystore`（别名 `weijing`）+ `keystore.properties`（均 gitignore）；**.gitignore 必须保留**。
- `isMinifyEnabled=false` → release ≈21MB。
- 已声明 `INTERNET`、`POST_NOTIFICATIONS`、`REQUEST_INSTALL_PACKAGES`；不加 `ACCESS_NETWORK_STATE`。

## 应用内自更新（双源，v1.3.0 起开发）

- **Gitee 主 + GitHub 备**，并行查、取 versionCode 大者。常量在 `data/AppUpdateSource.kt` 顶部：
  `GITEE_REPO`（**留空=不启用**）、`GITHUB_REPO`。
- **Gitee 账号 `weijingzhishi`**（昵称 Zihang Wang，2026-09-22 注册）；
  `GITEE_REPO = "weijingzhishi/weijing"` **已填好**（脚本也从这里读）。
  ⚠️ 建仓库要**实名认证**；仓库**必须公开**（私有仓库的发行版附件匿名拿不到 → 自更新必失败）。
- 🚨 实测（**必须 `curl --resolve` 绕过本机 2886 行 hosts 才算数**）：`api.github.com` ✅；
  `release-assets.githubusercontent.com` ✅ 1.4MB/s；**`github.com` ❌ TCP 建不起来**。
  → GitHub 下载**禁用** `browser_download_url`，必须走 `api.github.com/.../releases/assets/{id}`
  + `Accept: application/octet-stream`（不带这个头回的是 JSON 元数据）。
- Gitee ✅ `/releases/latest` **确实带用户上传的附件**（曾误判为「只返回源码包」——
  其实是那批样本的最新版本身没挂附件；要判定就直接看网页 `gitee.com/{o}/{r}/releases`）。
  附件匿名直链通：`/releases/download/` → `/attach_files/{id}/download/` → `foruda.gitee.com`（200，6MB/4.6s）。
  对照活体样本：`wflwang/bledebug`（真实用 Gitee 做 OTA 的安卓项目）。
- Gitee 🚨 **匿名 API 会限流**：连打 ~60–80 次后**全线** `403 Rate Limit Exceeded`，冷却 >45s。
  响应是**纯文本** → `json.load` 报 `Extra data: line 1 column 5`；`search/*` 被限时
  **返回 `[]` 而不是报错**。→ 排查期响应先落盘再离线解析，别反复刷。
- Gitee ⚠️ 无 `size` 字段（附件也没有）；`assets` 混着自动源码包（靠 `.apk` 后缀过滤，**别取下标 0**）；
  `/releases` **列表是升序**（`per_page=1` 给最旧那条）；单附件 ≤100MB / 仓库附件总量 ≤1GB。
- 文件：`data/AppUpdateSource.kt`(双源+`UpdateChannel`)、`util/AppUpdatePolicy.kt`(纯规则+单测)、
  `util/ApkDownloader.kt`、`util/ApkInstaller.kt`、`ui/components/AppUpdateHost.kt`、
  `res/xml/update_file_paths.xml`；`MainScreen` 顶层挂 `AppUpdateHost`。`AppSettings` 24h 限频。
- **无静默安装可能**（系统强制点一次「安装」+ 授权未知来源）；**首个带自更新的包仍需手动发一次**。
- 🚨 **Kotlin 块注释可嵌套**：注释里出现 `/*`（如写路径 `` `tags/*.zip` ``）会把后面代码全吞掉，
  报 `Missing '}'` + `Unclosed comment`，**行号完全不相关**。字符串里无害，只有注释炸。
- ⚠️ release 包里 `res/xml/*.xml` 被 AGP **改名缩短**（如 `res/88.xml`），不是缺失；
  核验要在缩短名 xml 里按 **UTF-16** 搜 `cache-path`。

## 图标（`ui/components/HabitIcons.kt` 唯一来源）

- `UiIcons` 20 + `HabitIcons` 15；只依赖 `material-icons-core`。
- 🚨 必须 `addPath(pathData = addPathNodes(d))`，**不能** `path { addPathNodes(d) }`
  （返回值被丢弃 → 编译无警告、图标空白）。改完跑 `HabitIconsTest`。
- key = `HabitIcons` ↔ `Habit.iconName`，写错**静默回退 Star**；删图标四处同删。
- 🔥 启动图标：画布 108dp、启动器只看中间 72dp、**安全区仅 66dp**；脚本 `android/gen_icons.py`。

## 导航 / 结构

4 tab：**今日 / 历史 / 计数器 / 管理中心**；`MainScreen` 用整数 index 切 tab，**无 navigation-compose**。
管理中心 = 金刚区（视觉效果/关于/备份/提醒）+ 计划清单（`SettingsScreen` 内嵌 `HabitManageSection`，
不用 LazyColumn）。**别再开新 tab**。「独立计数器」(StandaloneCounter) ≠ `Habit.isCounter`。

- 🚨 **今日页不许用 `if(空) 空状态 else 列表` 分流**：一分流格言卡就被关进 else。恒为一个 LazyColumn：
  头部卡（进度卡 / 「今天没有排期」卡 / `EmptyState` 三选一）→ 格言卡 →（今天没排期时）即将到来 → 今日习惯。
- 今日页兜底窗口 **60 天**。
- 每日格言：`DailyQuotes` + `RemoteQuoteSource` + `QuotePolicy` + `AppSettings` 按天缓存 + `VM.refreshTodayQuote()`。
  降级链 **当日缓存 → 异步远程 → 内置库**；首帧给内置句，**绝不等网络**；每天最多请求一次。
  🚨 取模用 `toEpochDay()`（不是 `dayOfYear`）且必须 `.mod()`。
- 计数器卡片：`hasLimit` 分流两种密度；无上限时不写「无上限自由计数」、不画分隔线；内边距 14dp；按钮 40dp `IconActionBox`。

## 🚨 「一次性任务」判定（改卡片前必读）

`HabitSchedule.isOneShot(habit)`：`TYPE_NONE` **或**（有 endDate 且区间内只落得到一天）。
- 不能只判 `recurrenceType == "none"`：伪循环语义上等同单次，旧代码只看字面值 → 卡片照样挂「连续 1 天」，
  而排期说明条件是 `type != daily`，刚好一起藏掉（两头都错）。
- 落点：`HabitCard` 用 `isOneShot` 决定「连续/最长」行与排期行；`scheduleLabel()` 对一次性统一回「仅 YYYY-MM-DD」。
- 单测 8 条在 `HabitScheduleTest`。

## Room（version = 4）

迁移：`1_2` 建 `standalone_counters`；`2_3` habits 加 `isParentPlan`/`subTasks`、check_ins 加
`completedSubTaskIds`；`3_4` counters 加 `resetPeriod`/`resetIntervalDays`/`periodStartDate`
+ 建 `counter_period_logs`（FK CASCADE）。
改实体必须：①写显式 `Migration` 并 `addMigrations()`；②导出 JSON 进版本管理。**禁用 `fallbackToDestructiveMigration()`**。
逐字段比对导出 JSON；`ALTER TABLE` 一句一列，NOT NULL 新列必须带 DEFAULT。
- 子任务：`model/Models.kt` 的 `SubTask` + `Converters.kt`（org.json 手写）。`BackupCodec` FORMAT_VERSION = 4。
- 周期归零：`util/CounterPeriodCalculator.kt` 纯函数；**惰性结算** `rolloverCounterPeriods()` 在 ON_RESUME 与
  `stepCounter`/`resetCounter` 前触发，按天短路 + Mutex。
- 示例数据播种靠持久标记 `AppSettings.sampleSeeded`（**不能靠「数据为空」**）。

## Compose 坑（改视觉前必读）

1. 半透明色不能做带 elevation 的 Card 底色 → `color.compositeOver(surface)`。
2. `Text(fontSize=…)` 不覆盖行高 → 给 `style=` 或显式 `lineHeight`。
3. 别用 `CardDefaults.outlinedCardBorder()`（取 `outlineVariant`，本主题只覆盖 `outline`）→ 直接 `BorderStroke`。
4. 列表排序别挂 `updatedAt`（写操作会刷新 → 每点一下跳位）。
5. **编译通过 ≠ 画得出来**：把「有返回值的函数」当语句写进 lambda 会静默失效。
6. 同文件改动必须**串行**提交（并行 Edit 互相覆盖），跨文件才能并行。
7. 竖线类装饰：`Modifier.height(IntrinsicSize.Min)` + 子项 `fillMaxHeight()`。
8. 删文件用 `rm`，**别用 `git rm`**。
9. `Modifier.weight` 只在 `RowScope`/`ColumnScope`，提取 Composable 必须写 `private fun RowScope.Xxx(...)`。

## 触觉 / 音效（v1.3.0）

- 触觉 `util/Haptics.kt`，`HabitApplication.onCreate()` attach；只在 **Compose UI 层**调用（VM 无 Context）。
  完成 → STRONG；取消/撤销 → LIGHT；计数只在「刚好达标」那次 STRONG。同级 70ms 节流且**分档独立计时**。
- 音效入口 = **`util/Feedback.kt`**（`Feedback.fire(Event.X)`）。Event 枚举把 (触觉档位, 音效) 写进构造参数，
  漏配直接编译失败；**新加反馈一律走它**。方案见根目录 `音效方案-未竟-20260922.md`。
- 音效**必须比触觉保守**：不做点击音/导航音，只在「成就时刻」响。默认 7 响 / 2 关（`UNDO`、`STEP_SOFT`）。
  **通知栏打卡路径只振不响**（`Sounds` 用 `ActivityLifecycleCallbacks` 跟踪可见性自动拦）。
- 来源 = 本地合成 `android/gen_sfx.py`（纯标准库，本机**无 ffmpeg/numpy**），成品 `res/raw/sfx_*.wav`。
- 音频属性走 **`USAGE_ASSISTANCE_SONIFICATION`（STREAM_SYSTEM）**：静音模式只静铃声+通知，走媒体流会导致静音下照样出声。
  代价=系统音量 0 时听不见 → 设置页必须有**试听 + 系统音量诊断**。
- 🚨 改触发点：节流 **150ms 且按音效各自计时**；WAV 首尾强制淡入淡出；`SoundPool.load()` 异步，
  未就绪时 `play()` 直接返回（否则第一声静默消失）；`SubTaskSection` **新参数必须透传**。
- 「今日全部完成」用 **`completesDay` 参数透传**（TodayScreen 算 `completedCount + 1 == totalCount`），
  **不用** progress 边沿检测（会让 PLAN_DONE 与 DAY_DONE 叠成两声噪音）。

## 主题（`ui/theme/AppTheme.kt` 唯一真相；禁用 Material You）

- 🔴 主色低饱和（≤30%）且与计划/计数器色板 ΔE ≥ 25（实测最小 31.1），`AppThemeTest` 守回归。
- 🔴 完成/选中/进度/FAB 取 `colorScheme.primary`，禁止写死 `Color(0xFF10B981)`。
- 🚨 主色上的文字/图标用 `onPrimary`，禁止 `Color.White`。
- ⚠️ 欠账：main 除 AppTheme 外仍有 18 处 `Color(0xFF…)`（8 文件，多为图标 tint）。
- 主题 id 存 `AppSettings.themeId`（util 不反向依赖 ui）。

## 表单弹窗（`ui/components/FormDialog.kt`）

「新建计划」/「新建独立计数器」共用 `FormTokens`/`AppFormDialog`/`FormSection`/`FormTextField`/
`FormDescriptionField`/`FormLabeledField`/`FormGroupCard`/`FormSwitchRow`/`FormSegmentedControl`/`FormChip`/`FormColorPicker`。
**新增表单直接用这套，别手写间距圆角。**
标签放输入框**上方**；底部「取消/保存」在滚动区外；内容区 `heightIn(max = screenHeightDp*0.72f)`；
弹窗内只允许**一个**滚动区；`singleLine` 与 `minLines/maxLines` 互斥。

月历三件套（2026-09-22 拆分）：`CalendarMonthView.kt`(入口，**只持有 `currentMonth`/`selectedDate`** + `CalendarCard` + `calAccent`) /
`CalendarMonthGrid.kt`(月头/表头/网格/`DayCell`/图例) / `CalendarDayPlanList.kt`(当天计划清单)。
🚨 **状态宿主不能下沉**：两个 remember 挪进小块 → 翻月重建 → 选中日期丢失。
「为某天新建计划」默认类型：未来日 `TYPE_NONE`；**今天 `TYPE_DAILY`**。

## 现状 / 欠账

- P1+P3 已执行（`b04cd89`、`c028632`）：删热力图死链与 navigation 依赖、旧包名 schemas；拆月历 + 文件归属整理。
- P2 未做：D1 颜色治理（仅 `HabitManageSection` 计数目标徽章 `#10B981` 真违规）/ D2 星期四合一 /
  D3 `notificationSettingsIntent` 与 `calAccent`+`detailAccent` 去重。
- main ≈11k 行 / 50 文件；大文件：AddEditHabitDialog / SettingsScreen / StandaloneCountersScreen /
  HabitRepository / HabitCard / CalendarDayPlanList。
- 已知弃用警告：`AddEditHabitDialog.kt`/`MainScreen.kt` 的 `LocalLifecycleOwner`（换 `lifecycle-runtime-compose` 需联网）。

## 🚨 沙箱限制

只有系统目录的 exe 能跑：把 notepad 拷到 `E:\anzhuo` 都起不来（bash 直跑 `Permission denied`，
`cmd //c start` 返回 0 但无进程，关沙箱提权也没用）。
→ 任何「运行刚下载的安装包」需求，**直接给用户命令行让他双击**，别反复试。
