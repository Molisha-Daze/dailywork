package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.util.CounterPeriodCalculator

/**
 * 独立计数器的新建 / 编辑弹窗。
 *
 * 校验规则与网页版 AddEditCounterModal.handleSubmit 保持一致：
 * 名称必填去空白、步长为正、单位去空白后兜底为「次」、开启上限时上限至少为 1。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCounterDialog(
    initialCounter: StandaloneCounter? = null,
    onDismiss: () -> Unit,
    onSave: (StandaloneCounter) -> Unit
) {
    var name by remember { mutableStateOf(initialCounter?.name ?: "") }
    var currentCountText by remember {
        mutableStateOf((initialCounter?.currentCount ?: 0).toString())
    }
    var hasLimit by remember { mutableStateOf(initialCounter?.hasLimit ?: false) }
    var limitCountText by remember {
        mutableStateOf((initialCounter?.limitCount ?: 10).toString())
    }
    var unit by remember { mutableStateOf(initialCounter?.unit ?: "次") }
    var stepText by remember { mutableStateOf((initialCounter?.step ?: 1).toString()) }
    var colorHex by remember { mutableStateOf(initialCounter?.colorHex ?: "#EF4444") }
    var note by remember { mutableStateOf(initialCounter?.note ?: "") }
    var resetPeriod by remember {
        mutableStateOf(initialCounter?.resetPeriod ?: StandaloneCounter.RESET_NONE)
    }
    // 「每 N 天」的默认值取 7：这是最常被想到的自定义周期，用户改一个数字比从 1 开始往上加省事。
    var resetIntervalDaysText by remember {
        mutableStateOf((initialCounter?.resetIntervalDays ?: 7).toString())
    }

    val parsedIntervalDays = resetIntervalDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    var nameError by remember { mutableStateOf(false) }

    val themeColor = parseColorSafe(colorHex, MaterialTheme.colorScheme.primary)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialCounter == null) "新建独立计数器" else "编辑独立计数器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("计数器名称") },
                    placeholder = { Text("例如：冰箱里的可乐") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入计数器名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = currentCountText,
                        onValueChange = { input ->
                            currentCountText = input.filter { it.isDigit() }.take(9)
                        },
                        label = { Text("当前数值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { input -> unit = input.take(6) },
                        label = { Text("单位") },
                        placeholder = { Text("罐 / 杯 / 次") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 上限设置
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "设置上限 / 容量",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (hasLimit) {
                                    "将显示剩余量与进度条"
                                } else {
                                    "不设上限，可无限累计"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = hasLimit, onCheckedChange = { hasLimit = it })
                    }

                    if (hasLimit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = limitCountText,
                            onValueChange = { input ->
                                limitCountText = input.filter { it.isDigit() }.take(9)
                            },
                            label = { Text("上限数值（${unit.ifBlank { "次" }}）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ResetPeriodSection(
                    resetPeriod = resetPeriod,
                    onResetPeriodChange = { resetPeriod = it },
                    intervalDaysText = resetIntervalDaysText,
                    onIntervalDaysChange = { input ->
                        resetIntervalDaysText = input.filter { it.isDigit() }.take(3)
                    },
                    parsedIntervalDays = parsedIntervalDays
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = stepText,
                    onValueChange = { input ->
                        stepText = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("点击步长") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注说明（可选）") },
                    placeholder = { Text("例如：喝一次点一下") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "主题色",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StandaloneCounter.PRESET_COLORS.forEach { hex ->
                        val swatch = parseColorSafe(hex, MaterialTheme.colorScheme.primary)
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else Modifier
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = true
                                return@Button
                            }
                            val parsedCount = currentCountText.toIntOrNull() ?: 0
                            val parsedLimit = limitCountText.toIntOrNull() ?: 1
                            val base = initialCounter ?: StandaloneCounter(name = "")
                            onSave(
                                base.copy(
                                    name = name.trim(),
                                    currentCount = parsedCount.coerceAtLeast(0),
                                    hasLimit = hasLimit,
                                    limitCount = if (hasLimit) parsedLimit.coerceAtLeast(1) else null,
                                    unit = unit.trim().ifBlank { "次" },
                                    step = (stepText.toIntOrNull() ?: 1).coerceAtLeast(1),
                                    colorHex = colorHex,
                                    note = note.trim().ifBlank { null },
                                    resetPeriod = resetPeriod,
                                    // periodStartDate 不在这里算：锚点要根据「周期配置有没有变」
                                    // 来决定是沿用还是重设，那段逻辑在 Repository 里（纯 IO、可追溯），
                                    // UI 只负责把用户选的周期类型和天数交出去。
                                    resetIntervalDays = parsedIntervalDays,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("保存计数器", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 「自动归零」设置区。
 *
 * 五个选项平铺而不是收进下拉菜单：这是创建计数器时最需要一眼看全的语义
 * ——「它会不会自己清零」直接决定了这个计数器适合记什么 ——
 * 藏进二级菜单会让人漏配，然后困惑「为什么我的库存数第二天变成 0 了」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResetPeriodSection(
    resetPeriod: String,
    onResetPeriodChange: (String) -> Unit,
    intervalDaysText: String,
    onIntervalDaysChange: (String) -> Unit,
    parsedIntervalDays: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Text(
            text = "自动归零",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (resetPeriod == StandaloneCounter.RESET_NONE) {
                "数值一直累计，不会自动清零"
            } else {
                "${CounterPeriodCalculator.displayName(resetPeriod, parsedIntervalDays)}，" +
                    "清零时把上一周期的数值存进历史记录"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StandaloneCounter.RESET_OPTIONS.forEach { option ->
                FilterChip(
                    selected = resetPeriod == option,
                    onClick = { onResetPeriodChange(option) },
                    label = { Text(resetPeriodLabel(option, parsedIntervalDays)) }
                )
            }
        }

        if (resetPeriod == StandaloneCounter.RESET_INTERVAL) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = intervalDaysText,
                onValueChange = onIntervalDaysChange,
                label = { Text("每隔几天归零") },
                placeholder = { Text("例如 3") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * FilterChip 上的短标签。
 *
 * 「每 N 天」把当前天数直接带进标签，这样用户改完输入框能在 chip 上立刻看到结果，
 * 不用去别处确认自己改的有没有生效。
 */
private fun resetPeriodLabel(option: String, intervalDays: Int): String = when (option) {
    StandaloneCounter.RESET_NONE -> "不归零"
    StandaloneCounter.RESET_DAILY -> "每日"
    StandaloneCounter.RESET_WEEKLY -> "每周"
    StandaloneCounter.RESET_MONTHLY -> "每月"
    StandaloneCounter.RESET_INTERVAL -> "每 $intervalDays 天"
    else -> option
}
