package io.github.molishadaze.weijing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import io.github.molishadaze.weijing.ui.screens.MainScreen
import io.github.molishadaze.weijing.ui.theme.AppTheme
import io.github.molishadaze.weijing.ui.theme.HabitTrackerTheme
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.viewmodel.HabitViewModel
import io.github.molishadaze.weijing.viewmodel.HabitViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HabitApplication
        val viewModel = ViewModelProvider(
            this,
            HabitViewModelFactory(app.repository)
        )[HabitViewModel::class.java]

        setContent {
            val settings = remember { AppSettings(this) }
            val fontScale by settings.fontScale.collectAsState(
                initial = AppSettings.DEFAULT_FONT_SCALE
            )

            val themeId by settings.themeId.collectAsState(initial = settings.currentThemeId())

            // 用 LocalDensity 的 fontScale 实现全局字号：
            // 所有以 sp 标注的文字都会按此缩放，dp 尺寸不受影响，
            // 因此不会出现布局被撑破但文字没变的情况。
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(fontScale, baseDensity) {
                Density(baseDensity.density, fontScale)
            }

            // 主题初值走 currentThemeId() 而不是等到 Flow 发射：
            // 晚了会在启动时闪一下别的配色。
            HabitTrackerTheme(appTheme = AppTheme.of(themeId)) {
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    MainScreen(viewModel = viewModel, settings = settings)
                }
            }
        }
    }
}
