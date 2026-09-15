package com.devterminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devterminal.ui.theme.Dimens
import com.devterminal.ui.theme.MonoFamily
import com.devterminal.ui.theme.faint
import com.devterminal.ui.theme.hairline
import com.devterminal.ui.theme.muted

/**
 * 这套基础件的共同目标：**降低视觉噪音**。
 *
 * 具体做法：
 *  - 分割线用 1px 半透明「发丝线」而不是 Material 默认的分隔条；
 *  - 按钮去掉背景块与描边，只留图标 + 文字，靠留白建立节奏；
 *  - 输入框边框减淡，聚焦时才显现，未聚焦时几乎融进背景；
 *  - 对话框统一 26dp 圆角、左对齐标题、按钮组右对齐。
 */

/** 发丝分割线：只在必要时出现，颜色极淡 */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.hairline
) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 分区小标题：字重 + 字间距建立层级，不加色块 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.muted
    )
}

/** 空态 / 占位提示 */
@Composable
fun QuietHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.faint
    )
}

/** 等宽正文（代码、路径、命令一律用它） */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Int = 12,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text,
        modifier = modifier,
        fontFamily = MonoFamily,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

/** 无背景的图标按钮：不抢视觉焦点 */
@Composable
fun QuietIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
    size: Dp = 22.dp
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(size), tint = tint)
    }
}

/** 图标 + 文字的轻量按钮，用于抽屉和对话框底部动作 */
@Composable
fun QuietAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, null, modifier = Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/**
 * 抽屉里的九宫格式动作入口。
 * 图标走描边风格（用 0.08 透明度的容器衬托），文字在下方，比三行挤在一起的文字按钮清爽得多。
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(19.dp), tint = accent)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.faint
        )
    }
}

/** 统一的输入框：边框极淡，圆角偏大 */
@Composable
fun QuietTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    mono: Boolean = true,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onAny = { if (onImeAction != null) onImeAction() }
        ),
        textStyle = TextStyle(
            fontSize = 13.sp,
            fontFamily = if (mono) MonoFamily else androidx.compose.ui.text.font.FontFamily.Default
        ),
        placeholder = placeholder?.let { { Text(it, fontSize = 12.5.sp) } },
        label = label?.let { { Text(it, fontSize = 11.sp) } },
        singleLine = singleLine,
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.hairline,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

/** 圆角卡片容器：用于对话框内的分组 */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(Modifier.padding(Dimens.md), content = content)
    }
}

/**
 * 全应用统一的对话框外观。
 *
 * 标题左对齐、正文留足呼吸、按钮右对齐且不带多余强调，
 * 目的是让用户「读完就关」，而不是被一堆彩色按钮牵着走。
 */
@Composable
fun QuietDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String = "关闭",
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = { Column(content = content) },
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.muted)
            }
        }
    )
}

/** 一行「标签 + 说明 + 开关」的设置项 */
@Composable
fun QuietSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.muted)
        }
        Spacer(Modifier.width(Dimens.sm))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 状态徽标：一个色点 + 文字，比实心药丸轻盈 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
    )
}

/** 行内左右分布的小工具 */
@Composable
fun RowScope.SpacerWeight() {
    Spacer(Modifier.weight(1f))
}
