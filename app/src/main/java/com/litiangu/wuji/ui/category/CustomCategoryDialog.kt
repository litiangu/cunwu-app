package com.litiangu.wuji.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.WujiRadius

/**
 * 自定义分类创建弹窗 [Screen 2.3]
 *
 * 居中弹窗，用户输入新分类名称，经校验后回调合法名称。
 * 校验规则（点击确认时触发）：
 * 1. 空值（trim 后为空）→ 提示「请输入分类名称」
 * 2. 超长（>8 字符）→ 提示「分类名称不能超过 8 个字符」
 * 3. 重名（与现有分类 trim+lowercase 归一比对命中）→ 提示「分类已存在，请换一个名称」
 *
 * @param existingNames 现有分类名称列表（用于查重）
 * @param onConfirm 校验通过回调，参数为 trim 后的分类名称（String）。
 *                  调用方负责执行 DAO insert 并拿到自增 id。
 * @param onDismiss 弹窗关闭（取消 / 遮罩点击）
 */
@Composable
fun CustomCategoryDialog(
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 输入框状态：用 TextFieldValue 保留光标位置等
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }

    // 自动聚焦输入框（弹窗打开时拉起键盘）
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            // 遮罩点击/系统返回 → 直接关闭，不触发校验
            focusManager.clearFocus()
            onDismiss()
        },
        // 去掉默认标题区域，完全自绘内容
        title = null,
        text = {
            Column {
                // 弹窗标题
                Text(
                    text = "添加自定义分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 名称输入框
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        // 用户开始修改时清除之前的错误提示
                        if (error != null) error = null
                    },
                    placeholder = {
                        Text("输入分类名称（例如：其他）")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 执行三道校验
                    val trimmed = text.text.trim()
                    error = when {
                        trimmed.isEmpty() -> "请输入分类名称"
                        trimmed.length > 8 -> "分类名称不能超过 8 个字符"
                        existingNames.any { it.trim().equals(trimmed, ignoreCase = true) } ->
                            "分类已存在，请换一个名称"
                        else -> null
                    }

                    // 校验通过 → 回调合法名称给上层，由上层执行 insert
                    if (error == null) {
                        focusManager.clearFocus()
                        onConfirm(trimmed)
                    }
                },
                shape = RoundedCornerShape(WujiRadius.md),          // 12dp 圆角按钮
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandOrange,   // 橙色确认按钮（Screen 2.3 规格）
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                focusManager.clearFocus()
                onDismiss()
            }) {
                Text("取消")
            }
        },
        // 圆角 24dp 对齐设计规范（Screen 2.3 规格）
        shape = RoundedCornerShape(WujiRadius.cardLg),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    )
}
