package io.github.molishadaze.weijing.ui.components

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.model.SubTask
import io.github.molishadaze.weijing.notification.NotificationHelper
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

// 图标候选表已上移到 HabitIcons.kt 的 PRESET_ICONS —— 与图标定义放在一起，
// 避免「加了图标却忘了加进候选表」这类两处不一致。

val PRESET_COLORS = listOf(
    "#10B981" to "翡翠绿",
    "#3B82F6" to "海蓝色",
    "#8B5CF6" to "幻紫色",
    "#F59E0B" to "琥珀橙",
    "#EC4899" to "玫瑰粉",
    "#14B8A6" to "青碧色"
)

/** 大计划的一键示例：与网页版「填入力量训练示例」一致。 */
private val FITNESS_TEMPLATE = listOf(
    "深蹲 4 组 x 10 次",
    "卧推 4 组 x 10 次",
    "引体向上 3 组 x 8 次",
    "静态拉伸 10 分钟"
)

private val PLAN_PURPLE = Color(0xFF8B5CF6)

private val WEEKDAY_LABELS = listOf(
    1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日"
)

private val RECURRENCE_OPTIONS = listOf(
    HabitSchedule.TYPE_DAILY to "每天",
    HabitSchedule.TYPE_WEEKLY to "每周",
    HabitSchedule.TYPE_MONTHLY to "每月",
    HabitSchedule.TYPE_INTERVAL to "每N天",
    HabitSchedule.TYPE_NONE to "单次"
)

/**
 * 新建 / 编辑计划对话框，字段顺序与分区刻意对齐网页版 AddEditHabitModal.tsx：
 *
 * 计划模式（常规 / 大计划）→ 标题 → 子任务清单（大计划）→ 内容描述 →
 * 计数器（仅常规）→ 重复方式 → 生效区间 → 提醒时间 → 图标与主题色。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditHabitDialog(
    initialHabit: Habit? = null,
    defaultStartDate: String? = null,
    defaultRecurrenceType: String? = null,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    val context = LocalContext.current

    // 计划模式：true = 大计划（细化任务消除），false = 常规单项计划。
    // 网页版把它做成顶部二选一的分段控件，这里同样放在最上面，
    // 否则用户（以及上一个版本的我）根本发现不了「大计划」在哪。
    var isParentPlan by remember {
        mutableStateOf(initialHabit?.isParentPlan ?: false)
    }

    var name by remember { mutableStateOf(initialHabit?.name ?: "") }
    var description by remember { mutableStateOf(initialHabit?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initialHabit?.iconName ?: "Star") }
    var selectedColor by remember { mutableStateOf(initialHabit?.colorHex ?: "#10B981") }
    var reminderTime by remember { mutableStateOf(initialHabit?.reminderTime) }

    // 排期
    var recurrenceType by remember {
        mutableStateOf(
            initialHabit?.recurrenceType
                ?: defaultRecurrenceType
                ?: HabitSchedule.DEFAULT_TYPE
        )
    }
    // 注意：老数据的 startDate 是空串，语义为「不限起始」。
    // 这里不能擅自填成今天，否则该习惯在开始日期之前的历史排期会全部消失。
    var startDate by remember {
        mutableStateOf(
            initialHabit?.startDate?.takeIf { it.isNotBlank() }
                ?: defaultStartDate
                ?: DateUtils.today()
        )
    }
    var endDate by remember { mutableStateOf(initialHabit?.endDate?.takeIf { !it.isNullOrBlank() }) }
    var weeklyDays by remember { mutableStateOf(HabitSchedule.parseWeeklyDays(initialHabit?.weeklyDays)) }
    var monthlyDaysText by remember { mutableStateOf(initialHabit?.monthlyDays ?: "") }
    var intervalText by remember { mutableStateOf((initialHabit?.intervalDays ?: 2).toString()) }

    // 计数器（仅常规计划可用）
    var isCounter by remember { mutableStateOf(initialHabit?.isCounter ?: false) }
    var targetText by remember { mutableStateOf((initialHabit?.targetCount ?: 1).toString()) }
    var unit by remember { mutableStateOf(initialHabit?.unit ?: "次") }

    // 细化小计划（大计划）。id 在添加时生成，编辑期保持不变，
    // 这样改名不会丢掉已经勾选过的进度。
    var subTasks by remember { mutableStateOf(initialHabit?.subTaskList ?: emptyList()) }
    var subTaskDraft by remember { mutableStateOf("") }

    var errorText by remember { mutableStateOf<String?>(null) }
    var isNameError by remember { mutableStateOf(false) }

    // 精确定时能力：Android 12+ 上用户可以在系统设置里关掉，关掉后提醒会降级为非精确
    var exactAlarmAvailable by remember { mutableStateOf(NotificationHelper.canScheduleExactAlarms(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 从系统精确定时设置页返回后才刷新，避免「系统未允许精确提醒」的提示滞留
        exactAlarmAvailable = NotificationHelper.canScheduleExactAlarms(context)
    }

    // 兜底：用户通过其他路径切走再回来（例如从最近任务切回）时也刷新一次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmAvailable = NotificationHelper.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun addSubTask() {
        val title = subTaskDraft.trim()
        if (title.isNotBlank()) {
            // id 在此刻生成：编辑已有习惯时，老子任务的 id 不动，
            // 因此改名不会让已勾选的进度丢失。
            subTasks = subTasks + SubTask(id = SubTask.newId(), title = title)
            subTaskDraft = ""
        }
    }

    fun commit() {
        when {
            name.isBlank() -> {
                isNameError = true
                errorText = "请输入计划名称"
            }
            recurrenceType == HabitSchedule.TYPE_WEEKLY && weeklyDays.isEmpty() -> {
                errorText = "请选择至少一天"
            }
            recurrenceType == HabitSchedule.TYPE_MONTHLY &&
                HabitSchedule.parseMonthlyDays(monthlyDaysText).isEmpty() -> {
                errorText = "请输入 1-31 之间的日期，多个用逗号分隔，如 1,15,28"
            }
            recurrenceType == HabitSchedule.TYPE_INTERVAL &&
                (intervalText.trim().toIntOrNull() ?: 0) < 1 -> {
                errorText = "间隔天数至少为 1"
            }
            recurrenceType == HabitSchedule.TYPE_NONE && startDate.isBlank() -> {
                errorText = "单次计划请选择具体日期"
            }
            // startDate 为空表示「不限起始」，此时不做上下界比较
            endDate != null && startDate.isNotBlank() && endDate!! < startDate -> {
                errorText = "结束日期不能早于开始日期"
            }
            !isParentPlan && isCounter && (targetText.trim().toIntOrNull() ?: 0) < 1 -> {
                errorText = "目标次数至少为 1"
            }
            else -> {
                val base = initialHabit ?: Habit(name = "")
                onSave(
                    base.copy(
                        name = name.trim(),
                        description = description.trim().takeIf { it.isNotBlank() },
                        iconName = selectedIcon,
                        colorHex = selectedColor,
                        reminderTime = reminderTime,
                        startDate = startDate,
                        endDate = endDate,
                        recurrenceType = recurrenceType,
                        weeklyDays = if (recurrenceType == HabitSchedule.TYPE_WEEKLY)
                            HabitSchedule.formatWeeklyDays(weeklyDays) else null,
                        monthlyDays = if (recurrenceType == HabitSchedule.TYPE_MONTHLY)
                            HabitSchedule.formatMonthlyDays(HabitSchedule.parseMonthlyDays(monthlyDaysText))
                        else null,
                        intervalDays = if (recurrenceType == HabitSchedule.TYPE_INTERVAL)
                            intervalText.trim().toIntOrNull() ?: 1 else 1,
                        // 大计划与计数器互斥（网页版同样如此）：
                        // 大计划的完成判定是「子任务全部勾满」，再加个次数目标会互相打架。
                        isCounter = if (isParentPlan) false else isCounter,
                        targetCount = if (!isParentPlan && isCounter) targetText.trim().toIntOrNull() ?: 1 else 1,
                        unit = if (!isParentPlan && isCounter) unit.trim().ifBlank { "次" } else "次",
                        // 有子任务即视为大计划；空列表一律存 null，避免库里塞一堆 "[]"
                        isParentPlan = subTasks.isNotEmpty(),
                        subTasks = subTasks.takeIf { it.isNotEmpty() }
                    )
                )
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialHabit == null) "新增计划与习惯" else "编辑计划 / 习惯",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ---------- 计划模式：常规 vs 大计划 ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    PlanModeTab(
                        label = "常规单项计划",
                        selected = !isParentPlan,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { isParentPlan = false }
                    )
                    PlanModeTab(
                        label = "新建大计划",
                        selected = isParentPlan,
                        activeColor = PLAN_PURPLE,
                        modifier = Modifier.weight(1f),
                        onClick = { isParentPlan = true }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------- 标题 ----------
                SectionLabel(if (isParentPlan) "大计划标题 *" else "计划标题 *")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) isNameError = false
                    },
                    placeholder = {
                        Text(
                            if (isParentPlan) "例如：力量与体能训练、清晨全套习惯"
                            else "例如：今日喝水打卡、晨跑 3 公里",
                            fontSize = 12.sp
                        )
                    },
                    isError = isNameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ---------- 大计划：子任务清单 ----------
                if (isParentPlan) {
                    SubTaskEditor(
                        subTasks = subTasks,
                        draft = subTaskDraft,
                        onDraftChange = { subTaskDraft = it },
                        onAdd = { addSubTask() },
                        onRemove = { id -> subTasks = subTasks.filterNot { it.id == id } },
                        onApplyTemplate = {
                            subTasks = FITNESS_TEMPLATE.map { SubTask(id = SubTask.newId(), title = it) }
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ---------- 内容描述 ----------
                SectionLabel("内容描述（多行，如训练动作 / 备忘）")
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text("例如：\n深蹲 4 组 x 10 次\n卧推 4 组 x 10 次", fontSize = 12.sp)
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ---------- 计数器（仅常规计划） ----------
                if (!isParentPlan) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "开启目标计数器",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "例如：今日喝水 3 杯，点一下加 1，点满 3 次即完成",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = isCounter, onCheckedChange = { isCounter = it })
                        }

                        if (isCounter) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = targetText,
                                    onValueChange = { targetText = it },
                                    label = { Text("目标达成数值") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    label = { Text("计数单位") },
                                    placeholder = { Text("杯 / 组 / 次") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ---------- 重复方式 ----------
                SectionLabel("重复方式")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RECURRENCE_OPTIONS.forEach { (type, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = recurrenceType == type,
                            onClick = { recurrenceType = type },
                            label = { Text(label) },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (recurrenceType) {
                    HabitSchedule.TYPE_WEEKLY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WEEKDAY_LABELS.forEach { (value, label) ->
                                val picked = weeklyDays.contains(value)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (picked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            weeklyDays = if (picked) weeklyDays - value else weeklyDays + value
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (picked) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HabitSchedule.TYPE_MONTHLY -> {
                        OutlinedTextField(
                            value = monthlyDaysText,
                            onValueChange = { monthlyDaysText = it },
                            label = { Text("每月哪几天，如 1,15,28") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    HabitSchedule.TYPE_INTERVAL -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("每几天一次") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    HabitSchedule.TYPE_NONE -> {
                        Text(
                            text = "单次计划只在开始日期当天出现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------- 生效区间 ----------
                SectionLabel("生效区间")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DatePickerButton(
                        value = startDate,
                        modifier = Modifier.weight(1f),
                        onValueChange = { startDate = it }
                    )
                    Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (endDate == null) {
                        OutlinedButton(
                            onClick = {
                                endDate = if (startDate.isBlank()) DateUtils.today() else startDate
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("长期有效", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        DatePickerButton(
                            value = endDate!!,
                            modifier = Modifier.weight(1f),
                            onValueChange = { endDate = it },
                            onClear = { endDate = null }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------- 提醒时间 ----------
                SectionLabel("提醒时间（可选，不设就不提醒）")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            val curHour = reminderTime?.split(":")?.getOrNull(0)?.toIntOrNull()
                                ?: cal.get(Calendar.HOUR_OF_DAY)
                            val curMinute = reminderTime?.split(":")?.getOrNull(1)?.toIntOrNull()
                                ?: cal.get(Calendar.MINUTE)

                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    reminderTime = String.format("%02d:%02d", hour, minute)
                                    // 在用户真正设置提醒的这一刻再请求通知权限，比冷启动时硬要更容易被接受
                                    requestNotificationPermissionIfNeeded()
                                },
                                curHour,
                                curMinute,
                                true
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(UiIcons.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = reminderTime?.let { "提醒时间: $it" } ?: "设置每日提醒时间")
                    }

                    if (reminderTime != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { reminderTime = null }) {
                            Text("清除提醒")
                        }
                    }
                }

                // 精确定时被系统关闭时明确告知，而不是静默降级
                if (reminderTime != null && !exactAlarmAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "系统未允许精确提醒，提醒可能延迟数分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                // 必须走 Activity Result：等用户从系统设置返回后再查询，
                                // 而不是 startActivity 之后立刻查询（那样查到的还是旧状态）
                                exactAlarmSettingsLauncher.launch(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        }) {
                            Text("去设置")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---------- 图标与主题色 ----------
                SectionLabel("选择图标")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_ICONS.forEach { (iconKey, vector) ->
                        val isSelected = selectedIcon == iconKey
                        val tintColor = parseColorSafe(selectedColor, MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) tintColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) tintColor else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = iconKey,
                                tint = if (isSelected) tintColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionLabel("选择主题色")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PRESET_COLORS.forEach { (colorHex, _) ->
                        val isSelected = selectedColor == colorHex
                        val parsedColor = parseColorSafe(colorHex, MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { commit() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = parseColorSafe(selectedColor, MaterialTheme.colorScheme.primary)
                )
            ) {
                Text("保存计划", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/** 顶部的「常规 / 大计划」二选一。 */
@Composable
private fun PlanModeTab(
    label: String,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 大计划的子任务编辑区（网页版同款紫色分区）。 */
@Composable
private fun SubTaskEditor(
    subTasks: List<SubTask>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onApplyTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PLAN_PURPLE.copy(alpha = 0.07f))
            .border(1.dp, PLAN_PURPLE.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                UiIcons.FormatListBulleted,
                contentDescription = null,
                tint = PLAN_PURPLE,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "子任务清单 (${subTasks.size} 项)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "主页点击展开，每做完一项点击消除",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PLAN_PURPLE.copy(alpha = 0.15f))
                    .clickable(onClick = onApplyTemplate)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "填入力量训练示例",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PLAN_PURPLE
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("输入小计划名称，如：深蹲 4 组 x 10 次", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PLAN_PURPLE)
                    .clickable(enabled = draft.isNotBlank(), onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加小计划",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (subTasks.isEmpty()) {
                Text(
                    text = "暂未添加小计划，请在上方输入后点击 +，或直接使用右上角示例",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                subTasks.forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, PLAN_PURPLE.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(PLAN_PURPLE.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PLAN_PURPLE
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.title,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { onRemove(task.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除小计划",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DatePickerButton(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val parsed = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    }
    // 空串表示「不限起始」，不能显示成空白或今天的日期
    val display = if (value.isBlank()) "不限" else value
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onValueChange(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day))
                    },
                    parsed.year,
                    parsed.monthValue - 1,
                    parsed.dayOfMonth
                ).show()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(display, style = MaterialTheme.typography.bodySmall)
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    UiIcons.Remove,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

internal fun parseColorSafe(colorHex: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        fallback
    }

/** 本 App 的系统通知设置页 Intent（供 StartActivityForResult 使用，便于返回后重新检查权限）。 */
internal fun notificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

/** 打开本 App 的系统通知设置页。 */
internal fun openAppNotificationSettings(context: Context) {
    context.startActivity(notificationSettingsIntent(context))
}
