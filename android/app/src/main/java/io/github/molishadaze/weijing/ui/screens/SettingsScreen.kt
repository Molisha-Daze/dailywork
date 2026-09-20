package io.github.molishadaze.weijing.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.molishadaze.weijing.ui.components.UiIcons
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.util.Haptics
import io.github.molishadaze.weijing.viewmodel.HabitViewModel
import kotlinx.coroutines.launch

/**
 * 管理中心：上半是四个功能金刚区，下半是计划清单（原「习惯管理」页）。
 *
 * 这个布局刻意与网页版 App.tsx 的 manage tab 保持一致：
 * 1x4 功能图标在上，计划清单在下——如此两边导航结构即可一一对上。
 */
@Composable
fun SettingsScreen(
    viewModel: HabitViewModel,
    settings: AppSettings,
    currentFontScale: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activePanel by remember { mutableStateOf<Panel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.exportBackupJson()
                writeTextToUri(context, uri, json)
            }.onSuccess {
                message = "备份已导出"
            }.onFailure {
                message = "导出失败：${it.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = readTextFromUri(context, uri)
                viewModel.importBackupJson(json)
            }.onSuccess {
                message = "数据已恢复"
            }.onFailure {
                message = "恢复失败：${it.message}"
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ManagementPanelGrid(
                currentFontScale = currentFontScale,
                versionName = appVersionName(context),
                onOpen = { activePanel = it }
            )
        }

        item {
            HapticFeedbackCard(settings = settings)
        }

        item {
            HabitManageSection(viewModel = viewModel)
        }
    }

    when (activePanel) {
        Panel.FONT_SIZE -> FontSizeDialog(
            currentFontScale = currentFontScale,
            onSelect = { settings.setFontScale(it) },
            onDismiss = { activePanel = null }
        )
        Panel.ABOUT -> AboutDialog(
            context = context,
            onDismiss = { activePanel = null }
        )
        Panel.BACKUP -> BackupDialog(
            onExport = {
                exportLauncher.launch(
                    "habit-tracker-backup-${System.currentTimeMillis()}.json"
                )
            },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            onDismiss = { activePanel = null }
        )
        Panel.NOTIFICATION -> NotificationDialog(
            context = context,
            onDismiss = { activePanel = null }
        )
        null -> Unit
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }
        )
    }
}

private enum class Panel {
    FONT_SIZE, ABOUT, BACKUP, NOTIFICATION
}

/** 1x4 金刚区：与管理中心网页版一一对应的四个入口。 */
@Composable
private fun ManagementPanelGrid(
    currentFontScale: Float,
    versionName: String,
    onOpen: (Panel) -> Unit
) {
    val currentSize = AppSettings.sizeOf(currentFontScale)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridEntry(
                icon = UiIcons.Tune,
                label = "设置字号",
                value = currentSize.label,
                tint = Color(0xFF10B981),
                onClick = { onOpen(Panel.FONT_SIZE) }
            )
            GridEntry(
                icon = Icons.Default.Info,
                label = "关于",
                value = "v$versionName",
                tint = Color(0xFF14B8A6),
                onClick = { onOpen(Panel.ABOUT) }
            )
            GridEntry(
                icon = UiIcons.Download,
                label = "数据备份",
                value = "导出恢复",
                tint = Color(0xFF3B82F6),
                onClick = { onOpen(Panel.BACKUP) }
            )
            GridEntry(
                icon = UiIcons.Alarm,
                label = "提醒设置",
                value = "声音通知",
                tint = Color(0xFFF59E0B),
                onClick = { onOpen(Panel.NOTIFICATION) }
            )
        }
    }
}

/**
 * 触觉反馈开关。
 *
 * 刻意不做进金刚区：那块是 1x4、与网页版一一对应的四个入口，
 * 塞第五个会让两端的导航结构再次错位。所以单独占一张卡片夹在中间。
 */
@Composable
private fun HapticFeedbackCard(settings: AppSettings) {
    val enabled by settings.hapticEnabled.collectAsState(initial = AppSettings.DEFAULT_HAPTIC_ENABLED)
    val supported = Haptics.isSupported()
    val systemDisabled = Haptics.isSystemHapticsDisabled()

    // 三种「没反应」的原因必须区分开，否则用户会以为开关失灵是我们的 bug：
    // 没马达是硬件限制，系统关掉是系统设置问题，只有最后一种才轮到我们背锅。
    val subtitle = when {
        !supported -> "当前设备没有振动马达"
        systemDisabled -> "系统「触觉反馈」已关闭，需到系统设置里开启"
        enabled -> "打卡完成、计数达标等操作会有振动"
        else -> "已关闭，所有操作都不会振动"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = UiIcons.Vibration,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "触觉反馈",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = enabled && supported,
                enabled = supported,
                onCheckedChange = { next ->
                    settings.setHapticEnabled(next)
                    // 打开时立刻振一下，让用户当场知道这个强度是什么感觉。
                    // apply() 会同步刷新内存并回调监听器，所以这里读到的必然是刚写入的新值，
                    // 不会因为落盘是异步的而振不出来。
                    if (next) Haptics.play(Haptics.Level.MEDIUM)
                }
            )
        }
    }
}

@Composable
private fun GridEntry(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(14.dp),
            color = tint.copy(alpha = 0.14f),
            modifier = Modifier.size(40.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FontSizeDialog(
    currentFontScale: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val currentSize = AppSettings.sizeOf(currentFontScale)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置字号") },
        text = {
            Column {
                AppSettings.FontSize.entries.forEach { size ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSize == size,
                            onClick = { onSelect(size.scale) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${size.label}字号",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = size.percent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun AboutDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 版本号小胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "版本号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v${appVersionName(context)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "看到更好的自己",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 花体英文：Compose 无内置手写字体，用系统 Cursive 字族 +
                // 轻微字距来逼近网页版 Dancing Script 的手写观感。
                Text(
                    text = "To see a better version of yourself",
                    fontFamily = FontFamily.Cursive,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

@Composable
private fun BackupDialog(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据备份") },
        text = {
            Text(
                "导出全部习惯、打卡记录与独立计数器为 JSON 文件。\n\n" +
                    "注意：从文件恢复会**覆盖当前全部数据**，且不可逆。"
            )
        },
        confirmButton = { TextButton(onClick = { onExport(); onDismiss() }) { Text("导出备份") } },
        dismissButton = { TextButton(onClick = { onImport(); onDismiss() }) { Text("从文件恢复") } }
    )
}

@Composable
private fun NotificationDialog(context: Context, onDismiss: () -> Unit) {
    val exactAllowed = canScheduleExactAlarms(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒设置") },
        text = {
            Column {
                Text(
                    text = "通知权限：${if (notificationsEnabled(context)) "已开启" else "未开启"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text(
                        text = if (exactAllowed) "精确定时：已授予" else "精确定时：未授予，提醒可能延迟数分钟",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "这两项由系统管控，需跳转到系统的通知设置页授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.startActivity(notificationSettingsIntent(context))
                    onDismiss()
                }
            ) { Text("去系统设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun appVersionName(context: Context): String = try {
    @Suppress("DEPRECATION")
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "未知"
} catch (e: Exception) {
    "未知"
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun canScheduleExactAlarms(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<android.app.AlarmManager>()?.canScheduleExactAlarms() ?: false
    } else {
        true
    }

private fun notificationSettingsIntent(context: Context): Intent =
    Intent().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
    }

private fun writeTextToUri(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("无法写入目标文件")
}

private fun readTextFromUri(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: throw IllegalStateException("无法读取所选文件")
