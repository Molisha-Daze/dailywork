# 项目约定 — dailywork「未竟」（Kotlin/Compose/Room Android）

> 唯一实现 `android/`；网页版 `src/` 2026-09-20 已删（存档 `E:\anzhuo\weijing-web-archive-1.0-20260920.zip`）。
> ⚠️ HEAD 常落后、工作区长期大片未提交：恢复文件只 `git checkout HEAD -- <精确路径>`，**绝不** `git checkout -- .`。

## 工具链 / 构建 / 模拟器 → 细节全在技能里

`E:\anzhuo\toolchain`（jdk-17 / gradle-8.9 / android-sdk platform-35 + build-tools 35.0.0），
无 `ANDROID_HOME`、无 `gradlew`。构建入口 `python E:\anzhuo\toolchain\wb_build_weijing.py <task>`
（⚠️ **一次只吃一个 task**）。

- 技能：`android-apk-offline-build`（构建/签名/核验）、`android-ui-verify-mumu`（MuMu 实测：
  启动实例 / tap 坐标系 / 自更新全流程）、`android-room-migration-release`、
  `android-adaptive-launcher-icon`、`android-app-self-update`。
- 本仓库特有：Bash 先 `export PATH="/c/Users/admin/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:$PATH"`；
  输出重定向到文件再读（别用管道）；别从 Bash 调 `cmd`；给 Python 传路径用 `C:/…`/`E:/…`（`/c/…` 不转换）。
- 🚨 **BUILD SUCCESSFUL ≠ 测试跑了** → 必解析 `app/build/test-results/testDebugUnitTest/*.xml` 数用例。
- 离线无新依赖；无 ui-test / robolectric / androidx.test。
- 🚨 沙箱只能跑系统目录的 exe；**唯一例外 `MuMuManager.exe`**（可拉起模拟器实例）。

## 发版

- 应用名**未竟**；namespace=applicationId=`io.github.molishadaze.weijing`；minSdk 29 / target 35。
- 当前 **1.4.0 / versionCode 10400**。`versionCode = major*10000+minor*100+patch`。
  **只改 name 不改 code = 没发新版**。
- 发版跑 **`android/release_gitee.py`**（纯标准库，自己读版本与仓库路径，建发行版 + 传 APK + 回读校验）。
  令牌走 `GITEE_TOKEN`（优先）或 `android/.gitee-token`。`--dry-run` / `--prune N`。
- 🚨 脚本两个已踩的坑：① `target_commitish` 必须读仓库的 `default_branch`；② **multipart 附件名必须
  显式指定**，否则「已存在则跳过」永远匹配不上 → 每跑一次多传一份。
- 同步清单：`build.gradle.kts`、`README.md` 版本表、`strings.xml:app_name`、通知渠道 description。
- 签名 `android/release.keystore`（别名 `weijing`，`CN=Weijing`，SHA-256 `1bb71eeb…`）+
  `keystore.properties`（均 gitignore）；**.gitignore 必须保留**。`isMinifyEnabled=false` → release ≈21MB。
- 已声明 `INTERNET`、`POST_NOTIFICATIONS`、`REQUEST_INSTALL_PACKAGES`；不加 `ACCESS_NETWORK_STATE`。

## 应用内自更新（细节见技能 `android-app-self-update`）

- **Gitee 主 + GitHub 备**，并行查、取 versionCode 大者。常量在 `data/AppUpdateSource.kt` 顶部：
  `GITEE_REPO = "weijingzhishi/weijing"`、`GITHUB_REPO = "Molisha-Daze/dailywork"`。
  🚨 两处都要**实名**且**必须公开**（私有仓库附件匿名拿不到 → 必静默失败）。
- 🚨 网络实测：`api.github.com` ✅、`release-assets.githubusercontent.com` ✅，但 **`github.com` ❌**。
  → GitHub 下载禁用 `browser_download_url`，必须走 `api.github.com/.../assets/{id}` +
  `Accept: application/octet-stream`。🔴 **GitHub 备源至今 0 条 release**（`/releases` 返回空数组）
  → 双源实际只有 Gitee 一源在工作。
  ⚠️ 本机 hosts 有 2886 行 GitHub520，**必须 `curl --resolve` 才算数**。
- 🚨 **Gitee 匿名 API 会限流**（打爆后本机 IP 全线 403，冷却很久；响应是**纯文本**非 JSON）；
  **但附件下载直链不受限**（→ `foruda.gitee.com` 200）。排查期响应先落盘再离线解析，别反复刷。
- Gitee ⚠️ 无 `size` 字段；`assets` 混着自动源码包（靠 `.apk` 过滤，**别取下标 0**）；
  `/releases` 是**升序**；单附件 ≤100MB / 仓库总量 ≤1GB。
- 文件：`data/AppUpdateSource.kt`、`util/AppUpdatePolicy.kt`（纯规则 + 单测 15 条）、
  `util/ApkDownloader.kt`、`util/ApkInstaller.kt`、`ui/components/AppUpdateHost.kt`、
  `res/xml/update_file_paths.xml`；`MainScreen` 顶层挂 `AppUpdateHost`；`AppSettings` 24h 限频。
- 🚨 `ApkInstaller.verify()` 要求 `longVersionCode` **与发布信息严格相等**，否则「已阻止安装」。
- **无静默安装可能**；**首个带自更新的包仍需手动发一次**。
- ⚠️ **静默检查失败是故意静默的**（`silent=true` + 取不到版本 → `Idle`）；只有**手动检查**
  （管理中心 → 关于 → 检查更新）才报「更新未完成」。
- ✅ 2026-09-22 实机走通全流程（10399 → 10400：发现新版 → 下载 21.7MB → 权限引导 → 调起安装器 → 成功）。
- ⚠️ 欠账：更新日志弹窗原样显示 Markdown（`##`/`**` 没渲染）。

## 仓库 / 文档

- **GitHub 仓库 = `Molisha-Daze/dailywork`**（= `AppUpdateSource.GITHUB_REPO`，push 可达）。
  🚨 仓库根已有 `README.md`（`abce663`）+ `docs/screenshots*.png`；**不要**再写一份重复的。
- 🔥 **README 语气要客气、平实**（用户对「装逼感」敏感），开头需体现「**纯自用**」+「**纯 AI 生成**」。
  🔥 **只写功能**，不写取舍/联网说明/自更新/发版/目录结构 —— 那些属于源码注释与 `android/README.md`。
  面向使用者的文档不要堆架构与运维内容，且**废话要少**（用户已就此提过两轮）。
- 🔥 **截图必须是手机竖屏比例**。MuMu 默认 tablet.1 / 1920×1080 是横屏，截出来很难看；
  改 `resolution_mode=custom` + 1080×1920 后 **physical density 会掉到 10**，
  必须 `adb shell wm density 440` 覆盖，否则界面挤成一团、字变点阵。改完要重启实例。
  用完复原：`wm density reset` + `resolution_mode` 回 `tablet.1`。竖屏 tab 坐标 y≈1854。
- 🚨 本机能 `git push`（Windows 凭据管理器有条目），但**该凭据不给 API 写权限**
  （`PATCH /repos/…` → 401）。改 description / topics / homepage **只能用户手动在网页做**。
- ⚠️ `gh` CLI **未安装**；`android/.gitee-token` 是 Gitee 的，对 GitHub 无效。
- 文档类改动：`README.md`（项目介绍）/ `android/README.md`（深度技术说明，含签名与发版细节）；
  `android/gen_preview.py` 拼截图（需 `…/python/envs/default/Scripts/python.exe`，系统 python 无 PIL）。
- 历史大文件（**评估为可删，待用户确认**）：`P3执行与发版-20260922.md`、`P0-P1执行验收-20260921.md`、
  `优化评估-未竟-20260921.md`、`优化评估-可行性核验-20260921.md`、`音效方案-未竟-20260922.md`。
  ⚠️ 后者的 **§10 实施记录**（DAY_DONE 改为 `completesDay` 透传）与
  `优化评估-未竟` 里的 **P2 待办**（`SettingsScreen.kt` 仍有 `Color(0xFF10B981)`）是未落地项，删前要摘出。
  `.iconwork/` 是图标/截图工作区（gitignore；竖屏截图在 `shots-portrait/`）。

## 图标（`ui/components/HabitIcons.kt` 唯一来源）

- `UiIcons` 20 + `HabitIcons` 15；只依赖 `material-icons-core`。
- 🚨 必须 `addPath(pathData = addPathNodes(d))`，**不能** `path { addPathNodes(d) }`（返回值被丢弃 → 图标空白）。改完跑 `HabitIconsTest`。
- key = `HabitIcons` ↔ `Habit.iconName`，写错**静默回退 Star**；删图标四处同删。
- 🔥 启动图标：画布 108dp、启动器只看中间 72dp、**安全区仅 66dp**；脚本 `android/gen_icons.py`。

## 导航 / 结构

4 tab：**今日 / 历史 / 计数器 / 管理中心**；`MainScreen` 用整数 index 切 tab，**无 navigation-compose**。
管理中心 = 金刚区（视觉效果/关于/备份/提醒）+ 计划清单（`SettingsScreen` 内嵌 `HabitManageSection`）。
**别再开新 tab**。「独立计数器」(StandaloneCounter) ≠ `Habit.isCounter`。

- 🚨 **今日页不许用 `if(空) 空状态 else 列表` 分流**：一分流格言卡就被关进 else。恒为一个 LazyColumn：
  头部卡（进度卡 / 「今天没有排期」卡 / `EmptyState` 三选一）→ 格言卡 →（今天没排期时）即将到来 → 今日习惯。
- 今日页兜底窗口 **60 天**。
- 每日格言降级链 **当日缓存 → 异步远程 → 内置库**；首帧给内置句，**绝不等网络**；每天最多请求一次。
  🚨 取模用 `toEpochDay()`（不是 `dayOfYear`）且必须 `.mod()`。
- 计数器卡片：`hasLimit` 分流两种密度；无上限时不写「无上限自由计数」、不画分隔线；内边距 14dp；按钮 40dp `IconActionBox`。

## 🚨 「一次性任务」判定（改卡片前必读）

`HabitSchedule.isOneShot(habit)`：`TYPE_NONE` **或**（有 endDate 且区间内只落得到一天）。
- 不能只判 `recurrenceType == "none"`：伪循环语义等同单次，旧代码只看字面值 → 卡片照样挂「连续 1 天」，
  而排期说明条件 `type != daily` 刚好一起藏掉（两头都错）。
- 落点：`HabitCard` 用 `isOneShot` 决定「连续/最长」与排期行；`scheduleLabel()` 对一次性统一回「仅 YYYY-MM-DD」。
- 单测 8 条在 `HabitScheduleTest`。

## Room（version = 4）

迁移：`1_2` 建 `standalone_counters`；`2_3` habits 加 `isParentPlan`/`subTasks`、check_ins 加
`completedSubTaskIds`；`3_4` counters 加 `resetPeriod`/`resetIntervalDays`/`periodStartDate` + 建
`counter_period_logs`（FK CASCADE）。
改实体必须：①写显式 `Migration` 并 `addMigrations()`；②导出 JSON 进版本管理。
**禁用 `fallbackToDestructiveMigration()`**。逐字段比对导出 JSON；`ALTER TABLE` 一句一列，NOT NULL 新列带 DEFAULT。
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
10. 🚨 **Kotlin 块注释可嵌套**：注释里出现 `/*`（如写路径 `tags/*.zip`）会吞掉后面代码，
    报 `Missing '}'` + `Unclosed comment` 且**行号完全不相关**。只在注释里炸，字符串里无害。
11. ⚠️ release 包里 `res/xml/*.xml` 被 AGP **改名缩短**（如 `res/88.xml`），不是缺失；
    核验要在缩短名 xml 里按 **UTF-16** 搜 `cache-path`。

## 触觉 / 音效（v1.3.0）→ 完整方案见根目录 `音效方案-未竟-20260922.md`

- 触觉 `util/Haptics.kt`；只在 **Compose UI 层**调用（VM 无 Context）。完成 STRONG / 取消撤销 LIGHT /
  计数只在「刚好达标」那次 STRONG；同级 70ms 节流且**分档独立计时**。
- 音效一律走 **`util/Feedback.kt`**（`Feedback.fire(Event.X)`）：Event 把 (触觉档位, 音效) 写进构造参数，
  **漏配直接编译失败**。默认 7 响 / 2 关（`UNDO`、`STEP_SOFT`）。
- 音效**必须比触觉保守**：无点击音/导航音；**通知栏打卡路径只振不响**。
- 音频属性走 **`USAGE_ASSISTANCE_SONIFICATION`（STREAM_SYSTEM）**：走媒体流会导致静音下照样出声；
  代价 = 系统音量 0 时听不见 → 设置页必须有**试听 + 系统音量诊断**。
- 🚨 改触发点：节流 **150ms 且按音效各自计时**；WAV 首尾强制淡入淡出；`SoundPool.load()` 异步，
  未就绪时 `play()` 直接返回；`SubTaskSection` **新参数必须透传**。
- 「今日全部完成」用 **`completesDay` 参数透传**，**不用** progress 边沿检测（会叠成两声噪音）。

## 主题（`ui/theme/AppTheme.kt` 唯一真相；禁用 Material You）

- 🔴 主色低饱和（≤30%）且与计划/计数器色板 ΔE ≥ 25（实测最小 31.1），`AppThemeTest` 守回归。
- 🔴 完成/选中/进度/FAB 取 `colorScheme.primary`，禁止写死 `Color(0xFF10B981)`。
- 🚨 主色上的文字/图标用 `onPrimary`，禁止 `Color.White`。
- ⚠️ 欠账：main 除 AppTheme 外仍有 18 处 `Color(0xFF…)`（8 文件，多为图标 tint）。主题 id 存 `AppSettings.themeId`。

## 表单弹窗（`ui/components/FormDialog.kt`）

「新建计划」/「新建独立计数器」共用那套 `Form*` 组件（`AppFormDialog`/`FormSection`/`FormTextField`/
`FormDescriptionField`/`FormLabeledField`/`FormGroupCard`/`FormSwitchRow`/`FormSegmentedControl`/
`FormChip`/`FormColorPicker`/`FormTokens`）。**新增表单直接用这套，别手写间距圆角。**
标签在输入框**上方**；底部按钮在滚动区外；内容区 `heightIn(max = screenHeightDp*0.72f)`；
弹窗内只允许**一个**滚动区；`singleLine` 与 `minLines/maxLines` 互斥。

月历三件套：`CalendarMonthView.kt`（入口，**只持有 `currentMonth`/`selectedDate`**）/
`CalendarMonthGrid.kt`（网格/`DayCell`/图例）/ `CalendarDayPlanList.kt`（当天清单）。
🚨 **状态宿主不能下沉**：两个 remember 挪进小块 → 翻月重建 → 选中日期丢失。
「为某天新建计划」默认类型：未来日 `TYPE_NONE`；**今天 `TYPE_DAILY`**。

## 现状 / 欠账

- P1+P3 已执行（`b04cd89`、`c028632`）；P2 未做：D1 颜色治理（仅 `HabitManageSection` 计数目标徽章
  `#10B981` 真违规）/ D2 星期四合一 / D3 `notificationSettingsIntent` 与 `calAccent`+`detailAccent` 去重。
- main ≈11k 行 / 50 文件；大文件：AddEditHabitDialog / SettingsScreen / StandaloneCountersScreen /
  HabitRepository / HabitCard / CalendarDayPlanList。
- 已知弃用警告：`AddEditHabitDialog.kt`/`MainScreen.kt` 的 `LocalLifecycleOwner`。
