package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.ui.components.AddEditHabitDialog
import io.github.molishadaze.weijing.ui.components.EmptyState
import io.github.molishadaze.weijing.ui.components.UiIcons
import io.github.molishadaze.weijing.ui.components.getIconVector
import io.github.molishadaze.weijing.ui.components.parseColorSafe
import io.github.molishadaze.weijing.ui.components.scheduleLabel
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

/**
 * 「计划清单」区块 —— 嵌在管理中心下半部分（与网页版管理中心的布局一致）。
 *
 * 有意**不用 LazyColumn**：本区块会被放进 SettingsScreen 的 LazyColumn 里，
 * 嵌套 LazyColumn 需要固定内层高度，滚动行为会很怪。习惯数量通常只有几十个，
 * 直接用 Column 全量渲染即可（网页版同样是全量 map，行为对齐）。
 */
@Composable
fun HabitManageSection(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val habitsWithStats by viewModel.habitsWithStats.collectAsState()
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                        text = "${habitsWithStats.size} 项",
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

        if (habitsWithStats.isEmpty()) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                habitsWithStats.forEachIndexed { index, item ->
                    HabitManageCard(
                        habit = item.habit,
                        canMoveUp = index > 0,
                        canMoveDown = index < habitsWithStats.size - 1,
                        onMoveUp = { viewModel.moveHabit(index, index - 1) },
                        onMoveDown = { viewModel.moveHabit(index, index + 1) },
                        onEdit = { editingHabit = item.habit },
                        onDelete = { habitToDelete = item.habit }
                    )
                }
            }
        }
    }

    editingHabit?.let { habit ->
        AddEditHabitDialog(
            initialHabit = habit,
            onDismiss = { editingHabit = null },
            onSave = { updated ->
                // 弹窗回传的是完整 Habit（含 id 与 sortOrder），直接整体更新
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

    habitToDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("确认删除习惯？") },
            text = { Text("确定要删除习惯「${habit.name}」吗？历史打卡记录和留存照片也将一并移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHabit(habit)
                        habitToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // 用统一的 parseColorSafe 而不是就地 try/catch：colorHex 来自用户输入，
    // 非法值必须回退而不是崩溃，且回退逻辑应当全局只有一份。
    val habitColor = parseColorSafe(habit.colorHex, MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    .background(habitColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(habit.iconName),
                    contentDescription = null,
                    tint = habitColor,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 网页版在标题右侧挂两个 badge：子任务数量 / 计数目标
                    if (habit.subTaskList.isNotEmpty()) {
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

        // 内容描述预览（网页版 manage 卡片底部同款浅底块）
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
