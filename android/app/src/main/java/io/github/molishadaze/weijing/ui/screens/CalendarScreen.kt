package io.github.molishadaze.weijing.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.molishadaze.weijing.ui.components.AddEditHabitDialog
import io.github.molishadaze.weijing.ui.components.CalendarMonthView
import io.github.molishadaze.weijing.ui.components.DayDetailDialog
import io.github.molishadaze.weijing.ui.components.PhotoViewerDialog
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

/**
 * 打卡日历页（导航第二个 tab），与网页版 App.tsx 的 history tab 对齐：
 * 标题「打卡日历」+ 月视图网格 + 所选日期的计划清单。
 *
 * 网页版没有「全部打卡流水」列表，所以这一版也把它去掉了——
 * 两页内容对不上比少一个流水列表更让人困惑。
 */
@Composable
fun CalendarScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val habits by viewModel.allHabits.collectAsState()
    val allCheckIns by viewModel.allCheckIns.collectAsState()

    var detailDate by remember { mutableStateOf<String?>(null) }
    var createForDate by remember { mutableStateOf<String?>(null) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp)
    ) {
        item {
            Text(
                text = "打卡日历",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            CalendarMonthView(
                habits = habits,
                checkIns = allCheckIns,
                onToggleCheckIn = { habitId, date -> viewModel.toggleCheckIn(habitId, date) },
                onIncrement = { habitId, date -> viewModel.incrementCheckIn(habitId, date) },
                onDecrement = { habitId, date -> viewModel.decrementCheckIn(habitId, date) },
                onToggleSubTask = { habitId, date, subTaskId ->
                    viewModel.toggleSubTask(habitId, subTaskId, date)
                },
                // 关键：把所选日期直接带进新建对话框，这样就能给未来某天安排日程
                onCreateForDate = { date -> createForDate = date },
                onOpenDayDetail = { date -> detailDate = date }
            )
        }
    }

    // 某一天的完整详情
    detailDate?.let { date ->
        DayDetailDialog(
            date = date,
            habits = habits.filter { HabitSchedule.isScheduled(it, date) },
            checkIns = allCheckIns,
            onDismiss = { detailDate = null },
            onToggleCheckIn = { habitId, d -> viewModel.toggleCheckIn(habitId, d) },
            onIncrement = { habitId, d -> viewModel.incrementCheckIn(habitId, d) },
            onDecrement = { habitId, d -> viewModel.decrementCheckIn(habitId, d) },
            onToggleSubTask = { habitId, d, subTaskId ->
                viewModel.toggleSubTask(habitId, subTaskId, d)
            },
            onAttachPhoto = { habitId, d, uri ->
                viewModel.attachPhotoFromUri(context, habitId, uri, d)
            },
            onRemovePhoto = { habitId, d -> viewModel.removePhoto(habitId, d) },
            onViewPhoto = { path -> viewingPhotoPath = path }
        )
    }

    // 为指定日期新建计划
    createForDate?.let { date ->
        AddEditHabitDialog(
            initialHabit = null,
            // 未来日期默认建成「仅当天」的单次计划，否则一个每天循环的习惯
            // 会从今天开始每天冒出来，与用户「安排那一天」的意图相反。
            defaultStartDate = date,
            defaultRecurrenceType = if (date > DateUtils.today()) {
                HabitSchedule.TYPE_NONE
            } else {
                HabitSchedule.DEFAULT_TYPE
            },
            onDismiss = { createForDate = null },
            onSave = { habit -> viewModel.addHabit(habit) }
        )
    }

    viewingPhotoPath?.let { path ->
        PhotoViewerDialog(photoPath = path, onDismiss = { viewingPhotoPath = null })
    }
}
