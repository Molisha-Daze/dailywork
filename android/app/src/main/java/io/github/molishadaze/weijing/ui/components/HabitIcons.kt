package io.github.molishadaze.weijing.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 「未竟」自带的矢量图标集。
 *
 * 为什么不直接用 androidx.compose.material:material-icons-extended？
 * —— 那个包 35.7 MB / 两千多个图标，本工程只用到三十来个。不开 R8 时整个 jar 都会进 dex，
 * 直接把 APK 撑到 59 MB。自绘这 35 个之后依赖被彻底移除，体积与构建时间一起降。
 *
 * 路径数据取自官方 Material Design Icons（Apache-2.0），通过 [addPathNodes] 解析 SVG 的 d 串再交给
 * `Builder.addPath`，没有人工转写，因此不会出现「路径抄错导致图形畸变」这类问题。
 * 新增图标：https://fonts.google.com/icons 选一个，把 svg 里的 d 串贴进来即可。
 *
 * ⚠️ 改完务必跑 `HabitIconsTest`。图标这东西「代码看着对」和「真能画出来」是两回事 ——
 * 本文件曾经因为一行写法错误，让全部图标静默变成空白，而编译与单测全绿。
 */
private fun icon(name: String, vararg paths: Pair<String, Boolean>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { (d, evenOdd) ->
            // ⚠️ 节点必须经 addPath(pathData = ...) 传进去，这是唯一的入口。
            //
            // 踩过的坑：写成 `path { addPathNodes(d) }`（尾随 lambda）会**编译通过、零警告、
            // 单测不报错**，但全部图标渲染成一片空白 —— 因为库里的 addPathNodes 是普通函数
            // `(String) -> List<PathNode>`，**不是** PathBuilder 的扩展；写在 lambda 里当语句时
            // 它的返回值被静默丢弃。而 PathBuilder 只有 moveTo/lineTo 这类基本指令，没有任何
            // 接收「节点列表」的方法，公开的 `path {}` 扩展连 pathFillType 都不收。
            //
            // 教训：「编译成功」+「d 串与源 SVG 逐字节一致」两条都验不出这个 bug，
            // 必须真去数 ImageVector 里的节点。HabitIconsTest 负责守这条线（含反向探针）。
            addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Black),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
    }.build()

/** 界面通用图标（Tab、按钮、状态指示等）。 */
object UiIcons {
    val Remove: ImageVector by lazy { icon("Remove", "M19 13H5v-2h14v2z" to false) }
    val Alarm: ImageVector by lazy { icon("Alarm", "m22 5.72-4.6-3.86-1.29 1.53 4.6 3.86L22 5.72zM7.88 3.39 6.6 1.86 2 5.71l1.29 1.53 4.59-3.85zM12.5 8H11v6l4.75 2.85.75-1.23-4-2.37V8zM12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9a9 9 0 0 0 0-18zm0 16c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z" to false) }
    val Download: ImageVector by lazy { icon("Download", "M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z" to false) }
    val Tune: ImageVector by lazy { icon("Tune", "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z" to false) }
    val Palette: ImageVector by lazy { icon("Palette", "M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10a2.5 2.5 0 0 0 2.5-2.5c0-.61-.23-1.2-.64-1.67a.528.528 0 0 1-.13-.33c0-.28.22-.5.5-.5H16c3.31 0 6-2.69 6-6 0-4.96-4.49-9-10-9zm5.5 11c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm-3-4c-.83 0-1.5-.67-1.5-1.5S13.67 6 14.5 6s1.5.67 1.5 1.5S15.33 9 14.5 9zM5 11.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S7.33 13 6.5 13 5 12.33 5 11.5zm6-4c0 .83-.67 1.5-1.5 1.5S8 8.33 8 7.5 8.67 6 9.5 6s1.5.67 1.5 1.5z" to false) }
    val Vibration: ImageVector by lazy { icon("Vibration", "M0 15h2V9H0v6zm3 2h2V7H3v10zm19-8v6h2V9h-2zm-3 8h2V7h-2v10zM16.5 3h-9C6.67 3 6 3.67 6 4.5v15c0 .83.67 1.5 1.5 1.5h9c.83 0 1.5-.67 1.5-1.5v-15c0-.83-.67-1.5-1.5-1.5zM16 19H8V5h8v14z" to false) }
    val CalendarMonth: ImageVector by lazy { icon("CalendarMonth", "M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z" to false) }
    val ArrowDownward: ImageVector by lazy { icon("ArrowDownward", "m20 12-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z" to false) }
    val ArrowUpward: ImageVector by lazy { icon("ArrowUpward", "m4 12 1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z" to false) }
    val PhotoCamera: ImageVector by lazy { icon("PhotoCamera", "M9 2 7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z" to false) }
    val Description: ImageVector by lazy { icon("Description", "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" to false) }
    val ExpandLess: ImageVector by lazy { icon("ExpandLess", "m12 8-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z" to false) }
    val ExpandMore: ImageVector by lazy { icon("ExpandMore", "M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z" to false) }
    val FormatListBulleted: ImageVector by lazy { icon("FormatListBulleted", "M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5zm0-6c-.83 0-1.5.67-1.5 1.5S3.17 7.5 4 7.5 5.5 6.83 5.5 6 4.83 4.5 4 4.5zm0 12c-.83 0-1.5.68-1.5 1.5s.68 1.5 1.5 1.5 1.5-.68 1.5-1.5-.67-1.5-1.5-1.5zM7 19h14v-2H7v2zm0-6h14v-2H7v2zm0-8v2h14V5H7z" to false) }
    val OpenInNew: ImageVector by lazy { icon("OpenInNew", "M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z" to false) }
    val LocalFireDepartment: ImageVector by lazy { icon("LocalFireDepartment", "m16 6-.44.55c-.42.52-.98.75-1.54.75C13 7.3 12 6.52 12 5.3V2S4 6 4 13c0 4.42 3.58 8 8 8s8-3.58 8-8c0-2.96-1.61-5.62-4-7zm-4 13c-1.1 0-2-.87-2-1.94 0-.51.2-.99.58-1.36L12 14.3l1.43 1.4c.37.37.57.85.57 1.36 0 1.07-.9 1.94-2 1.94zm3.96-1.5c.04-.36.22-1.89-1.13-3.22L12 11.5l-2.83 2.78C7.81 15.62 8 17.16 8.04 17.5A5.982 5.982 0 0 1 6 13c0-3.16 2.13-5.65 4.03-7.25a4.024 4.024 0 0 0 3.99 3.55c.78 0 1.54-.23 2.18-.66A6.175 6.175 0 0 1 18 13c0 1.79-.79 3.4-2.04 4.5z" to false) }
    val Inbox: ImageVector by lazy { icon("Inbox", "M19 3H5c-1.1 0-2 .9-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5v-3h3.56c.69 1.19 1.97 2 3.45 2s2.75-.81 3.45-2H19v3zm0-5h-4.99c0 1.1-.9 2-2 2s-2-.9-2-2H5V5h14v9z" to false) }
    val RadioButtonUnchecked: ImageVector by lazy { icon("RadioButtonUnchecked", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z" to false) }
    val Tag: ImageVector by lazy { icon("Tag", "M20 10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4h4zm-6 4h-4v-4h4v4z" to false) }
    val RestartAlt: ImageVector by lazy { icon("RestartAlt", "M12 5V2L8 6l4 4V7c3.31 0 6 2.69 6 6 0 2.97-2.17 5.43-5 5.91v2.02c3.95-.49 7-3.85 7-7.93 0-4.42-3.58-8-8-8zm-6 8c0-1.65.67-3.15 1.76-4.24L6.34 7.34A8.014 8.014 0 0 0 4 13c0 4.08 3.05 7.44 7 7.93v-2.02c-2.83-.48-5-2.94-5-5.91z" to false) }
    val VolumeUp: ImageVector by lazy { icon("VolumeUp", "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" to false) }
}

/**
 * 习惯图标池。key 即数据库 `Habit.iconName` 的取值，由 [getIconVector] 解析；
 * [PRESET_ICONS] 是它的「可选子集」。增删图标时这三处要一起看。
 */
object HabitIcons {
    val DirectionsRun: ImageVector by lazy { icon("DirectionsRun", "M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9 1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z" to false) }
    val Book: ImageVector by lazy { icon("Book", "M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z" to false) }
    val LocalDrink: ImageVector by lazy { icon("LocalDrink", "m3 2 2.01 18.23C5.13 21.23 5.97 22 7 22h10c1.03 0 1.87-.77 1.99-1.77L21 2H3zm9 17c-1.66 0-3-1.34-3-3 0-2 3-5.4 3-5.4s3 3.4 3 5.4c0 1.66-1.34 3-3 3zm6.33-11H5.67l-.44-4h13.53l-.43 4z" to false) }
    val FitnessCenter: ImageVector by lazy { icon("FitnessCenter", "M20.57 14.86 22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z" to false) }
    val SelfImprovement: ImageVector by lazy { icon("SelfImprovement", "M21 16v-2c-2.24 0-4.16-.96-5.6-2.68l-1.34-1.6A1.98 1.98 0 0 0 12.53 9h-1.05c-.59 0-1.15.26-1.53.72l-1.34 1.6C7.16 13.04 5.24 14 3 14v2c2.77 0 5.19-1.17 7-3.25V15l-3.88 1.55c-.67.27-1.12.93-1.12 1.66C5 19.2 5.8 20 6.79 20H9v-.5a2.5 2.5 0 0 1 2.5-2.5h3c.28 0 .5.22.5.5s-.22.5-.5.5h-3c-.83 0-1.5.67-1.5 1.5v.5h7.21c.99 0 1.79-.8 1.79-1.79 0-.73-.45-1.39-1.12-1.66L14 15v-2.25c1.81 2.08 4.23 3.25 7 3.25z" to false) }
    val DirectionsBike: ImageVector by lazy { icon("DirectionsBike", "M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm5.8-10 2.4-2.4.8.8c1.3 1.3 3 2.1 5.1 2.1V9c-1.5 0-2.7-.6-3.6-1.5l-1.9-1.9c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 1.4 0 .6.2 1.1.6 1.4L11 14v5h2v-6.2l-2.2-2.3zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5z" to false) }
    val Nightlight: ImageVector by lazy { icon("Nightlight", "M14 2c1.82 0 3.53.5 5 1.35-2.99 1.73-5 4.95-5 8.65s2.01 6.92 5 8.65A9.973 9.973 0 0 1 14 22C8.48 22 4 17.52 4 12S8.48 2 14 2z" to false) }
    val Restaurant: ImageVector by lazy { icon("Restaurant", "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z" to false) }
    val Work: ImageVector by lazy { icon("Work", "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z" to false) }
    val Medication: ImageVector by lazy { icon("Medication", "M6 3h12v2H6zm11 3H7c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 9h-2.5v2.5h-3V15H8v-3h2.5V9.5h3V12H16v3z" to false) }
    val Savings: ImageVector by lazy { icon("Savings", "m19.83 7.5-2.27-2.27c.07-.42.18-.81.32-1.15A1.498 1.498 0 0 0 16.5 2c-1.64 0-3.09.79-4 2h-5C4.46 4 2 6.46 2 9.5S4.5 21 4.5 21H10v-2h2v2h5.5l1.68-5.59 2.82-.94V7.5h-2.17zM13 9H8V7h5v2zm3 2c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z" to false) }
    val MusicNote: ImageVector by lazy { icon("MusicNote", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" to false) }
    val DirectionsWalk: ImageVector by lazy { icon("DirectionsWalk", "M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9 7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7" to false) }
    val WbSunny: ImageVector by lazy { icon("WbSunny", "m6.76 4.84-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7 1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91 1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z" to false) }
    val Star: ImageVector by lazy { icon("Star", "M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" to false) }
}

/** 数据库里的 iconName -> 矢量图。名字不认识时回退到 Star，不要静默留空。 */
fun getIconVector(iconName: String): ImageVector = when (iconName) {
    "Run" -> HabitIcons.DirectionsRun
    "Book" -> HabitIcons.Book
    "Water" -> HabitIcons.LocalDrink
    "Fitness" -> HabitIcons.FitnessCenter
    "Meditation" -> HabitIcons.SelfImprovement
    "Bike" -> HabitIcons.DirectionsBike
    "Sleep" -> HabitIcons.Nightlight
    "Meal" -> HabitIcons.Restaurant
    "Work" -> HabitIcons.Work
    "Medicine" -> HabitIcons.Medication
    "Money" -> HabitIcons.Savings
    "Music" -> HabitIcons.MusicNote
    "Walk" -> HabitIcons.DirectionsWalk
    "Sun" -> HabitIcons.WbSunny
    else -> HabitIcons.Star
}

/**
 * 新建/编辑习惯时的图标候选。顺序即界面网格顺序。
 *
 * key 与 [getIconVector] 的解析分支一一对应，删除图标时**三处一起删**：
 * 这里的候选行、[HabitIcons] 里的矢量定义、`getIconVector` 的 when 分支
 * （外加 `HabitIconsTest` 里那份显式 key 清单）。
 * 只删一半会出现「有候选却画不出图形」或「解析得到不存在的 key」。
 * 「爪印 Pet」就是按这个流程整体移除的（2026-09-21）。
 */
val PRESET_ICONS: List<Pair<String, ImageVector>> = listOf(
    "Run" to HabitIcons.DirectionsRun,
    "Book" to HabitIcons.Book,
    "Water" to HabitIcons.LocalDrink,
    "Fitness" to HabitIcons.FitnessCenter,
    "Meditation" to HabitIcons.SelfImprovement,
    "Bike" to HabitIcons.DirectionsBike,
    "Sleep" to HabitIcons.Nightlight,
    "Meal" to HabitIcons.Restaurant,
    "Work" to HabitIcons.Work,
    "Medicine" to HabitIcons.Medication,
    "Money" to HabitIcons.Savings,
    "Music" to HabitIcons.MusicNote,
    "Walk" to HabitIcons.DirectionsWalk,
    "Sun" to HabitIcons.WbSunny,
    "Star" to HabitIcons.Star,
)
