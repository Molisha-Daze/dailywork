package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月视图日历 + 当天计划清单（与网页版 CalendarMonthView.tsx 一一对齐）：
 *
 * 1. 周一为每周第一天，周六 / 周日表头统一用琥珀色
 * 2. 月份可自由前后翻（**包括未来月份**）——不能翻到未来就无法「提前安排某天的日程」
 * 3. 日期格：选中=主色描边+浅底，今天=浅灰底，非本月=半透明；下方状态点
 *    （全完成=实心点，有未完成=空心灰点）
 * 4. 下方清单实时联动所选日期，可勾选/计数/挂照片/勾子任务
 * 5. 清单头部有「新建」按钮，可给任意一天（含未来）当场建计划
 *
 * ⚠️ 拆分约定：这里只保留**选中的月份与日期两个状态**，渲染全部下沉到下面的小块。
 * 小块一律无状态、参数显式传入 —— 切文件时状态宿主没动，行为和拆分前完全一致；
 * 反过来如果把 `currentMonth` / `selectedDate` 的 remember 下移到某个小块里，
 * 翻月就会把它连同小块一起重建，选中态直接丢失。
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
        CalendarCard(
            currentMonth = currentMonth,
            selectedDate = effectiveSelectedDate,
            today = today,
            habits = habits,
            checkInsByDateAndHabit = checkInsByDateAndHabit,
            onJumpToToday = {
                currentMonth = YearMonth.from(today)
                selectedDate = today
            },
            onMonthChange = { currentMonth = it },
            onSelectDate = { selectedDate = it },
            onOpenDayDetail = onOpenDayDetail
        )

        SelectedDayPlanCard(
            selectedDate = effectiveSelectedDate,
            today = today,
            habits = habits,
            checkInsByDateAndHabit = checkInsByDateAndHabit,
            onToggleCheckIn = onToggleCheckIn,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
            onToggleSubTask = onToggleSubTask,
            onCreateForDate = onCreateForDate,
            onOpenDayDetail = onOpenDayDetail
        )
    }
}

/** 上半部分的月历卡片：月头导航 → 星期表头 → 日期网格 → 图例。 */
@Composable
private fun CalendarCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    checkInsByDateAndHabit: Map<String, CheckIn>,
    onJumpToToday: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDayDetail: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MonthNavRow(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                today = today,
                onJumpToToday = onJumpToToday,
                onMonthChange = onMonthChange
            )

            Spacer(modifier = Modifier.height(10.dp))
            WeekdayHeaderRow()

            Spacer(modifier = Modifier.height(6.dp))
            MonthGridRows(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                today = today,
                habits = habits,
                checkInsByDateAndHabit = checkInsByDateAndHabit,
                onSelectDate = onSelectDate
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // 图例 + 进入当天详情（网页版同款）
            CalendarFooterRow(selectedDate = selectedDate, onOpenDayDetail = onOpenDayDetail)
        }
    }
}

/**
 * 「完成 / 选中」的强调色，取当前主题主色。
 *
 * 放在月历入口文件里是因为三个日历相关文件都要用（同包，`internal` 即可，无需 import）。
 * 为什么不写死一支绿 —— 完整原因见 AppTheme.kt 顶部的「设计硬约束」注释块。
 */
@Composable
internal fun calAccent(): Color = MaterialTheme.colorScheme.primary
