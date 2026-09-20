package io.github.molishadaze.weijing.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.molishadaze.weijing.ui.components.AddEditHabitDialog
import io.github.molishadaze.weijing.ui.components.EmptyState
import io.github.molishadaze.weijing.ui.components.HabitCard
import io.github.molishadaze.weijing.ui.components.PhotoViewerDialog
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.viewmodel.HabitViewModel

@Composable
fun TodayScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 只显示今天真正有排期的习惯（「每周一三五」这类在没排期的日子不该出现）
    val todayHabits by viewModel.todayHabits.collectAsState()
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val completedCount = todayHabits.count { it.isCompletedToday }
    val totalCount = todayHabits.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                // 网页版 FAB 是 emerald-500 圆角方块，这里用同色 + 白图标对齐
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
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
            if (todayHabits.isEmpty()) {
                EmptyState(
                    title = "今天没有安排打卡计划",
                    subtitle = "已有习惯今天不排期，或点击右下角 + 新建一个。",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header progress summary（网页版同款白色汇总卡 + 进度条 + 百分比）
                    item {
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
                                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "$completedCount / $totalCount",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
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
                                    color = Color(0xFF10B981),
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

                    // Habit list
                    items(
                        items = todayHabits,
                        key = { it.habit.id }
                    ) { item ->
                        HabitCard(
                            item = item,
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
