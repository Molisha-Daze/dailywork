package io.github.molishadaze.weijing.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.ui.components.PRESET_COLORS
import io.github.molishadaze.weijing.util.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题配色的回归测试。
 *
 * 这里守的是一条**设计约束**，不是代码行为：
 * App 的主色必须和「计划 / 计数器」的自定义色板拉开距离。
 *
 * 一旦两者接近，界面上就会分不清「这抹颜色是完成状态」还是「这个计划恰好选了这个颜色」，
 * 观感非常像渲染出错（改主题之前，完成态的 #10B981 与习惯色板第一支就是同一个值）。
 * 这种问题靠肉眼看截图很难在改色的当次发现，所以交给 ΔE 来判：
 * CIELAB ΔE > 25 在色彩学上即「明显不同的颜色」，本 App 实测最小 31.1。
 */
class AppThemeTest {

    /**
     * 十六进制转 [Color]。
     *
     * 不能用 android.graphics.Color.parseColor —— JVM 单测里那个类没有被 mock，
     * 一调用就抛 "not mocked"（踩过一次，7 条用例全红在这一行上）。
     */
    private fun colorOf(hex: String): Color {
        val rgb = java.lang.Long.parseLong(hex.removePrefix("#"), 16).toInt()
        return Color(0xFF000000.toInt() or rgb)
    }

    /** 用户可自选的数据色：计划图标主题色 + 独立计数器主题色。 */
    private val userPickedColors: List<Pair<String, Color>> =
        PRESET_COLORS.map { (hex, name) -> "计划色 $name $hex" to colorOf(hex) } +
            StandaloneCounter.PRESET_COLORS.map { hex -> "计数器色 $hex" to colorOf(hex) }

    // ---------- 色彩计算（全部本地实现，不依赖任何色彩库） ----------

    private fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else java.lang.Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun lab(color: Color): DoubleArray {
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        fun f(t: Double): Double =
            if (t > 0.008856) java.lang.Math.pow(t, 1.0 / 3.0) else (7.787 * t + 16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** CIE76 色差。够用：这里要判的是「是不是明显不同」，不是「差多少才算好看」。 */
    private fun deltaE(a: Color, b: Color): Double {
        val c1 = lab(a)
        val c2 = lab(b)
        val dl = c1[0] - c2[0]
        val da = c1[1] - c2[1]
        val db = c1[2] - c2[2]
        return java.lang.Math.sqrt(dl * dl + da * da + db * db)
    }

    /** 每个主题实际会被用到的配色（跟随系统两套都算）。 */
    private fun palettesOf(theme: AppTheme): List<ThemePalette> = when (theme.forceDark) {
        null -> listOf(theme.lightPalette, theme.darkPalette)
        true -> listOf(theme.darkPalette)
        false -> listOf(theme.lightPalette)
    }

    // ---------- 约束 1：主色不能和用户自选色混淆 ----------

    @Test
    fun `主题主色与计划计数器色板明显不同`() {
        AppTheme.entries.forEach { theme ->
            palettesOf(theme).forEach { palette ->
                userPickedColors.forEach { (label, picked) ->
                    val d = deltaE(palette.primary, picked)
                    assertTrue(
                        "${theme.label}(${if (palette.isDark) "暗" else "亮"}) 的主色与 $label 太接近：ΔE=${
                            String.format("%.1f", d)
                        }（要求 >= 25）",
                        d >= 25.0
                    )
                }
            }
        }
    }

    /** 次要强调色同样要躲开：它出现在「已达上限」这类提示上，撞色一样会误读。 */
    @Test
    fun `主题次色与色板不混淆`() {
        AppTheme.entries.forEach { theme ->
            palettesOf(theme).forEach { palette ->
                val worst = userPickedColors.minOf { (_, picked) -> deltaE(palette.secondary, picked) }
                assertTrue(
                    "${theme.label} 的 secondary 与色板太接近：ΔE=${String.format("%.1f", worst)}",
                    worst >= 20.0
                )
            }
        }
    }

    // ---------- 约束 2：可读性 ----------

    @Test
    fun `正文与背景的对比度达到 WCAG AA`() {
        AppTheme.entries.forEach { theme ->
            palettesOf(theme).forEach { palette ->
                val pairs = listOf(
                    "primary/onPrimary" to (palette.primary to palette.onPrimary),
                    "surface/onSurface" to (palette.surface to palette.onSurface),
                    "surface/onSurfaceVariant" to (palette.surface to palette.onSurfaceVariant),
                    "background/onBackground" to (palette.background to palette.onBackground),
                    "primaryContainer/onPrimaryContainer" to (palette.primaryContainer to palette.onPrimaryContainer)
                )
                pairs.forEach { (label, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue(
                        "${theme.label} 的 $label 对比度只有 ${String.format("%.2f", ratio)}（要求 >= 4.5）",
                        ratio >= 4.5
                    )
                }
            }
        }
    }

    /** 主色还要能从背景里「跳出来」，否则按钮、选中态会看不清。 */
    @Test
    fun `主色在背景上足够显眼`() {
        AppTheme.entries.forEach { theme ->
            palettesOf(theme).forEach { palette ->
                val ratio = contrast(palette.surface, palette.primary)
                assertTrue(
                    "${theme.label} 的主色在卡片上不够显眼：${String.format("%.2f", ratio)}（要求 >= 3.0）",
                    ratio >= 3.0
                )
            }
        }
    }

    // ---------- 约束 3：枚举与存储不要各说各话 ----------

    @Test
    fun `默认主题 id 指向 AUTO`() {
        assertEquals(
            "AppSettings.DEFAULT_THEME_ID 与 AppTheme.AUTO.id 不一致，新装用户会落到未知 id",
            AppTheme.AUTO.id,
            AppSettings.DEFAULT_THEME_ID
        )
        assertEquals(AppTheme.AUTO, AppTheme.of(AppSettings.DEFAULT_THEME_ID))
    }

    @Test
    fun `未知主题 id 回落到 AUTO 而不是崩溃`() {
        assertEquals(AppTheme.AUTO, AppTheme.of(null))
        assertEquals(AppTheme.AUTO, AppTheme.of(""))
        assertEquals(AppTheme.AUTO, AppTheme.of("__no_such_theme__"))
    }

    @Test
    fun `选择列表覆盖全部主题且不重复`() {
        assertEquals(AppTheme.entries.size, AppTheme.pickList.distinct().size)
        assertTrue("AUTO 必须排在最前", AppTheme.pickList.first() == AppTheme.AUTO)
        // 三白天 + 三夜间
        assertEquals(3, AppTheme.entries.count { it.forceDark == false })
        assertEquals(3, AppTheme.entries.count { it.forceDark == true })
    }
}
