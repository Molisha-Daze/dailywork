# 项目约定 — dailywork「未竟」习惯打卡

> **单一实现：`android/`**（Kotlin + Compose + Room，**version = 4**）—— 唯一产物，出 APK 的那份。
> 原 `src/` 网页版（React19+TS+Tailwind4+Vite + IndexedDB，只是安卓 App 的**浏览器演示原型**）
> 已于 **2026-09-20 归档并删除**。原因：零代码共用、数据不互通、落后一个版本，每个功能要写两遍。
> - 存档：`E:\anzhuo\weijing-web-archive-1.0-20260920.zip`（41 条目 / 233KB，内含 `ARCHIVE-NOTE.txt`
>   写明重建方式与 8 条已知差距）；不含 `node_modules`（锁文件齐全，`npm install` 可重建）。
> - 另有 31 个文件在 git 历史里。⚠️ HEAD 停在 `8fed251 plfz`，工作区有**安卓的未提交改动**，
>   恢复网页版必须用精确路径 `git checkout HEAD -- src`，**绝不能 `git checkout -- .`**。

## 构建（Windows 离线工具链）

工具链在 **E:\anzhuo\toolchain**：`jdk-17.0.20.1+1`、`gradle-8.9/bin/gradle`、
`android-sdk`（platform-35 + build-tools 35.0.0）；无 ANDROID_HOME、无 gradlew。

```bash
export PATH="/c/Users/admin/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:$PATH"
export JAVA_HOME="E:\\anzhuo\\toolchain\\jdk-17.0.20.1+1"
export ANDROID_HOME="E:\\anzhuo\\toolchain\\android-sdk"
export GRADLE_USER_HOME="E:\\anzhuo\\toolchain\\.gradle-home"
"/e/anzhuo/toolchain/gradle-8.9/bin/gradle" -p "C:/Users/admin/Documents/trae_projects/lunwen/dailywork/android" \
  :app:assembleRelease :app:testDebugUnitTest --console=plain --no-daemon
```

- PATH 必须补 PortableGit 的 `usr/bin`，否则 ls/tail/cat 全找不到。别从 Bash 调 `cmd`（被拦）；
  输出重定向到文件再读，别用管道。全量 ~1.5min，增量 ~25s。
- 核验 APK：`build-tools/35.0.0/aapt.exe dump badging` + `apksigner.bat verify --print-certs`。

## 发版（当前 1.0.1）

- 应用名 **未竟**；`namespace` = `applicationId` = **`io.github.molishadaze.weijing`**；
  源码 `java/io/github/molishadaze/weijing/`。minSdk 29 / target 35；**无 INTERNET 权限**（全程离线）。
- `versionCode = major*10000 + minor*100 + patch`（1.0.1 → 10001）。⚠️ 别用「与 versionName 末段对齐」
  的老写法（1.0.1 与 1.1 都算成 11 会撞车）。**只改 name 不改 code = 没发新版**；
  国产启动器按 versionCode 缓存图标，涨号也是图标刷新前提。
- 签名 `android/release.keystore`（PKCS12/RSA2048/10000 天，别名 `weijing`，密码
  `WjRXOdEtu3hwYmTqsX3ZmQhU`）+ `keystore.properties`，**均 gitignore**；缺失时 release 在**签名阶段报错**
  （刻意设计）。证书 SHA-256 `1bb71eeb…a211f`。换签名/包名 → 老版本必须**卸载重装、数据不迁移**。
- release **20.90MB**，`isMinifyEnabled=false`（R8 关闭是用户明确选择；开启可压到 3.14MB 但需按版本
  归档 `mapping.txt`）。核验 dex 用 Python zipfile 按字节算，`grep -a` 对二进制会给出矛盾结论。
- 「关于」页版本号走 `PackageManager` 自动读（`appVersionName()`），**只改 gradle 即可**，不用改文案。
- 改版同步清单（已随网页版归档而缩短）：`strings.xml:app_name`、`build.gradle.kts`、
  `NotificationHelper` 渠道 description、`android/README.md`。
- ⚠️ `.gitignore` **必须保留** —— 它兼管安卓的 `*.keystore` / `*.jks` / `keystore.properties` /
  `.iconwork/` / `.gradle/` 规则，删掉会让签名私钥被 git 跟踪（真实事故级）。

## 图标

- `ui/components/HabitIcons.kt` 是**唯一来源**：`UiIcons` 19 + `HabitIcons` 16，同时喂养
  `getIconVector()` 与 `PRESET_ICONS`。依赖 `material-icons-core`（已移除 35.7MB 的 extended）。
- 🚨 **必须走 `addPath(pathData = addPathNodes(d), …)`，绝不能写 `path { addPathNodes(d) }`。**
  `addPathNodes` 是普通函数、**不是** `PathBuilder` 扩展；写在尾随 lambda 里当语句会**编译零警告、
  单测不报错，但返回值被丢弃 → 图标全部渲染成空白**（真实事故）。`Builder.addPath` 是唯一入口。
  改完必须跑 `ui/components/HabitIconsTest`（反射遍历 35 个图标断言 `ImageVector.root` 节点数 > 0，
  纯 JVM 可测，含反向探针证明不空转）。
- 新增图标：jsdelivr 拉 `@material-design-icons/svg@latest/filled/<name>.svg`（国内可达）→
  `addPathNodes(d)` 解析，**零人工转写**。查 Compose API 用 `javap` 列 jar 真实签名，别凭记忆。
- **图标 key** 唯一纽带 = `HabitIcons` ↔ `Habit.iconName`（原网页 `getHabitIconComponent` 已归档）。
  写错 key 会**静默回退成 Star**，不报错。
- 🔥 **启动图标（自适应）：画布 108dp，启动器只看中间 72dp，安全区仅 66dp** —— 前景层图案必须缩到
  ~66dp 四周留白，**「全出血」只适用于背景层**。生成脚本 `android/gen_icons.py`（源图在
  `~/.workbuddy/clipboard-images/`）。`mipmap-anydpi-v26/ic_launcher.xml` 与各密度 `ic_launcher*.png`
  **别删**（早期缺失导致编译不过）。
  🚨 **核验「有没有被蒙版切」必须按半径算，别用外接框对角线**（66×58dp 框对角线半长 43.9dp 看着
  "远超 36dp"，其实四角无墨 —— 犯过这错、报过假警报）。正确 = 遍历墨点取 `max(hypot(dx,dy))`；
  分 A 可见区外=无害 / B 方形内且 >36dp=真被切 / C 33~36dp=只贴边。**前景层是不透明整图**，
  求图案范围不能用 `alpha.getbbox()`。当前实测最远墨点 34.7dp vs 蒙版 36dp，被裁 0px。
  **完整可复用流程见用户级 skill `android-adaptive-launcher-icon`。**

## 导航 / 结构

4 个 tab：**今日打卡 / 历史回顾 / 计数器 / 管理中心**。
管理中心 = 上半屏 4 个金刚区（字号/关于/备份/提醒）+ 下半屏计划清单（`SettingsScreen` 内嵌
`HabitManageSection`）。⚠️ 别再加「习惯管理」tab；金刚区是**固定 4 入口**，别往里塞东西
（振动开关放金刚区**下方独立卡片**）。`HabitManageSection` **不用 LazyColumn**（嵌在外层 LazyColumn 里）。
「独立计数器」= 脱离习惯的**独立实体**；`Habit.isCounter` = 习惯内计数目标 —— **同名不同义**。

## Room（version = 4）

迁移史：`1_2` 建 `standalone_counters`；`2_3` habits 加 `isParentPlan`/`subTasks`、check_ins 加
`completedSubTaskIds`；`3_4` standalone_counters 加 `resetPeriod`/`resetIntervalDays`/`periodStartDate`、
新建 `counter_period_logs`（FK CASCADE）。

改实体必须同时：①写显式 `Migration(old,new)` 并在 `addMigrations()` 注册；②把新导出的
`app/schemas/.../N.json` 纳入版本管理。**绝不可用 `fallbackToDestructiveMigration()`**（静默删库）。
手写迁移后逐字段比对导出 JSON 的**列名/affinity/notNull**；`ALTER TABLE` 一句只能加一列，
且 **NOT NULL 新列必须带 DEFAULT** —— 实体不写 `@ColumnInfo(defaultValue)` 时 Room 不校验该列 default，
所以「DDL 带 DEFAULT、实体不带」既安全又必要。

- 子任务：`model/SubTask.kt` + `data/Converters.kt`（org.json 手写 TypeConverter）。
  **完成判定 = 子任务全部勾满**；`toggleSubTaskGroup` 一键全勾/全清；`isParentPlan` 由有无子任务推导。
  备份 `BackupCodec` **FORMAT_VERSION = 4**（v1~v3 仍可解析，缺的字段各取默认值）。
- 计数器周期归零（1.0.1 新增）：`util/CounterPeriodCalculator.kt` 纯函数（自然日 / 自然周周一 / 自然月 /
  每 N 天滚动）+ `CounterPeriodCalculatorTest`（覆盖跨月跨年闰年）。**惰性结算**：
  `HabitRepository.rolloverCounterPeriods()` 在 MainScreen 的 ON_RESUME 与 `stepCounter`/`resetCounter`
  前触发，按天短路 + Mutex；归档进 `counter_period_logs`，同周期重复归档**合并累加**，空周期不留记录。

## 示例数据（`data/SampleData.kt`）

**4 习惯 + 2 计数器**：喝水3杯 / 力量与体能+4子任务 / 晨跑07:30 / 深度阅读21:00 /
冰箱可乐6-12有上限不归零 / 今日咖啡每日归零。
⚠️ **播种必须用持久标记 `AppSettings.sampleSeeded` 判定，不能靠「数据为空」** —— 否则用户删完习惯后
下次冷启动示例又长回来（已归档的网页版正是这缺陷，安卓没照抄）。老用户升级只补打标记、不塞示例；
手动入口在计划清单空状态「载入示例数据」（**追加**不覆盖）。

## Compose UI 坑（改视觉前必读）

1. **半透明色不能做带 elevation 的 Card 底色** —— M3 顺序 `graphicsLayer(clip=false)` → border →
   background → clip，阴影会从半透明背景透出糊成暗框。用 `color.compositeOver(surface)` 合成不透明色。
2. **`Text(fontSize=…)` 不覆盖行高** —— 会继承 bodyLarge 的 24sp lineHeight，裸写 `fontSize=12.sp`
   实际高 24dp。给 `style=` 或显式 `lineHeight`。
3. **别用 `CardDefaults.outlinedCardBorder()`**（取 `outlineVariant`，本主题只覆盖 `outline`）——
   要描边直接 `BorderStroke(1.dp, color)`。
4. **列表排序别挂 `updatedAt`** —— 写操作会刷新它，列表项每点一下都跳位。实体顺序按 id / 显式 sortOrder。
5. **「编译通过」≠「画得出来」** —— Compose 里把「返回值的函数」当语句写在 lambda 里会静默失效
   （编译器不报、单测不报、APK 正常）。改视觉代码后验证必须打在**真实执行路径**上。
6. **同一批并行 Edit 同一文件会互相覆盖**（工具按同一份原文写回）—— 同文件的改动必须串行提交。
   跨文件才能并行。

## 触觉反馈（`util/Haptics.kt` 全局单例，`HabitApplication.onCreate()` attach）

一律在 **Compose UI 层**调用，别塞进 ViewModel（纯 VM 无 Context，改它要动构造与 Factory）。
**完成 → STRONG；取消/撤销 → LIGHT**（同强度的话反复点击就能刷振动，反馈彻底失效）；计数类只在
**「刚好达标」那一次**给 STRONG（`count < target && count+1 >= target`），超标后不给；导航类
（切 tab / 翻月 / 开弹窗 / 表单输入 / 选图标）**一律不加**；同级 70ms 节流且**分档独立计时**
（共用一个时间戳会让连点的轻振吞掉紧随其后的达标强振）。

## 杂项坑

- minSdk 29 → java.time 原生可用，无需 desugaring。
- `Could not read workspace metadata … kotlin-dsl/…/metadata.bin` → kotlin-dsl 缓存损坏（构建被中断留的
  残骸），**删掉是安全的**；紧随的 KSP `lateinit property cleanFilenames` 是 KSP 内部错，`:app:clean` 即恢复。
- `gradle --stop` 与 `clean` 串在一条 Bash 命令里会让 Bash 收 SIGTERM（exit 1），但任务实际已执行。分开跑。
- 单测 `testImplementation(libs.junit)` 4.13.2，纯 JVM 逻辑（`StreakCalculator`、`CounterPeriodCalculator`）
  可直接测，不需 Robolectric。
- 既有弃用警告（未处理）：`AddEditHabitDialog.kt` / `MainScreen.kt` 的 `LocalLifecycleOwner`
  （compose-ui 版本；换 `lifecycle-runtime-compose` 需联网，离线环境暂不动）。
