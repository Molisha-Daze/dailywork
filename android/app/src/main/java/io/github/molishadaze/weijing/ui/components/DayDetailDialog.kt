package io.github.molishadaze.weijing.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.util.Haptics
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DETAIL_PURPLE = Color(0xFF8B5CF6)

/**
 * 「完成」的强调色，取当前主题主色（理由同 CalendarMonthView.calAccent：
 * 写死的 #10B981 与计划色板第一支同值，会和「某个计划恰好是翡翠绿」混淆）。
 */
@Composable
private fun detailAccent(): Color = MaterialTheme.colorScheme.primary

private val DETAIL_WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 某一天的完整详情弹窗，对应网页版 DayDetailModal.tsx。
 *
 * 网页版是「双击日期格 / 点完整详情 / 点进入当天详情」三种方式打开它；
 * 安卓端没有双击，所以把入口做成清单头部的「完整详情」与图例右侧的「进入当天详情」。
 */
@Composable
fun DayDetailDialog(
    date: String,
    habits: List<Habit>,
    checkIns: List<CheckIn>,
    onDismiss: () -> Unit,
    onToggleCheckIn: (habitId: Long, date: String) -> Unit,
    onIncrement: (habitId: Long, date: String) -> Unit,
    onDecrement: (habitId: Long, date: String) -> Unit,
    onToggleSubTask: (habitId: Long, date: String, subTaskId: String) -> Unit,
    onAttachPhoto: (habitId: Long, date: String, uri: android.net.Uri) -> Unit,
    onRemovePhoto: (habitId: Long, date: String) -> Unit,
    onViewPhoto: (photoPath: String) -> Unit
) {
    // 逐项记下「当前正在给哪个习惯选照片」，否则 picker 回调拿不到上下文。
    var photoTargetHabitId by remember { mutableLongStateOf(0L) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && photoTargetHabitId != 0L) {
            onAttachPhoto(photoTargetHabitId, date, uri)
        }
    }

    val checkInMap = remember(checkIns, date) {
        checkIns.filter { it.date == date }.associateBy { it.habitId }
    }
    val completedCount = habits.count { habit ->
        HabitSchedule.isCompleted(habit, checkInMap[habit.id])
    }

    val parsedDate = remember(date) {
        runCatching { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    val weekday = parsedDate?.let { DETAIL_WEEKDAYS[it.dayOfWeek.value - 1] } ?: ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildString {
                                parsedDate?.let {
                                    append(it.year).append("年")
                                    append(it.monthValue).append("月")
                                    append(it.dayOfMonth).append("日")
                                } ?: append(date)
                                if (weekday.isNotBlank()) append(" · ").append(weekday)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "当天计划完成进度：$completedCount / ${habits.size} 项",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (habits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当天没有安排任何计划项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        habits.forEach { habit ->
                            DayDetailPlanCard(
                                habit = habit,
                                checkIn = checkInMap[habit.id],
                                onToggle = { onToggleCheckIn(habit.id, date) },
                                onIncrement = { onIncrement(habit.id, date) },
                                onDecrement = { onDecrement(habit.id, date) },
                                onToggleSubTask = { onToggleSubTask(habit.id, date, it) },
                                onPickPhoto = {
                                    photoTargetHabitId = habit.id
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemovePhoto = { onRemovePhoto(habit.id, date) },
                                onViewPhoto = onViewPhoto
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailPlanCard(
    habit: Habit,
    checkIn: CheckIn?,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val habitColor = parseColorSafe(habit.colorHex, detailAccent())
    val isDone = HabitSchedule.isCompleted(habit, checkIn)
    val count = HabitSchedule.currentCount(checkIn)
    val target = HabitSchedule.effectiveTarget(habit)
    val hasSubTasks = habit.subTaskList.isNotEmpty()
    val completedIds = checkIn?.completedSubTaskIdList.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDone) detailAccent().copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface)
            .then(
                if (isDone) Modifier.border(1.dp, detailAccent().copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        // 历史详情里补打卡同样是「完成」，给强振；取消只给轻振。
                        Haptics.play(if (isDone) Haptics.Level.LIGHT else Haptics.Level.STRONG)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Outlined.CheckCircle else UiIcons.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isDone) detailAccent() else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(habitColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(habit.iconName),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(scheduleLabel(habit))
                        habit.reminderTime?.let { append(" · 提醒: ").append(it) }
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (habit.isCounter) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = count > 0) {
                                Haptics.play(Haptics.Level.LIGHT)
                                onDecrement()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            UiIcons.Remove,
                            contentDescription = "减 1",
                            tint = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "$count/$target ${habit.unit}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) detailAccent() else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(detailAccent())
                            .clickable {
                                val justReachedTarget = count < target && count + 1 >= target
                                Haptics.play(
                                    if (justReachedTarget) Haptics.Level.STRONG else Haptics.Level.LIGHT
                                )
                                onIncrement()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(text = "+1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (hasSubTasks) {
            Spacer(modifier = Modifier.height(9.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DETAIL_PURPLE.copy(alpha = 0.08f))
                    .border(1.dp, DETAIL_PURPLE.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        UiIcons.FormatListBulleted,
                        contentDescription = null,
                        tint = DETAIL_PURPLE,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "子任务 (${completedIds.size}/${habit.subTaskList.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DETAIL_PURPLE,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "点击消除",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    habit.subTaskList.forEach { task ->
                        val done = completedIds.contains(task.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (done) detailAccent().copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    // 勾满最后一项才强振，中途每勾一项给轻振。
                                    val completesWholePlan =
                                        !done && completedIds.size + 1 >= habit.subTaskList.size
                                    Haptics.play(
                                        if (completesWholePlan) Haptics.Level.STRONG
                                        else Haptics.Level.LIGHT
                                    )
                                    onToggleSubTask(task.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (done) detailAccent() else Color.Transparent)
                                    .then(
                                        if (done) Modifier
                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = task.title,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Text(
                                text = if (done) "已完成" else "待完成",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (done) detailAccent() else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        if (!hasSubTasks && !habit.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Icon(
                    UiIcons.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = habit.description!!,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 照片凭证（网页版：缩略图 + 查看大图 + 移除 + 更换）
        val photoPath = checkIn?.photoPath
        if (isDone || photoPath != null) {
            Spacer(modifier = Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (photoPath != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onViewPhoto(photoPath) }
                    ) {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = "打卡凭证",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "拍照凭证已留存",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemovePhoto, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除照片",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "可附带一张照片作为完成凭证",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPickPhoto)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            UiIcons.PhotoCamera,
                            contentDescription = null,
                            tint = habitColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (photoPath == null) "拍照留证" else "更换凭证",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
