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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.util.HabitSchedule
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
/** 标题 + 「今日」快捷跳转 + 上一月 / 下一月。 */
@Composable
internal fun MonthNavRow(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onJumpToToday: () -> Unit,
    onMonthChange: (YearMonth) -> Unit
) {
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
            if (selectedDate != today || currentMonth != YearMonth.from(today)) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(calAccent().copy(alpha = 0.12f))
                        .border(1.dp, calAccent().copy(alpha = 0.4f), RoundedCornerShape(50))
                        .clickable(onClick = onJumpToToday)
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
            onClick = { onMonthChange(currentMonth.minusMonths(1)) },
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
            onClick = { onMonthChange(currentMonth.plusMonths(1)) },
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
}

/** 星期表头。周末（周六 / 周日）统一用琥珀色。 */
@Composable
internal fun WeekdayHeaderRow() {
    val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                // 原实现只给周日上色，周六仍是普通灰 —— 同为休息日却两种视觉，
                // 看起来像「周日有特殊含义」而不是「周末」。
                color = if (index >= 5) CAL_AMBER.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 日期网格：每月永远是整周对齐的若干行，空格子用空白 Box 占位。 */
@Composable
internal fun MonthGridRows(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    checkInsByDateAndHabit: Map<String, CheckIn>,
    onSelectDate: (LocalDate) -> Unit
) {
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
                    DayCell(
                        habitSummaries = DaySummary.of(
                            habits = habits,
                            date = dateObj,
                            checkInsByDateAndHabit = checkInsByDateAndHabit
                        ),
                        dayNumber = dayNumber,
                        isSelected = dateObj == selectedDate,
                        isToday = dateObj == today,
                        onClick = { onSelectDate(dateObj) }
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

/** 一个日期格的汇总数据，由 [MonthGridRows] 在循坏外算好传进来。 */
private class DaySummary(
    val hasPlans: Boolean,
    val completedCount: Int,
    val scheduledCount: Int
) {
    val isAllCompleted: Boolean get() = hasPlans && completedCount == scheduledCount

    companion object {
        fun of(
            habits: List<Habit>,
            date: LocalDate,
            checkInsByDateAndHabit: Map<String, CheckIn>
        ): DaySummary {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val scheduled = habits.filter { HabitSchedule.isScheduled(it, date) }
            val completedCount = scheduled.count { habit ->
                HabitSchedule.isCompleted(habit, checkInsByDateAndHabit["${dateStr}_${habit.id}"])
            }
            return DaySummary(
                hasPlans = scheduled.isNotEmpty(),
                completedCount = completedCount,
                scheduledCount = scheduled.size
            )
        }
    }
}

/** 单个日期格：数字 + 下方状态点（全完成实心、有未完成空心）。 */
@Composable
private fun RowScope.DayCell(
    habitSummaries: DaySummary,
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, calAccent(), RoundedCornerShape(14.dp))
                else Modifier
            )
            .background(
                when {
                    isSelected -> calAccent().copy(alpha = 0.12f)
                    isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
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
            if (habitSummaries.hasPlans) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (habitSummaries.isAllCompleted) calAccent() else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (habitSummaries.isAllCompleted) calAccent()
                            else Color(0xFF9CA3AF),
                            shape = CircleShape
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/** 月历卡片底部：完成度图例 + 「进入当天详情」。 */
@Composable
internal fun CalendarFooterRow(
    selectedDate: LocalDate,
    onOpenDayDetail: (String) -> Unit
) {
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
                onOpenDayDetail(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
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

private val CAL_AMBER = Color(0xFFF59E0B)
