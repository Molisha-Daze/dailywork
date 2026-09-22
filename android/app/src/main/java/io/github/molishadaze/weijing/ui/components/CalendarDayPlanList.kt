package io.github.molishadaze.weijing.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.util.Feedback
import io.github.molishadaze.weijing.util.HabitSchedule
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
/** 下半部分：所选日期的计划清单（实时联动所选日期，可新建 / 查看完整详情）。 */
@Composable
internal fun SelectedDayPlanCard(
    selectedDate: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    checkInsByDateAndHabit: Map<String, CheckIn>,
    onToggleCheckIn: (habitId: Long, date: String) -> Unit,
    onIncrement: (habitId: Long, date: String) -> Unit,
    onDecrement: (habitId: Long, date: String) -> Unit,
    onToggleSubTask: (habitId: Long, date: String, subTaskId: String) -> Unit,
    onCreateForDate: (date: String) -> Unit,
    onOpenDayDetail: (date: String) -> Unit
) {
    val selectedDateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val selectedDayHabits = habits.filter { HabitSchedule.isScheduled(it, selectedDate) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SelectedDayHeaderRow(
                selectedDateStr = selectedDateStr,
                selectedDate = selectedDate,
                today = today,
                planCount = selectedDayHabits.size,
                onCreateForDate = onCreateForDate,
                onOpenDayDetail = onOpenDayDetail
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedDayHabits.isEmpty()) {
                SelectedDayEmptyHint(selectedDateStr = selectedDateStr)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    selectedDayHabits.forEach { habit ->
                        CalendarDayPlanRow(
                            habit = habit,
                            checkIn = checkInsByDateAndHabit["${selectedDateStr}_${habit.id}"],
                            onToggle = { onToggleCheckIn(habit.id, selectedDateStr) },
                            onIncrement = { onIncrement(habit.id, selectedDateStr) },
                            onDecrement = { onDecrement(habit.id, selectedDateStr) },
                            onToggleSubTask = { subTaskId ->
                                onToggleSubTask(habit.id, selectedDateStr, subTaskId)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 清单头部：标题 + 「N 项」胶囊 + 新建 / 完整详情。 */
@Composable
private fun SelectedDayHeaderRow(
    selectedDateStr: String,
    selectedDate: LocalDate,
    today: LocalDate,
    planCount: Int,
    onCreateForDate: (date: String) -> Unit,
    onOpenDayDetail: (date: String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedDate == today) "今日计划清单"
                    else "${shortDateLabel(selectedDate, today)} 计划清单",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false：标题只按内容占宽，不抢整行剩余空间。
                    // 双保险 —— 即使将来日期文案又变长，也只会自己打省略号，
                    // 不会再把右边的「N 项」胶囊挤没。
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$planCount 项",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 快速新建：把所选日期直接带进新建对话框，用来安排未来的某一天
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(calAccent().copy(alpha = 0.12f))
                .clickable { onCreateForDate(selectedDateStr) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = calAccent(),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "新建",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = calAccent()
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpenDayDetail(selectedDateStr) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "完整详情",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    UiIcons.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedDayEmptyHint(selectedDateStr: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$selectedDateStr 没有排期中的计划",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 日历下方「当天计划」的单行。与网页版一致：
 * 勾选框 + 图标 + 名称（完成加删除线）+ 排期/提醒 + 计数器或照片 + 子任务清单 + 描述。
 */
@Composable
private fun CalendarDayPlanRow(
    habit: Habit,
    checkIn: CheckIn?,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleSubTask: (String) -> Unit
) {
    val habitColor = parseColorSafe(habit.colorHex, calAccent())
    val isDone = HabitSchedule.isCompleted(habit, checkIn)
    val count = HabitSchedule.currentCount(checkIn)
    val target = HabitSchedule.effectiveTarget(habit)
    val hasSubTasks = habit.subTaskList.isNotEmpty()
    val completedIds = checkIn?.completedSubTaskIdList.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDone) calAccent().copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .then(
                if (isDone) Modifier.border(1.dp, calAccent().copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 勾选框：网页版用实心勾/空心圈，不是 Material 的 Checkbox
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable {
                        // 日历里补打卡同样是「完成」，给强振；取消只给轻振。
                        Feedback.fire(
                            if (isDone) Feedback.Event.HABIT_UNDONE else Feedback.Event.HABIT_DONE
                        )
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Outlined.CheckCircle else UiIcons.RadioButtonUnchecked,
                    contentDescription = if (isDone) "取消完成" else "标记完成",
                    tint = if (isDone) calAccent() else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(habitColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(habit.iconName),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scheduleLabel(habit),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    habit.reminderTime?.let {
                        Text(
                            text = " · 提醒: $it",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasSubTasks) {
                        Text(
                            text = " · ${completedIds.size}/${habit.subTaskList.size} 子任务",
                            fontSize = 10.sp,
                            color = CAL_PURPLE
                        )
                    }
                }
            }

            if (habit.isCounter) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = count > 0) {
                                Feedback.fire(Feedback.Event.COUNTER_BACK)
                                onDecrement()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            UiIcons.Remove,
                            contentDescription = "减 1",
                            tint = if (count > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "$count/$target ${habit.unit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) calAccent() else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(calAccent())
                            .clickable {
                                val justReachedTarget = count < target && count + 1 >= target
                                Feedback.fire(
                                    if (justReachedTarget) Feedback.Event.COUNTER_GOAL
                                    else Feedback.Event.COUNTER_STEP
                                )
                                onIncrement()
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(text = "+1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else {
                checkIn?.photoPath?.let { path ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "打卡凭证",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    UiIcons.PhotoCamera,
                    contentDescription = "照片凭证",
                    tint = habitColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 子任务清单（网页版紫色分区）
        if (hasSubTasks) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CAL_PURPLE.copy(alpha = 0.08f))
                    .border(1.dp, CAL_PURPLE.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        UiIcons.FormatListBulleted,
                        contentDescription = null,
                        tint = CAL_PURPLE,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "子任务 (${completedIds.size}/${habit.subTaskList.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CAL_PURPLE,
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
                                    if (done) calAccent().copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                                .then(
                                    if (done) Modifier.border(1.dp, calAccent().copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                )
                                .clickable {
                                    // 勾满最后一项才强振，中途每勾一项给轻振。
                                    val completesWholePlan =
                                        !done && completedIds.size + 1 >= habit.subTaskList.size
                                    Feedback.fire(
                                        if (completesWholePlan) Feedback.Event.PLAN_DONE
                                        else Feedback.Event.SUBTASK_TOGGLED
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
                                    .background(if (done) calAccent() else Color.Transparent)
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
                                text = if (done) "完成" else "点此完成",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (done) calAccent() else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // 描述（有子任务时不重复展示，与网页版一致）
        if (!hasSubTasks && !habit.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
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
    }
}

private val CAL_PURPLE = Color(0xFF8B5CF6)

/**
 * 清单头部的日期简称：当年只写「9月25日」，跨年才补上年份。
 *
 * 原来这里直接塞 ISO 的 "2026-09-25"，再拼上「计划清单」共 16 个字。
 * 首页标题行是 `Row { Text标题; 胶囊"N 项" }` 且 Text 没有任何宽度约束，
 * 于是超长的标题会先吃掉整行宽度，把同一 Row 里的「N 项」胶囊压到 0 宽 ——
 * 这就是「点未来日期时右侧那三个控件像被吃掉」的真正原因。
 * 换成中文短日期后长度减半，跨年场景仍保留年份，不会丢信息。
 */
private fun shortDateLabel(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
