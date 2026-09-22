package io.github.molishadaze.weijing.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.model.HabitWithStats
import io.github.molishadaze.weijing.model.SubTask
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.util.Haptics
import java.io.File

/** 与网页版 emerald-500 一致的完成态色。 */

@Composable
fun HabitCard(
    item: HabitWithStats,
    onToggleCheckIn: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onPhotoSelected: (android.net.Uri) -> Unit,
    onPhotoClick: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val habit = item.habit
    val isCompleted = item.isCompletedToday
    val photoPath = item.todayCheckIn?.photoPath
    val count = HabitSchedule.currentCount(item.todayCheckIn)
    val target = HabitSchedule.effectiveTarget(habit)

    // Android Photo Picker launcher (compatible down to Android 10 without storage permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    // 完成态走「主题主色」而不是某一支固定绿 ——
    // 完整原因见 AppTheme.kt 顶部的「设计硬约束」注释块（ΔE 由 AppThemeTest 守着）。
    val doneTint = MaterialTheme.colorScheme.primary

    // ⚠️ 底色必须是「合成后不透明」的。网页版 bg-emerald-500/5 在 CSS 里半透明没问题，
    // 但 Compose 里 Card 带 elevation 时，Material3 Surface 是先
    // graphicsLayer(shadowElevation, clip = false) 再背景，阴影会从半透明背景里透出来，
    // 沿着卡片四边糊出一圈发暗的「黑框」——就是完成瞬间出现的那一圈。
    // compositeOver 把 5% 的主色压到不透明的 surface 上，观感与网页版一致，且不再漏阴影。
    val surfaceColor = MaterialTheme.colorScheme.surface
    val animatedBg by animateColorAsState(
        targetValue = if (isCompleted) {
            doneTint.copy(alpha = 0.05f).compositeOver(surfaceColor)
        } else {
            surfaceColor
        },
        label = "cardBg"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBg),
        border = if (isCompleted) {
            // 1dp、30% 主色。
            // 显式构造，别走 CardDefaults.outlinedCardBorder()——它取的是
            // colorScheme.outlineVariant，而本主题只覆盖了 outline，
            // outlineVariant 会落到 Material3 默认值（深色主题下是深灰），颜色不受控。
            BorderStroke(1.dp, doneTint.copy(alpha = 0.3f))
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Habit Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(habitColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconVector(habit.iconName),
                        contentDescription = habit.name,
                        tint = habitColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and Streaks
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        // 网页版完成态给标题加删除线
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 单次计划（TYPE_NONE）没有「连续 / 最长」可言：它全生命周期只有一天排期，
                    // 一旦完成必然算出 连续1天 / 最长1天 —— 对纯提醒性质的日程是纯噪音。
                    // 这里换成一句说明性文案，火苗图标一并去掉（它是连续打卡的语义符号）。
                    if (habit.recurrenceType == HabitSchedule.TYPE_NONE) {
                        Text(
                            text = "单次任务，点完消失~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = UiIcons.LocalFireDepartment,
                                contentDescription = "连续打卡",
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "连续 ${item.currentStreak} 天",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "最长 ${item.longestStreak} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 非每日习惯要说明排期，否则用户会奇怪「它怎么有时不出现」
                    if (habit.recurrenceType != HabitSchedule.TYPE_DAILY) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = scheduleLabel(habit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (habit.reminderTime != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = UiIcons.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "提醒: ${habit.reminderTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 网页版里计数控件和打卡圆圈是**并存**的：
                // 计数器习惯既要点 +1 累加，也要能一键整组标记完成/取消。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (habit.isCounter) {
                        // 计数器习惯：用 -/+ 累计，达到目标次数才算完成。
                        // 早先只有「有/无」两种状态，导致目标为 3 杯的习惯在 App 里永远无法完成。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = count > 0) {
                                        // 减一是「撤销」语义，给最轻的反馈即可，
                                        // 绝不能和 +1 同强度——否则用户分不清自己是加还是减。
                                        Haptics.play(Haptics.Level.LIGHT)
                                        onDecrement()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    UiIcons.Remove,
                                    contentDescription = "减一次",
                                    tint = if (count > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "$count/$target",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) doneTint else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(doneTint)
                                    .clickable {
                                        // count 是点击前的快照。只有「这一点刚好把它顶到目标」才值得强振；
                                        // 已经达标后再点（4/3）不给强振，否则「达标」这个信号会被稀释成每一下都一样。
                                        val justReachedTarget = count < target && count + 1 >= target
                                        Haptics.play(
                                            if (justReachedTarget) Haptics.Level.STRONG
                                            else Haptics.Level.LIGHT
                                        )
                                        onIncrement()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "加一次",
                                        // 底是主题主色，字色必须走 onPrimary：
                                        // 夜间主题的主色是浅灰白，硬编码白色会直接看不见。
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "+1",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 打卡圆圈
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) habitColor else Color.Transparent)
                            .then(
                                if (isCompleted) Modifier
                                else Modifier.border(2.dp, habitColor, CircleShape)
                            )
                            .clickable {
                                // 完成 → 强振；取消 → 轻振。
                                // 两者若同强度，用户反复点这一颗圆点就能无限刷振动，反馈会彻底失效。
                                // 注意：有子任务时此处走的是「整组全选/全不选」，
                                // isCompleted 仍表示「子任务是否已全部勾满」，语义一致。
                                Haptics.play(
                                    if (isCompleted) Haptics.Level.LIGHT else Haptics.Level.STRONG
                                )
                                onToggleCheckIn()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "取消打卡",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(habitColor.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }

            // 细化小计划勾选区（大计划专用）。
            // 放在照片区之上：子任务是「怎么才算完成」的直接依据，比附加凭证更该被先看到。
            if (item.hasSubTasks) {
                Spacer(modifier = Modifier.height(10.dp))
                SubTaskSection(
                    subTasks = habit.subTaskList,
                    completedIds = item.todayCheckIn?.completedSubTaskIdList.orEmpty(),
                    tint = habitColor,
                    onToggle = onToggleSubTask
                )
            }

            // 训练 / 计划内容（网页版：有子任务时不重复展示，避免冗余）
            if (!item.hasSubTasks && !habit.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = UiIcons.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "训练 / 计划内容",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = habit.description!!,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Photo Attachment Section (Available when checked in or checking in)
            if (isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (photoPath != null) {
                        // Thumbnail with view and delete
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(photoPath) }
                            ) {
                                AsyncImage(
                                    model = File(photoPath),
                                    contentDescription = "打卡凭证缩略图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "点击缩略图查看大图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = onRemovePhoto,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除照片",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "可附带照片作为完成凭证",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add / Replace photo button using Android Photo Picker
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = UiIcons.PhotoCamera,
                            contentDescription = "选择照片留证",
                            tint = habitColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 细化小计划勾选区。
 *
 * 默认**展开**（与网页版 HabitCardWeb 的 `isSubTasksExpanded = true` 一致）——
 * 子任务存在的意义就是被逐项勾选，藏起来就失去作用了。
 * 全部勾满才算完成，因此在标题右侧显式给出 "N/M 项"。
 */
@Composable
private fun SubTaskSection(
    subTasks: List<SubTask>,
    completedIds: List<String>,
    tint: Color,
    onToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val completedCount = completedIds.size
    val total = subTasks.size
    val progress = if (total > 0) completedCount.toFloat() / total else 0f
    // 与卡片完成态同一支色（主题主色），不用习惯自己的 tint，
    // 否则「已完成」三个字会和习惯色混在一起分不出层级。
    val doneTint = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "细化小计划",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$completedCount/$total 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) UiIcons.ExpandLess else UiIcons.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            subTasks.forEach { task ->
                val done = completedIds.contains(task.id)
                // 勾上最后一项 = 整份大计划宣告完成，这一刻才配得上强振；
                // 取消勾选属于撤销，只给轻振。
                val toggleWithHaptic = {
                    val completesWholePlan = !done && completedCount + 1 >= total
                    Haptics.play(
                        if (completesWholePlan) Haptics.Level.STRONG else Haptics.Level.LIGHT
                    )
                    onToggle(task.id)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { toggleWithHaptic() }
                        .padding(horizontal = 2.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = { toggleWithHaptic() },
                        colors = CheckboxDefaults.colors(checkedColor = tint),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // 网页版在每个子任务右侧给出「已完成 / 待完成」状态文字
                    Text(
                        text = if (done) "已完成" else "待完成",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) doneTint else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// internal 而不是 private：今日页的「即将到来」也要把 LocalDate 翻成「周三」这类中文星期。
internal val WEEKDAY_CN = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

fun scheduleLabel(habit: Habit): String = when (habit.recurrenceType) {
    HabitSchedule.TYPE_NONE -> if (habit.startDate.isBlank()) "单次" else "仅 ${habit.startDate}"
    HabitSchedule.TYPE_WEEKLY -> {
        val days = HabitSchedule.parseWeeklyDays(habit.weeklyDays)
        if (days.isEmpty()) "每周" else "每周 " + days.sorted().joinToString("、") { WEEKDAY_CN[it] ?: "" }
    }
    HabitSchedule.TYPE_MONTHLY -> {
        val days = HabitSchedule.parseMonthlyDays(habit.monthlyDays)
        if (days.isEmpty()) "每月" else "每月 ${days.sorted().joinToString("、")} 日"
    }
    HabitSchedule.TYPE_INTERVAL -> "每 ${habit.intervalDays.coerceAtLeast(1)} 天"
    else -> "每天"
}
// getIconVector 已上移到 HabitIcons.kt，与图标定义放在一起。
