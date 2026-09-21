package io.github.molishadaze.weijing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 应用主题。
 *
 * 颜色来源只有一个：[AppTheme]。M3 的动态取色（Material You）故意不支持 ——
 * 动态取色会按壁纸算出任意主色，无法保证「主色不和用户自定义的计划色撞在一起」，
 * 而后者是这个 App 的硬要求（详见 AppTheme.kt 里关于 CIELAB ΔE 的说明）。
 */
@Composable
fun HabitTrackerTheme(
    appTheme: AppTheme = AppTheme.AUTO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (appTheme.forceDark ?: darkTheme) appTheme.darkPalette else appTheme.lightPalette

    MaterialTheme(
        colorScheme = palette.toColorScheme(),
        typography = Typography,
        content = content
    )
}
