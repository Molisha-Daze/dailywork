package io.github.molishadaze.weijing.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

/**
 * 「计划清单」区块 —— 嵌在管理中心下半部分。
 *
 * 智能分流与折叠：
 * 1. 进行中计划（活跃习惯、尚未过期的任务）置顶展示，保持清单清爽；
 * 2. 往期/已完结日程（单次已完成或已到期日程）默认收叠，支持一键全部归档；
 * 3. 已归档计划提供次级折叠与恢复能力；
 * 4. 彻底区分「归档（保留历史打卡与照片）」与「删除（级联破坏数据）」。
 */
@Composable
fun HabitManageSection(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val habitsWithStats by viewModel.habitsWithStats.collectAsState()
    val archivedHabits by viewModel.archivedHabits.collectAsState()

    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }
    var habitToArchive by remember { mutableStateOf<Habit?>(null) }
    var showArchiveAllConfirm by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isFinishedExpanded by rememberSaveable { mutableStateOf(false) }
    var isArchivedExpanded by rememberSaveable { mutableStateOf(false) }

    val today = remember { DateUtils.todayDate() }
    val (activeList, finishedList) = remember(habitsWithStats, today) {
        habitsWithStats.partition { !HabitSchedule.isFinished(it.habit, today, it.totalCheckIns) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "计划清单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "进行中 ${activeList.size} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 6.dp
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("新增计划", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeList.isEmpty() && finishedList.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    title = "还没有计划项目",
                    subtitle = "点右上角「新增计划」创建你的第一个习惯，或直接载入示例看看效果",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = { viewModel.loadSampleData() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("载入示例数据")
                    }
                }
            }
        } else {
            if (activeList.isEmpty()) {
                Text(
                    text = "当前没有进行中的计划，所有排期均已完结或到期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeList.forEachIndexed { index, item ->
                        HabitManageCard(
                            habit = item.habit,
                            isFinished = false,
                            canMoveUp = index > 0,
                            canMoveDown = index < activeList.size - 1,
                            onMoveUp = { viewModel.moveHabit(index, index - 1) },
                            onMoveDown = { viewModel.moveHabit(index, index + 1) },
                            onEdit = { editingHabit = item.habit },
                            onArchive = { habitToArchive = item.habit },
                            onDelete = { habitToDelete = item.habit }
                        )
                    }
                }
            }

            // ---------- 往期 / 已完结日程（折叠归类） ----------
            if (finishedList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isFinishedExpanded = !isFinishedExpanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFinishedExpanded) UiIcons.ExpandLess else UiIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "往期 / 已完结日程",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${finishedList.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { showArchiveAllConfirm = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            )
                        ) {
                            Icon(
                                imageVector = UiIcons.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全部归档", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                AnimatedVisibility(visible = isFinishedExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        finishedList.forEach { item ->
                            HabitManageCard(
                                habit = item.habit,
                                isFinished = true,
                                canMoveUp = false,
                                canMoveDown = false,
                                onMoveUp = {},
                                onMoveDown = {},
                                onEdit = { editingHabit = item.habit },
                                onArchive = { habitToArchive = item.habit },
                                onDelete = { habitToDelete = item.habit }
                            )
                        }
                    }
                }
            }

            // ---------- 已归档计划（底部次级折叠） ----------
            if (archivedHabits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isArchivedExpanded = !isArchivedExpanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isArchivedExpanded) UiIcons.ExpandLess else UiIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已归档计划",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${archivedHabits.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                AnimatedVisibility(visible = isArchivedExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        archivedHabits.forEach { habit ->
                            ArchivedHabitCard(
                                habit = habit,
                                onUnarchive = { viewModel.unarchiveHabit(habit) },
                                onDelete = { habitToDelete = habit }
                            )
                        }
                    }
                }
            }
        }
    }

    editingHabit?.let { habit ->
        AddEditHabitDialog(
            initialHabit = habit,
            onDismiss = { editingHabit = null },
            onSave = { updated ->
                viewModel.updateHabit(updated)
                editingHabit = null
            }
        )
    }

    if (showAddDialog) {
        AddEditHabitDialog(
            onDismiss = { showAddDialog = false },
            onSave = { habit -> viewModel.addHabit(habit) }
        )
    }

    habitToArchive?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToArchive = null },
            title = { Text("归档计划") },
            text = {
                Text(
                    "确定要归档计划「${habit.name}」吗？\n\n" +
                        "归档后该计划将从日常清单中隐藏并取消定时提醒，但历史打卡记录、日历轨迹与留存照片均会完整保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archiveHabit(habit)
                        habitToArchive = null
                    }
                ) {
                    Text("确认归档")
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToArchive = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showArchiveAllConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveAllConfirm = false },
            title = { Text("全部归档往期日程？") },
            text = {
                Text(
                    "将把当前 ${finishedList.size} 项已完结或过期的往日日程一键归档。\n\n" +
                        "历史打卡记录与留存照片将完整保留在月历与流水中。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archiveFinishedHabits(finishedList.map { it.habit })
                        showArchiveAllConfirm = false
                    }
                ) {
                    Text("一键归档")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    habitToDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("确认彻底删除？") },
            text = {
                Text(
                    "确定要彻底删除计划「${habit.name}」吗？\n\n" +
                        "⚠️ 关联的历史打卡记录和留存照片也将一并彻底移除且无法恢复。\n\n" +
                        "提示：若只想清理界面，建议使用「归档」，历史打卡与照片将 100% 完整保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHabit(habit)
                        habitToDelete = null
                    }
                ) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HabitManageCard(
    habit: Habit,
    isFinished: Boolean = false,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val habitColor = parseColorSafe(habit.colorHex, MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFinished) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFinished) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                habitColor.copy(alpha = 0.16f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconVector(habit.iconName),
                        contentDescription = null,
                        tint = if (isFinished) MaterialTheme.colorScheme.outline else habitColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFinished) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isFinished) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ManageBadge(
                                text = "已完结",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (habit.subTaskList.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ManageBadge(
                                text = "${habit.subTaskList.size} 个子任务",
                                tint = Color(0xFF8B5CF6)
                            )
                        } else if (habit.isCounter) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ManageBadge(
                                text = "计数目标 ${HabitSchedule.effectiveTarget(habit)} ${habit.unit}",
                                tint = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = scheduleLabel(habit),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        if (habit.reminderTime != null) {
                            Icon(
                                imageVector = UiIcons.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = habit.reminderTime!!,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "无提醒",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "生效: ${habit.startDate.ifBlank { "不限" }}" +
                            if (habit.endDate.isNullOrBlank()) " (长期)" else " 至 ${habit.endDate}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isFinished) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            UiIcons.ArrowUpward,
                            contentDescription = "上移",
                            tint = if (canMoveUp) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            UiIcons.ArrowDownward,
                            contentDescription = "下移",
                            tint = if (canMoveDown) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }

                IconButton(onClick = onArchive, modifier = Modifier.size(30.dp)) {
                    Icon(
                        UiIcons.Archive,
                        contentDescription = "归档",
                        tint = if (isFinished) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!habit.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = habit.description!!,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                )
            }
        }
    }
}

/** 已归档项目的展示卡片，强调「恢复」与「彻底删除」。 */
@Composable
private fun ArchivedHabitCard(
    habit: Habit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(habit.iconName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = scheduleLabel(habit),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            TextButton(
                onClick = onUnarchive,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 2.dp
                )
            ) {
                Icon(
                    imageVector = UiIcons.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("恢复", style = MaterialTheme.typography.labelSmall)
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "彻底删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 网页版 manage 卡片标题右侧的小胶囊。 */
@Composable
private fun ManageBadge(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}
