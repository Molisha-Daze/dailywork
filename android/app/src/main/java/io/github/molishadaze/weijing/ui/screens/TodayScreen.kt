package io.github.molishadaze.weijing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.data.DailyQuote
import io.github.molishadaze.weijing.model.UpcomingHabit
import io.github.molishadaze.weijing.ui.components.AddEditHabitDialog
import io.github.molishadaze.weijing.ui.components.EmptyState
import io.github.molishadaze.weijing.ui.components.HabitCard
import io.github.molishadaze.weijing.ui.components.PhotoViewerDialog
import io.github.molishadaze.weijing.ui.components.UiIcons
import io.github.molishadaze.weijing.ui.components.WEEKDAY_CN
import io.github.molishadaze.weijing.ui.components.getIconVector
import io.github.molishadaze.weijing.ui.components.parseColorSafe
import io.github.molishadaze.weijing.ui.components.scheduleLabel
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.viewmodel.HabitViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    viewModel: HabitViewModel,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 只显示今天真正有排期的习惯（「每周一三五」这类在没排期的日子不该出现）
    val todayHabits by viewModel.todayHabits.collectAsState()
    // 今天没排期时用来兜底的「即将到来」列表，每个习惯只取下一次日期
    val upcomingHabits by viewModel.upcomingHabits.collectAsState()
    // 每日格言。初值就是内置库当天的句子，所以界面不必为「还没取到」准备任何占位/骨架屏
    val quote by viewModel.todayQuote.collectAsState()
    // 不 remember：跨零点后要能跟着列表一起刷新，LocalDate.now() 的开销可以忽略
    val today = DateUtils.todayDate()

    // 进入本页时同步一次格言：当天已有远程缓存会立刻返回、不发请求；没有才会异步取一条。
    // 用 today 作 key 是为了跨零点后自动换新 —— 日期一变，这个 effect 会重跑。
    LaunchedEffect(today) {
        viewModel.refreshTodayQuote(settings)
    }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val completedCount = todayHabits.count { it.isCompletedToday }
    val totalCount = todayHabits.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    // 「再完成一项就今日全齐」。传给卡片，用于把成就音升级成全天级别的收尾音。
    //
    // 这里算的是「还能不能再完成一项」，卡片内部还会用 isCompleted / justReachedTarget
    // 确认本次点击确实是「完成」方向 —— 两者组合才能得出「这次点击完成了全天」。
    // 不在这里做状态边沿检测（监听 progress 从 <100% 跨到 100%），是因为那样
    // PLAN_DONE 与 DAY_DONE 会先后响两声叠成噪音，而且卡片在历史页也被复用，
    // 页面层检测拿不到「这一下点击属于哪个习惯」的上下文。
    val completesDay = totalCount > 0 && completedCount + 1 == totalCount

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                // 主行动按钮一律用「主题主色 + onPrimary」，不写死翡翠绿 ——
                // 原因见 AppTheme.kt 顶部的「设计硬约束」注释块。
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增计划")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ⚠️ 这里刻意**不再**用 `if (空) 空状态 else 列表` 分流。
            // 只要分流，格言卡就会被关在 else 里，于是「一条日程都没有」那天反而看不到句子 ——
            // 而那天恰恰是最需要一句话的时候。现在列表恒在，靠 item 顺序拼装：
            // 头部信息卡（三选一）→ 格言卡 →（今天没排期时）即将到来 → 今日习惯。
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 头部信息卡三选一：
                // 有排期 → 进度汇总；今天空着但后面有 → 「今天没有排期」说明卡；
                // 彻底没有任何活跃计划 → 空状态（此时格言仍然会出现在它下面）。
                item(key = "header") {
                    when {
                        todayHabits.isNotEmpty() -> TodayProgressCard(
                            completedCount = completedCount,
                            totalCount = totalCount,
                            progress = progress
                        )

                        upcomingHabits.isNotEmpty() -> NoScheduleTodayCard()

                        else -> EmptyState(
                            title = "今天没有安排打卡计划",
                            subtitle = "已有习惯今天不排期，或点击右下角 + 新建一个。",
                            icon = Icons.Outlined.CheckCircle
                        )
                    }
                }

                // 每日格言永远是第二块，位置锚在同一视觉层级：
                // 用户不会因为「今天有没有排期 / 有没有计划」就找不到它。
                item(key = "quote") {
                    DailyQuoteCard(quote = quote)
                }

                // 今天没有排期，但后面有：按日期列出即将到来的计划。
                // 循环习惯在这里只出现一次（下一次），不会把未来几十次排期全铺开。
                if (todayHabits.isEmpty() && upcomingHabits.isNotEmpty()) {
                    item(key = "upcomingLabel") {
                        UpcomingSectionLabel(count = upcomingHabits.size)
                    }
                    items(
                        items = upcomingHabits,
                        key = { "${it.habit.id}_${it.date}" }
                    ) { upcoming ->
                        UpcomingHabitCard(upcoming = upcoming, today = today)
                    }
                }

                // Habit list
                items(
                    items = todayHabits,
                    key = { it.habit.id }
                ) { item ->
                    HabitCard(
                        item = item,
                        completesDay = completesDay,
                        // 有大计划时，整卡点击是「整组全选 / 全不选」而不是简单的有↔无，
                        // 否则会出现「勾了 2/4 项却被整卡一击标成已完成」的矛盾状态。
                        onToggleCheckIn = {
                            if (item.hasSubTasks) {
                                viewModel.toggleSubTaskGroup(item.habit.id)
                            } else {
                                viewModel.toggleCheckIn(item.habit.id)
                            }
                        },
                        onToggleSubTask = { subTaskId ->
                            viewModel.toggleSubTask(item.habit.id, subTaskId)
                        },
                        onIncrement = { viewModel.incrementCheckIn(item.habit.id) },
                        onDecrement = { viewModel.decrementCheckIn(item.habit.id) },
                        onPhotoSelected = { uri ->
                            viewModel.attachPhotoFromUri(context, item.habit.id, uri)
                        },
                        onPhotoClick = { path -> selectedPhotoPath = path },
                        onRemovePhoto = { viewModel.removePhoto(item.habit.id) }
                    )
                }
            }

            // Full screen photo viewer
            selectedPhotoPath?.let { path ->
                PhotoViewerDialog(
                    photoPath = path,
                    onDismiss = { selectedPhotoPath = null }
                )
            }

            // Add Habit Dialog
            if (showAddDialog) {
                AddEditHabitDialog(
                    onDismiss = { showAddDialog = false },
                    onSave = { habit -> viewModel.addHabit(habit) }
                )
            }
        }
    }
}

/** 今日进度汇总卡（网页版同款：白卡 + 进度条 + 百分比）。 */
@Composable
private fun TodayProgressCard(
    completedCount: Int,
    totalCount: Int,
    progress: Float
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日打卡",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = DateUtils.today(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$completedCount / $totalCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "完成进度",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 每日格言卡。
 *
 * 视觉上刻意用「引用」的语法，而不是「待办」的语法，所以和习惯卡拉开三处差异：
 * 圆角 16dp（习惯卡 20dp）、没有图标位、左侧一条 3dp 竖引用线。
 * 用户扫一眼就能明白这不是一件要去做的事，而是一句可以掠过的话。
 *
 * 句子从哪来（远程接口还是内置库）由 ViewModel 决定，这里不关心 ——
 * 卡片只负责显示「今天是哪一句」，所以两种来源下样式完全一致，用户不会看到
 * 「今天这张卡长得不一样」这种因为网络状态导致的视觉抖动。
 */
@Composable
private fun DailyQuoteCard(quote: DailyQuote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // height(IntrinsicSize.Min) + fillMaxHeight：让竖线高度跟着文字走。
        // 句子换成两行时线也跟着变长，不会出现「线只有一行高、文字却两行」的错位。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.text,
                    // 格言是会换行的长文本，必须显式给 lineHeight：
                    // 只改 fontSize 不会覆盖继承来的 24sp 行高，两行会散成一团。
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "—— ${quote.source}",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 「今天没有排期」的说明卡。
 *
 * 它代替的是原来那一整页空白：今天确实没有安排，但下面还有东西要看 ——
 * 一句话把「为什么这里是空的」说清楚，比单纯一个空状态图标更让人安心。
 */
@Composable
private fun NoScheduleTodayCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UiIcons.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今天没有排期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${DateUtils.today()} · 下面是最近会出现的计划",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpcomingSectionLabel(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "即将到来",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count 个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一条即将到来的日程。
 *
 * 刻意做成**只读**：这里显示的是未来的日期，无法在今天打卡；
 * 真要提前安排请去「历史回顾」里的月历（那边支持给任意一天建计划 / 勾选）。
 * 每个习惯只会出现一次，就是它下一次的排期。
 */
@Composable
private fun UpcomingHabitCard(upcoming: UpcomingHabit, today: LocalDate) {
    val habit = upcoming.habit
    val habitColor = parseColorSafe(habit.colorHex, MaterialTheme.colorScheme.primary)
    val daysAhead = ChronoUnit.DAYS.between(today, upcoming.date)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧日期块：一眼看出「还有几天」
            Column(
                modifier = Modifier
                    .size(width = 54.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (daysAhead) {
                        1L -> "明天"
                        2L -> "后天"
                        else -> "周${WEEKDAY_CN[upcoming.date.dayOfWeek.value] ?: ""}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${upcoming.date.monthValue}/${upcoming.date.dayOfMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scheduleLabel(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (daysAhead > 2) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$daysAhead 天后",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(habitColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(habit.iconName),
                    contentDescription = habit.name,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
