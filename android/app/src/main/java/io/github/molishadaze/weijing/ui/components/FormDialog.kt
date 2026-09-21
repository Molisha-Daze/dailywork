package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 表单弹窗的设计语言：间距 / 圆角 / 层级 / 按钮，全部定义在这里。
 *
 * 「新建计划」与「新建独立计数器」两个界面此前各写各的（一个 AlertDialog、
 * 一个 Dialog + Card），间距和圆角全靠人肉对齐，改一次飘一次。
 * 现在两边共用同一套组件，风格想漂都漂不动。
 */
internal object FormTokens {
    /** 弹窗圆角。 */
    val DialogShape = RoundedCornerShape(28.dp)

    /** 分组卡片圆角。 */
    val GroupShape = RoundedCornerShape(16.dp)

    /** 输入框圆角。 */
    val FieldShape = RoundedCornerShape(12.dp)

    /** 按钮 / 分段控件圆角。 */
    val ControlShape = RoundedCornerShape(12.dp)

    /** 弹窗内容左右留白。 */
    val OuterPadding = 20.dp

    /** 分组与分组之间。 */
    val SectionGap = 18.dp

    /** 分组标题 → 控件。 */
    val LabelGap = 8.dp

    /** 分组卡片内部元素之间。 */
    val InnerGap = 12.dp
}

/**
 * 两个新建界面共用的弹窗外壳：可滚动的内容区 + 固定在底部的操作区。
 *
 * 底部按钮不跟着内容滚 —— 长表单里「保存」必须永远可见，
 * 否则用户填完十几个字段还得先找按钮在哪（这是原来两个弹窗都存在的问题）。
 */
@Composable
internal fun AppFormDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    accent: Color,
    errorText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // 内容区必须有明确的高度上限：弹窗整体是 wrapContent，不给上限的话
    // 长表单会把 Dialog 撑到屏幕外，底部按钮直接消失。
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FormTokens.DialogShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FormTokens.OuterPadding)
                    .padding(top = FormTokens.OuterPadding, bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                BoxDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        shape = FormTokens.ControlShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text(saveLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 一个「分组标题 + 控件」单元。
 *
 * 所有字段的标签一律放在输入框**上方**，不再混用 M3 的浮动 label：
 * 两个界面之前一个用上方标签、一个用浮动标签，视觉重量完全不同。
 */
@Composable
internal fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FormSectionLabel(title)
        Spacer(modifier = Modifier.height(FormTokens.LabelGap))
        content()
    }
}

@Composable
internal fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * 带强调色底 + 描边的分组卡片（计数器开关、上限、自动归零等）。
 *
 * 底色用 compositeOver 合成出不透明色：半透明底叠在带 elevation 的容器上
 * 会把阴影透出来糊成暗框。
 */
@Composable
internal fun FormGroupCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val background = accent.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)
    val outline = accent.copy(alpha = 0.22f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FormTokens.GroupShape)
            .background(background)
            .border(width = 1.dp, color = outline, shape = FormTokens.GroupShape)
            .padding(14.dp),
        content = content
    )
}

/**
 * 统一外观的输入框。
 *
 * 注意 singleLine 与 minLines/maxLines 互斥，两者必须同时为 1，否则运行时直接抛异常。
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        placeholder = placeholder?.let { hint ->
            {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else maxLines,
        shape = FormTokens.FieldShape
    )
}

/** 分组内部的小字段说明，比 [FormSectionLabel] 轻一档。 */
@Composable
internal fun FormFieldCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 「小标题 + 输入框」，用于分组卡片内部和并排的两个字段。
 *
 * 这些字段大多有默认值（如步长默认 1、单位默认「次」），placeholder 根本不显示，
 * 只靠 placeholder 会让人看不懂这个框是干嘛的。
 */
@Composable
internal fun FormLabeledField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier) {
        FormFieldCaption(caption)
        Spacer(modifier = Modifier.height(FormTokens.LabelGap))
        FormTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            isError = isError,
            keyboardOptions = keyboardOptions
        )
    }
}

/**
 * 会自己伸缩的多行输入框：默认一行高，聚焦时展开成多行输入区；
 * 失焦且无内容收回一行，失焦但有内容则按内容自适应（上限 6 行）。
 */
@Composable
internal fun FormDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    collapsedHint: String,
    expandedHint: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        placeholder = if (focused) expandedHint else collapsedHint,
        singleLine = false,
        minLines = if (focused) 3 else 1,
        maxLines = if (focused || value.isNotBlank()) 6 else 1
    )
}

/** 标题 + 说明 + 开关的标准行，两个界面的开关区共用。 */
@Composable
internal fun FormSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 顶部二选一的分段控件（计划模式）。选中态用各自的强调色区分语义。 */
@Composable
internal fun FormSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: @Composable (Int) -> Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FormTokens.ControlShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) accent(index) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 统一选中态配色的 FilterChip。 */
@Composable
internal fun FormChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            // 裸写 fontSize 会继承 bodyLarge 的 24sp 行高，chip 会被撑高，必须给完整 style
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** 主题色色板，选中项加一圈描边。 */
@Composable
internal fun FormColorPicker(
    colors: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { hex ->
            val isSelected = hex == selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(parseColorSafe(hex, MaterialTheme.colorScheme.primary))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 2.5.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

/** 操作区上方那条分隔线。用 Box 而不是 HorizontalDivider，免得踩 M3 版本差异。 */
@Composable
private fun BoxDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
