package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.util.CounterPeriodCalculator

/**
 * 独立计数器的新建 / 编辑弹窗。
 *
 * 外壳、间距、圆角、字段样式全部复用 [AppFormDialog] 与 FormTokens，
 * 与「新建计划」弹窗共用同一套设计语言，改一处两边一起生效。
 *
 * 校验规则与网页版 AddEditCounterModal.handleSubmit 保持一致：
 * 名称必填去空白、步长为正、单位去空白后兜底为「次」、开启上限时上限至少为 1。
 */
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

    AppFormDialog(
        onDismiss = onDismiss,
        onSave = {
            if (name.isBlank()) {
                nameError = true
                return@AppFormDialog
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
        saveLabel = "保存计数器",
        accent = themeColor,
        errorText = if (nameError) "请输入计数器名称" else null
    ) {
        FormSection("计数器名称 *") {
            FormTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) nameError = false
                },
                placeholder = "例如：冰箱里的可乐",
                isError = nameError
            )
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormSection("起始数值与单位") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormLabeledField(
                    caption = "当前数值",
                    value = currentCountText,
                    onValueChange = { input ->
                        currentCountText = input.filter { it.isDigit() }.take(9)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                FormLabeledField(
                    caption = "单位",
                    value = unit,
                    onValueChange = { input -> unit = input.take(6) },
                    placeholder = "罐 / 杯 / 次",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormGroupCard(accent = themeColor) {
            FormSwitchRow(
                title = "设置上限 / 容量",
                subtitle = if (hasLimit) "将显示剩余量与进度条" else "不设上限，可无限累计",
                checked = hasLimit,
                onCheckedChange = { hasLimit = it }
            )

            if (hasLimit) {
                Spacer(modifier = Modifier.height(FormTokens.InnerGap))
                FormLabeledField(
                    caption = "上限数值（${unit.ifBlank { "次" }}）",
                    value = limitCountText,
                    onValueChange = { input ->
                        limitCountText = input.filter { it.isDigit() }.take(9)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormGroupCard(accent = themeColor) {
            ResetPeriodSection(
                resetPeriod = resetPeriod,
                onResetPeriodChange = { resetPeriod = it },
                intervalDaysText = resetIntervalDaysText,
                onIntervalDaysChange = { input ->
                    resetIntervalDaysText = input.filter { it.isDigit() }.take(3)
                },
                parsedIntervalDays = parsedIntervalDays
            )
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormSection("点击步长") {
            FormTextField(
                value = stepText,
                onValueChange = { input ->
                    stepText = input.filter { it.isDigit() }.take(3)
                },
                placeholder = "每次点击增加的数量，如 1",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormSection("备注说明") {
            FormDescriptionField(
                value = note,
                onValueChange = { note = it },
                collapsedHint = "用法 / 备忘，点击展开输入",
                expandedHint = "例如：\n喝一次点一下\n每周补货后手动减掉"
            )
        }

        Spacer(modifier = Modifier.height(FormTokens.SectionGap))

        FormSection("主题色") {
            FormColorPicker(
                colors = StandaloneCounter.PRESET_COLORS,
                selected = colorHex,
                onSelect = { colorHex = it }
            )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "自动归零",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
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

        Spacer(modifier = Modifier.height(FormTokens.InnerGap))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StandaloneCounter.RESET_OPTIONS.forEach { option ->
                FormChip(
                    selected = resetPeriod == option,
                    onClick = { onResetPeriodChange(option) },
                    label = resetPeriodLabel(option)
                )
            }
        }

        if (resetPeriod == StandaloneCounter.RESET_INTERVAL) {
            Spacer(modifier = Modifier.height(FormTokens.InnerGap))
            FormLabeledField(
                caption = "每隔几天归零",
                value = intervalDaysText,
                onValueChange = onIntervalDaysChange,
                placeholder = "例如 3",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

/**
 * FilterChip 上的短标签。
 *
 * 「每N天」这一格固定不带具体数字 —— 它是**周期类型的名字**，不是当前取值。
 * 原来把天数带进标签（输入 1 就显示「每 1 天」），于是旁边那个「每日」chip
 * 和它成了同一件事的两种写法，用户还得猜这两个有什么区别。
 * 具体天数交给下方的输入框展示，与「新建计划」弹窗的「每N天」保持一套说法。
 */
private fun resetPeriodLabel(option: String): String = when (option) {
    StandaloneCounter.RESET_NONE -> "不归零"
    StandaloneCounter.RESET_DAILY -> "每日"
    StandaloneCounter.RESET_WEEKLY -> "每周"
    StandaloneCounter.RESET_MONTHLY -> "每月"
    StandaloneCounter.RESET_INTERVAL -> "每N天"
    else -> option
}
