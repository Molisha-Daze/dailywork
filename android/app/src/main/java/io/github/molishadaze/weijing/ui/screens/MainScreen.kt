package io.github.molishadaze.weijing.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.molishadaze.weijing.ui.components.UiIcons
import io.github.molishadaze.weijing.ui.components.notificationSettingsIntent
import io.github.molishadaze.weijing.ui.theme.AppTheme
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

/** 与网页版 counters tab 一致的紫色（tailwind purple-500）。 */
private val COUNTER_TAB_COLOR = Color(0xFF8B5CF6)

private fun isNotificationGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

@Composable
fun MainScreen(viewModel: HabitViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }

    // 字号偏好。变更会即时反映到这里，进而更新「管理中心」里RadioButton的选中态；
    // 真正作用到排版是在 MainActivity 的 LocalDensity 里。
    val fontScale by settings.fontScale.collectAsState(initial = AppSettings.DEFAULT_FONT_SCALE)
    val themeId by settings.themeId.collectAsState(initial = AppSettings.DEFAULT_THEME_ID)
    val appTheme = AppTheme.of(themeId)

    // Android 13+ (API 33+) Runtime Notification Permission Request。
    // 回调结果必须被消费：被拒绝时要明确告诉用户去哪里开，而不是静默失效。
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> notificationDenied = !granted }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { notificationDenied = !isNotificationGranted(context) }

    LaunchedEffect(Unit) {
        // 首次安装的设备插入教学示例。内部有持久标记，之后每次冷启动都无事发生。
        viewModel.ensureSampleSeeded(settings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = isNotificationGranted(context)
            notificationDenied = !granted
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 回到前台时结算过期的计数器周期（惰性归零，见 HabitRepository.rolloverCounterPeriods）。
    //
    // 挂在 MainScreen 而不是计数器页，是因为触发时机要对齐"用户重新看屏幕"这件事本身：
    // 手机在后台躺了一整夜、早上解锁直接进的是「今日打卡」页 ——
    // 那一刻周期就该翻篇了，而不是等他什么时候想起来切到计数器 tab。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.rolloverCounterPeriods()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "今日打卡") },
                    label = { Text("今日打卡", style = MaterialTheme.typography.labelMedium) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(UiIcons.CalendarMonth, contentDescription = "打卡日历") },
                    label = { Text("打卡日历", style = MaterialTheme.typography.labelMedium) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = {
                        Icon(
                            UiIcons.Tag,
                            contentDescription = "独立计数器",
                            // 网页版 counters tab 用紫色，这里跟着用紫色，
                            // 否则同一个 tab 在两个端上颜色不一样。
                            tint = if (selectedTabIndex == 2) COUNTER_TAB_COLOR
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            "独立计数器",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedTabIndex == 2) COUNTER_TAB_COLOR
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "管理中心") },
                    label = { Text("管理中心", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (notificationDenied) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "通知权限未开启，每日提醒不会弹出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { settingsLauncher.launch(notificationSettingsIntent(context)) }) {
                        Text("去设置")
                    }
                    IconButton(onClick = { notificationDenied = false }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "忽略",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> TodayScreen(viewModel = viewModel, settings = settings)
                    1 -> CalendarScreen(viewModel = viewModel)
                    2 -> StandaloneCountersScreen(viewModel = viewModel)
                    3 -> SettingsScreen(
                        viewModel = viewModel,
                        settings = settings,
                        currentFontScale = fontScale,
                        currentTheme = appTheme
                    )
                }
            }
        }
    }
}
