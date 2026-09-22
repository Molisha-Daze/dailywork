package io.github.molishadaze.weijing.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.ui.components.AddEditCounterDialog
import io.github.molishadaze.weijing.ui.components.EmptyState
import io.github.molishadaze.weijing.ui.components.UiIcons
import io.github.molishadaze.weijing.ui.components.parseColorSafe
import io.github.molishadaze.weijing.util.CounterPeriod
import io.github.molishadaze.weijing.util.CounterPeriodCalculator
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.Feedback
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

/** 与网页版 counters 页一致的紫色（tailwind purple-500）。 */
private val COUNTER_PURPLE = Color(0xFF8B5CF6)

/**
 * 独立计数器页。
 *
 * 与「今日打卡」体系完全解耦：这里没有日期、没有排期、没有连续天数，
 * 只是若干可自由增减的计数器，用来记录「冰箱里还剩几罐可乐」这类松散数据。
 */
@Composable
fun StandaloneCountersScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val counters by viewModel.standaloneCounters.collectAsState()
    val counterLogs by viewModel.counterPeriodLogs.collectAsState()

    // 进页面先结算一次：App 很可能在后台躺了一整夜，周期该翻篇了。
    // 仓库层按天短路（同一天只真正跑一次），所以频繁切 tab 进来也没有额外开销。
    LaunchedEffect(Unit) { viewModel.rolloverCounterPeriods() }

    var showEditor by remember { mutableStateOf(false) }
    var editingCounter by remember { mutableStateOf<StandaloneCounter?>(null) }
    var pendingDelete by remember { mutableStateOf<StandaloneCounter?>(null) }
    var pendingReset by remember { mutableStateOf<StandaloneCounter?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCounter = null
                    showEditor = true
                },
                containerColor = COUNTER_PURPLE,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建计数器")
            }
        }
    ) { padding ->
        if (counters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "暂无独立计数器",
                    subtitle = "点击右下角按钮，创建你的第一个计数器，例如记录「冰箱里的可乐」"
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部标题栏：网页版 counters 页是「紫色 # 图标 + 标题 + 新建按钮」的一张卡片，
            // 安卓端原先光秃秃只有一个 FAB，缺的就是这块。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(COUNTER_PURPLE.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                UiIcons.Tag,
                                contentDescription = null,
                                tint = COUNTER_PURPLE,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "独立计数器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                // 主行动按钮跟随主题主色，理由同今日页 FAB
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    editingCounter = null
                                    showEditor = true
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    // 底是主题主色，字色走 onPrimary（夜间主题主色是浅色，白字会看不见）
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "新建计数器",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            items(items = counters, key = { it.id }) { counter ->
                CounterCard(
                    counter = counter,
                    logs = counterLogs[counter.id].orEmpty(),
                    onStep = { delta -> viewModel.stepCounter(counter.id, delta) },
                    onEdit = {
                        editingCounter = counter
                        showEditor = true
                    },
                    onDelete = { pendingDelete = counter },
                    onResetClick = { pendingReset = counter },
                    onResetLongClick = {
                        // 长按跳过了二次确认，反馈就是「已经执行了」的唯一凭据。
                        Feedback.fire(Feedback.Event.CLEARED)
                        viewModel.resetCounter(counter.id)
                    }
                )
            }
        }
    }

    if (showEditor) {
        AddEditCounterDialog(
            initialCounter = editingCounter,
            onDismiss = { showEditor = false },
            onSave = { counter ->
                if (counter.id == 0L) {
                    viewModel.addCounter(counter)
                } else {
                    viewModel.updateCounter(counter)
                }
                showEditor = false
            }
        )
    }

    pendingDelete?.let { counter ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除计数器") },
            text = { Text("确定要删除「${counter.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    // 删除不可逆，给一次「已执行」确实认。
                    // 音效刻意用不好听的下滑低音 —— 给破坏性操作配好听的音，
                    // 等于在鼓励用户多删数据。
                    Feedback.fire(Feedback.Event.CLEARED)
                    viewModel.deleteCounter(counter.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    pendingReset?.let { counter ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text("清零") },
            text = { Text("将「${counter.name}」的数值重置为 0。（长按清零按钮可直接跳过本确认）") },
            confirmButton = {
                TextButton(onClick = {
                    Feedback.fire(Feedback.Event.CLEARED)
                    viewModel.resetCounter(counter.id)
                    pendingReset = null
                }) {
                    Text("清零")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CounterCard(
    counter: StandaloneCounter,
    logs: List<CounterPeriodLog>,
    onStep: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetClick: () -> Unit,
    onResetLongClick: () -> Unit
) {
    val themeColor = parseColorSafe(counter.colorHex, MaterialTheme.colorScheme.primary)
    val hasLimit = counter.hasLimit && counter.limitCount != null && counter.limitCount!! > 0
    val limit = counter.limitCount ?: 0
    val progress = if (hasLimit) (counter.currentCount.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val remaining = if (hasLimit) (limit - counter.currentCount).coerceAtLeast(0) else 0
    val reachedLimit = hasLimit && counter.currentCount >= limit

    var historyExpanded by remember { mutableStateOf(false) }

    // 当前周期信息。
    // 锚点缺失（老数据刚升级、还没轮到第一次结算）时按今天估算，卡片上不会突然空一块；
    // 真实锚点会在下一次结算时落到库里，届时这里自然显示成正确的周期。
    val today = DateUtils.todayDate()
    val periodAnchor = counter.periodStartDate
        ?.let { text -> runCatching { DateUtils.parseDate(text) }.getOrNull() }
        ?: today
    val currentPeriod = CounterPeriodCalculator.periodOf(
        counter.resetPeriod, counter.resetIntervalDays, periodAnchor, today
    )
    val periodBadge = CounterPeriodCalculator.badgeText(
        counter.resetPeriod, counter.resetIntervalDays, periodAnchor, today
    )
    val showPeriod = periodBadge != null && currentPeriod != null

    // 卡片分两种密度：
    // - 有上限：进度条、剩余量、周期徽章都是真信息，值得占高度；
    // - 无上限：上面除标题外没有元信息，再照搬一套留白就是纯浪费（下面几处按此分流）。
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(if (hasLimit) 16.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = counter.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    counter.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 三个操作统一走 40dp 的自绘盒，而不是「一个自绘 + 两个 IconButton」：
                // 1) IconButton 自带 48dp 最小触控区，比自绘盒高 8dp，会把标题行整体顶高；
                // 2) 它的图标是 24dp，自绘盒是 18dp，一排三个按钮肉眼可见一大一小；
                // 3) 横向也省下 16dp，标题能多显示几个字。
                // 清零按钮同时支持短按确认与长按直接执行 —— 刻意不用 IconButton，
                // 它自带的 clickable 会与外层 combinedClickable 抢手势，导致长按/短按有一个失效。
                Row {
                    IconActionBox(
                        imageVector = UiIcons.RestartAlt,
                        contentDescription = "清零，长按可直接清零",
                        onClick = onResetClick,
                        onLongClick = onResetLongClick
                    )
                    IconActionBox(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        onClick = onEdit
                    )
                    IconActionBox(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        onClick = onDelete
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (hasLimit) 10.dp else 8.dp))

            if (hasLimit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "上限：$limit ${counter.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (reachedLimit) "已达上限" else "剩余：$remaining ${counter.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reachedLimit) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = themeColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 无上限时刻意不写「无上限自由计数」这一行：
            // 没有进度条、没有「上限 / 剩余」本身就是「不限量」的信号，
            // 再补一句纯说明文字，等于拿整整一行的高度去重复同一件事。

            // 周期徽章：只对配了自动归零的计数器显示。
            // 「今天 / 本周 / 本月 / 第 2/3 天」——用户一眼就能确认
            // 眼前这个数字算的是哪一段时间，不必去翻设置。
            // 这里仍写成显式的双判空而不是复用 showPeriod：只有这样编译器才能把
            // periodBadge / currentPeriod 智能转换成非空，省掉一串 !!。
            if (periodBadge != null && currentPeriod != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = periodBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = CounterPeriodCalculator.formatRange(currentPeriod),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 分隔线是给「上方确实有元信息」的情况划界的。无上限又不归零的卡片，
            // 标题下面直接就是数值，再画一条横线只会把「数值 + 加减」往下推。
            if (hasLimit || showPeriod) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            CounterValueRow(
                counter = counter,
                themeColor = themeColor,
                hasLimit = hasLimit,
                limit = limit,
                onStep = onStep
            )

            // 历史入口。默认收起 —— 卡片首屏要留给「现在是多少」，
            // 历史是"想回顾时才展开"的次要信息。
            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                CounterHistorySection(
                    logs = logs,
                    accentColor = themeColor,
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded }
                )
            }
        }
    }
}

/**
 * 卡片的「数值 + 增减」主操作行。
 *
 * 抽成独立组件是因为有上限 / 无上限两种密度现在共用一个实现 ——
 * 各写一份的话，改一处力度、间距就会漏掉另一处，卡片立刻长得不一样。
 */
@Composable
private fun CounterValueRow(
    counter: StandaloneCounter,
    themeColor: Color,
    hasLimit: Boolean,
    limit: Int,
    onStep: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = counter.currentCount.toString(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = themeColor
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = counter.unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    Feedback.fire(Feedback.Event.COUNTER_BACK)
                    onStep(-counter.step)
                },
                enabled = counter.currentCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(UiIcons.Remove, contentDescription = "减 ${counter.step}")
            }
            Button(
                onClick = {
                    // 「刚好到顶」才给强反馈。已经到上限后继续点不再强化，
                    // 否则「喝完了」这个信号会被后续每一次点击淹没。
                    val justHitLimit = hasLimit &&
                        counter.currentCount < limit &&
                        counter.currentCount + counter.step >= limit
                    Feedback.fire(
                        if (justHitLimit) Feedback.Event.COUNTER_LIMIT
                        else Feedback.Event.COUNTER_STEP
                    )
                    onStep(counter.step)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+${counter.step}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 展开时最多列出多少个周期；再多就只给一句总数提示，避免卡片被撑成一张长表。 */
private const val HISTORY_PREVIEW_COUNT = 7

/**
 * 计数器的历史周期列表（可折叠）。
 *
 * 这里展示的是**已经结算完的周期**——「昨天喝了 3 杯」就落在这里。
 * 归档记录只会往追加、不会改（同周期多次归档在仓库层已合并），
 * 所以列表顺序天然稳定，不会出现点一下 +1 就跳位的情况。
 */
@Composable
private fun CounterHistorySection(
    logs: List<CounterPeriodLog>,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                UiIcons.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "历史记录 · ${logs.size} 个周期",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) UiIcons.ExpandLess else UiIcons.ExpandMore,
                contentDescription = if (expanded) "收起历史记录" else "展开历史记录",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(2.dp))
            logs.take(HISTORY_PREVIEW_COUNT).forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = periodRangeText(log),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${log.count} ${log.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }
            if (logs.size > HISTORY_PREVIEW_COUNT) {
                Text(
                    text = "仅显示最近 $HISTORY_PREVIEW_COUNT 个周期，共 ${logs.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                )
            }
        }
    }
}

/**
 * 归档记录的两端日期 → 可读区间。
 *
 * 解析失败时退回原始字符串而不是抛异常：历史列表只是展示，
 * 一条脏记录不该让整个页面崩掉（它还是用户自己写进备份文件里带回来的）。
 */
private fun periodRangeText(log: CounterPeriodLog): String {
    val start = runCatching { DateUtils.parseDate(log.periodStart) }.getOrNull()
        ?: return log.periodStart
    val end = runCatching { DateUtils.parseDate(log.periodEnd) }.getOrNull()
        ?: return log.periodStart
    return CounterPeriodCalculator.formatRange(CounterPeriod(start, end))
}

/**
 * 自绘图标按钮，支持点击 + 长按。
 * 不用 IconButton 是为了避免其内置 clickable 与 [combinedClickable] 抢手势。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconActionBox(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
