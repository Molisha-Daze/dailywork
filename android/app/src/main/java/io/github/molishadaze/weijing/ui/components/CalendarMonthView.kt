package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.util.Haptics
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val CAL_AMBER = Color(0xFFF59E0B)
private val CAL_PURPLE = Color(0xFF8B5CF6)

/**
 * 「完成 / 选中」的强调色，取当前主题主色。
 *
 * 原来是写死的翡翠绿 #10B981，而它同时是计划色板的第一支 —— 于是「今天全部完成」的
 * 实心绿点和「某个计划恰好选了翡翠绿」看起来一模一样。改成跟随主题后，
 * 这层语义永远落在低饱和中性色上，与用户自选的高饱和彩色天然拉得开（见 AppThemeTest）。
 */
@Composable
private fun calAccent(): Color = MaterialTheme.colorScheme.primary

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

/**
 * 月视图日历 + 当天计划清单（与网页版 CalendarMonthView.tsx 一一对齐）：
 *
 * 1. 周一为每周第一天，周六 / 周日表头统一用琥珀色
 * 2. 月份可自由前后翻（**包括未来月份**）——不能翻到未来就无法「提前安排某天的日程」
 * 3. 日期格：选中=emerald 描边+浅底，今天=浅灰底，非本月=半透明；下方状态点
 *    （全完成=实心绿点，有未完成=空心灰点）
 * 4. 下方清单实时联动所选日期，可勾选/计数/挂照片/勾子任务
 * 5. 清单头部有「新建」按钮，可给任意一天（含未来）当场建计划
 */
@Composable
fun CalendarMonthView(
    habits: List<Habit>,
    checkIns: List<CheckIn>,
    onToggleCheckIn: (habitId: Long, date: String) -> Unit,
    onIncrement: (habitId: Long, date: String) -> Unit,
    onDecrement: (habitId: Long, date: String) -> Unit,
    onToggleSubTask: (habitId: Long, date: String, subTaskId: String) -> Unit,
    onCreateForDate: (date: String) -> Unit,
    onOpenDayDetail: (date: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(today) }

    // 切月后 selectedDate 可能还停在别的月份，会把网格高亮和下方清单搞成两天。
    // 这里把它夹回当前可见月份。
    val effectiveSelectedDate = remember(selectedDate, currentMonth) {
        if (YearMonth.from(selectedDate) == currentMonth) {
            selectedDate
        } else {
            currentMonth.atDay(selectedDate.dayOfMonth.coerceAtMost(currentMonth.lengthOfMonth()))
        }
    }

    val checkInsByDateAndHabit = remember(checkIns) {
        checkIns.associateBy { "${it.date}_${it.habitId}" }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------- 1. 月视图网格 ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${currentMonth.year}年 ${currentMonth.monthValue}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // 不在「本月 + 选中今天」的状态时才显示「今日」快捷跳转
                        if (effectiveSelectedDate != today ||
                            currentMonth != YearMonth.from(today)
                        ) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(calAccent().copy(alpha = 0.12f))
                                    .border(1.dp, calAccent().copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .clickable {
                                        currentMonth = YearMonth.from(today)
                                        selectedDate = today
                                    }
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "今日",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = calAccent()
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { currentMonth = currentMonth.minusMonths(1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "上一月",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { currentMonth = currentMonth.plusMonths(1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "下一月",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDays.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            // 周末（周六 / 周日）表头统一用琥珀色区分工作日。
                            // 原实现只给周日上色，周六仍是普通灰 —— 同为休息日却两种视觉，
                            // 看起来像「周日有特殊含义」而不是「周末」。
                            color = if (index >= 5) CAL_AMBER.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val firstDayOfMonth = currentMonth.atDay(1)
                val daysInMonth = currentMonth.lengthOfMonth()
                val leadEmptyDays = firstDayOfMonth.dayOfWeek.value - 1 // DayOfWeek: 1=周一
                val totalCells = ((leadEmptyDays + daysInMonth + 6) / 7) * 7

                for (row in 0 until totalCells / 7) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - leadEmptyDays + 1

                            if (dayNumber in 1..daysInMonth) {
                                val dateObj = currentMonth.atDay(dayNumber)
                                val dateStr = dateObj.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val isSelected = dateObj == effectiveSelectedDate
                                val isToday = dateObj == today

                                val scheduled = habits.filter { HabitSchedule.isScheduled(it, dateObj) }
                                val hasPlans = scheduled.isNotEmpty()
                                val completedCount = scheduled.count { habit ->
                                    HabitSchedule.isCompleted(
                                        habit,
                                        checkInsByDateAndHabit["${dateStr}_${habit.id}"]
                                    )
                                }
                                val isAllCompleted = hasPlans && completedCount == scheduled.size

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, calAccent(), RoundedCornerShape(14.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .background(
                                            when {
                                                isSelected -> calAccent().copy(alpha = 0.12f)
                                                isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { selectedDate = dateObj },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 12.sp,
                                            // ⚠️ 必须显式收紧行高。Material3 的 MaterialTheme 会把
                                            // LocalTextStyle 设成 bodyLarge（lineHeight = 24sp），
                                            // 这里只写了 fontSize，lineHeight 会继续沿用 24sp，
                                            // 单行数字的文字块凭空高一倍，把下面的状态点一路顶到选中绿框边上。
                                            // 12sp 等价于网页版那个 leading-none。
                                            lineHeight = 12.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = when {
                                                isToday -> calAccent()
                                                isSelected -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        if (hasPlans) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isAllCompleted) calAccent() else Color.Transparent)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isAllCompleted) calAccent()
                                                        else Color(0xFF9CA3AF),
                                                        shape = CircleShape
                                                    )
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                // 图例 + 进入当天详情（网页版同款）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendDot(filled = true, label = "已完成")
                        LegendDot(filled = false, label = "未完成")
                    }
                    Row(
                        modifier = Modifier.clickable {
                            onOpenDayDetail(effectiveSelectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "进入当天详情",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = calAccent()
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            UiIcons.OpenInNew,
                            contentDescription = null,
                            tint = calAccent(),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // ---------- 2. 所选日期的计划清单 ----------
        val selectedDateStr = effectiveSelectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val selectedDayHabits = habits.filter { HabitSchedule.isScheduled(it, effectiveSelectedDate) }
        val isSelectedToday = effectiveSelectedDate == today

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isSelectedToday) "今日计划清单"
                                else "${shortDateLabel(effectiveSelectedDate, today)} 计划清单",
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
                                    text = "${selectedDayHabits.size} 项",
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

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedDayHabits.isEmpty()) {
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
}

@Composable
private fun LegendDot(filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (filled) calAccent() else Color.Transparent)
                .then(
                    if (filled) Modifier
                    else Modifier.border(1.5.dp, Color(0xFF9CA3AF), CircleShape)
                )
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
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
                        Haptics.play(if (isDone) Haptics.Level.LIGHT else Haptics.Level.STRONG)
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
                                Haptics.play(Haptics.Level.LIGHT)
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
                                Haptics.play(
                                    if (justReachedTarget) Haptics.Level.STRONG else Haptics.Level.LIGHT
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
