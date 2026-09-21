package io.github.molishadaze.weijing.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 一套完整界面配色。
 *
 * 字段刻意只用 Material3 的语义槽（谁在什么背景上），不出现「浅灰 #F3F4F6」这种
 * 只有名字、没有角色的裸色值 —— 这样换主题时不会出现「某个角落忘了换」的问题。
 */
data class ThemePalette(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color
)

/**
 * 转 Material3 的 [ColorScheme]。
 *
 * 走 light/dark 两个前缀是为了让「本文件没有显式指定的槽」按正确的明暗基调兜底；
 * 二者接收的参数完全一致，剩下的只是选哪个构造函数的问题。
 */
fun ThemePalette.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        outline = outline, outlineVariant = outlineVariant,
        scrim = scrim,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        outline = outline, outlineVariant = outlineVariant,
        scrim = scrim,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer
    )
}

// ---------------------------------------------------------------------------
// 六套主题配色
//
// ⚠️ 设计硬约束：**主色必须与「习惯 / 计数器色板」拉开距离。**
// 习惯色板是 #10B981 #3B82F6 #8B5CF6 #F59E0B #EC4899 #14B8A6，
// 计数器色板是 #EF4444 #F59E0B #10B981 #0EA5E9 #8B5CF6 #EC4899 —— 全是高饱和彩色，
// 代表的是「用户数据」（某个计划自己的颜色）。
//
// App 自身的 chrome（主色、按钮、完成态）如果也用高饱和彩，就会出问题：
//   1. 用户分不清「这抹绿是完成状态」还是「这个习惯恰好是翡翠绿」；
//   2. 换主题后完成态与某个习惯色撞在一起，观感像渲染出错。
//
// 所以六套主题的主色一律走**低饱和中性 / 大地色**（饱和度 ≤ 30%），把高饱和留给数据。
// 每套都用 CIELAB ΔE 验过：主色与色板任一颜色 ΔE > 30（ΔE > 25 即「明显不同的颜色」）。
// 这条线由 AppThemeTest 守着，将来改色不用担心悄悄撞回去。
// ---------------------------------------------------------------------------

/** 宣纸：暖砂米。白底偏暖，长时间看最不刺眼。 */
private val PaletteXuan = ThemePalette(
    isDark = false,
    primary = Color(0xFF6E5E4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DFD2),
    onPrimaryContainer = Color(0xFF453729),
    secondary = Color(0xFF7A7168),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DED6),
    onSecondaryContainer = Color(0xFF4B443D),
    tertiary = Color(0xFF6F6A5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2DCD0),
    onTertiaryContainer = Color(0xFF474339),
    background = Color(0xFFF7F4EF),
    onBackground = Color(0xFF2A251F),
    surface = Color(0xFFFFFDFA),
    onSurface = Color(0xFF2A251F),
    surfaceVariant = Color(0xFFEFEAE1),
    onSurfaceVariant = Color(0xFF6B645B),
    outline = Color(0xFFDED7CB),
    outlineVariant = Color(0xFFEAE4D9),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/** 雾霭：冷石板蓝灰。中性偏冷，chrome 感最强。 */
private val PaletteMist = ThemePalette(
    isDark = false,
    primary = Color(0xFF46586B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4ED),
    onPrimaryContainer = Color(0xFF2B3844),
    secondary = Color(0xFF5F6B77),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE6EC),
    onSecondaryContainer = Color(0xFF3C4753),
    tertiary = Color(0xFF5A6672),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDE4EA),
    onTertiaryContainer = Color(0xFF3A444E),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF1E2732),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2732),
    surfaceVariant = Color(0xFFE7EBF0),
    onSurfaceVariant = Color(0xFF626C78),
    outline = Color(0xFFD5DCE4),
    outlineVariant = Color(0xFFE3E8EE),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/**
 * 青瓷：低饱和灰绿。看着「有点颜色」但饱和度只有个位数，
 * 与色板里那支高饱和翡翠绿 #10B981 的 ΔE 为 39.8，不会混淆。
 */
private val PaletteCeladon = ThemePalette(
    isDark = false,
    primary = Color(0xFF5A6E62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6DE),
    onPrimaryContainer = Color(0xFF33453A),
    secondary = Color(0xFF647269),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E8E2),
    onSecondaryContainer = Color(0xFF3B4740),
    tertiary = Color(0xFF5E6E6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE7E4),
    onTertiaryContainer = Color(0xFF3B4744),
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF1F2A23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2A23),
    surfaceVariant = Color(0xFFE7EDE8),
    onSurfaceVariant = Color(0xFF626E65),
    outline = Color(0xFFD5DFD8),
    outlineVariant = Color(0xFFE3EAE5),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/** 墨曜：中性深灰黑。不用纯黑，避免 OLED 拖影并保留层次。 */
private val PaletteInk = ThemePalette(
    isDark = true,
    primary = Color(0xFFB9C2CC),
    onPrimary = Color(0xFF1B1E23),
    primaryContainer = Color(0xFF3A4048),
    onPrimaryContainer = Color(0xFFDCE3EA),
    secondary = Color(0xFFA8B2BD),
    onSecondary = Color(0xFF20242A),
    secondaryContainer = Color(0xFF373D45),
    onSecondaryContainer = Color(0xFFD6DDE4),
    tertiary = Color(0xFFA9B4BF),
    onTertiary = Color(0xFF20252B),
    tertiaryContainer = Color(0xFF384048),
    onTertiaryContainer = Color(0xFFD8DFE7),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE7EBF0),
    surface = Color(0xFF1B1E23),
    onSurface = Color(0xFFE7EBF0),
    surfaceVariant = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFA5AFBA),
    outline = Color(0xFF3A4048),
    outlineVariant = Color(0xFF2A2E35),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** 午夜：深靛蓝。蓝移能压住高亮区域的眩光，夜间久看最舒服。 */
private val PaletteMidnight = ThemePalette(
    isDark = true,
    primary = Color(0xFFA8BEDB),
    onPrimary = Color(0xFF131B29),
    primaryContainer = Color(0xFF2F3B52),
    onPrimaryContainer = Color(0xFFD3E1F5),
    secondary = Color(0xFFA6B2C4),
    onSecondary = Color(0xFF1A2231),
    secondaryContainer = Color(0xFF303A4D),
    onSecondaryContainer = Color(0xFFD8E2F2),
    tertiary = Color(0xFFAEBBD1),
    onTertiary = Color(0xFF1B2331),
    tertiaryContainer = Color(0xFF333D52),
    onTertiaryContainer = Color(0xFFDAE4F6),
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE4EAF3),
    surface = Color(0xFF171B25),
    onSurface = Color(0xFFE4EAF3),
    surfaceVariant = Color(0xFF262C3A),
    onSurfaceVariant = Color(0xFFA2AEC1),
    outline = Color(0xFF3A4254),
    outlineVariant = Color(0xFF262C3A),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** 暖夜：炭褐暖调。睡前记录用，暖底比冷底更不抑制困意。 */
private val PaletteEmber = ThemePalette(
    isDark = true,
    primary = Color(0xFFDCCAB3),
    onPrimary = Color(0xFF2A2118),
    primaryContainer = Color(0xFF4A3C2E),
    onPrimaryContainer = Color(0xFFEDDFCB),
    secondary = Color(0xFFCBBBA8),
    onSecondary = Color(0xFF2B2319),
    secondaryContainer = Color(0xFF453A2D),
    onSecondaryContainer = Color(0xFFEADCC9),
    tertiary = Color(0xFFD2C2AE),
    onTertiary = Color(0xFF2C2419),
    tertiaryContainer = Color(0xFF483C2F),
    onTertiaryContainer = Color(0xFFEDDFCD),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEFE7DC),
    surface = Color(0xFF1F1A15),
    onSurface = Color(0xFFEFE7DC),
    surfaceVariant = Color(0xFF332B23),
    onSurfaceVariant = Color(0xFFBFAF9E),
    outline = Color(0xFF473B30),
    outlineVariant = Color(0xFF332B23),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/**
 * 可选的整体界面主题。
 *
 * [AUTO] 是默认值，也是老用户升级后的去处：跟随系统深浅，在「雾霭 / 墨曜」之间切换。
 *
 * [lightPalette] / [darkPalette] 各给一份是为了让 [AUTO] 复用同一批定义；
 * 锁定明 / 暗的主题由 [forceDark] 决定实际取哪一套，另一套不会被读到。
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val subtitle: String,
    val lightPalette: ThemePalette,
    val darkPalette: ThemePalette,
    /** null = 跟随系统；true / false = 锁定深 / 浅。 */
    val forceDark: Boolean?
) {
    AUTO(
        id = "auto",
        label = "跟随系统",
        subtitle = "雾霭 / 墨曜自动切换",
        lightPalette = PaletteMist,
        darkPalette = PaletteInk,
        forceDark = null
    ),
    XUAN(
        id = "xuan",
        label = "宣纸",
        subtitle = "暖砂米 · 白天",
        lightPalette = PaletteXuan,
        darkPalette = PaletteXuan,
        forceDark = false
    ),
    MIST(
        id = "mist",
        label = "雾霭",
        subtitle = "冷石板 · 白天",
        lightPalette = PaletteMist,
        darkPalette = PaletteMist,
        forceDark = false
    ),
    CELADON(
        id = "celadon",
        label = "青瓷",
        subtitle = "灰釉绿 · 白天",
        lightPalette = PaletteCeladon,
        darkPalette = PaletteCeladon,
        forceDark = false
    ),
    INK(
        id = "ink",
        label = "墨曜",
        subtitle = "中性深灰 · 夜间",
        lightPalette = PaletteInk,
        darkPalette = PaletteInk,
        forceDark = true
    ),
    MIDNIGHT(
        id = "midnight",
        label = "午夜",
        subtitle = "深靛蓝 · 夜间",
        lightPalette = PaletteMidnight,
        darkPalette = PaletteMidnight,
        forceDark = true
    ),
    EMBER(
        id = "ember",
        label = "暖夜",
        subtitle = "炭褐暖调 · 夜间",
        lightPalette = PaletteEmber,
        darkPalette = PaletteEmber,
        forceDark = true
    );

    companion object {
        /** 选择列表顺序：跟随系统 → 白天组 → 夜间组。 */
        val pickList: List<AppTheme> =
            listOf(AUTO) + entries.filter { it.forceDark == false } + entries.filter { it.forceDark == true }

        /** 未知 id（例如写坏的数据）一律回落到 [AUTO]，不让界面崩在半路。 */
        fun of(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
