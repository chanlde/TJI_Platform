package com.tji.device.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.icon.common.Eye
import com.tji.device.ui.icon.common.EyeOff
import com.tji.device.ui.theme.LoginColors

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String = "",
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,  // 这里传递 isError
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,

        placeholder = {
            Text(
                text = if (isPassword) "请输入密码" else "请输入账号",
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onPasswordVisibilityToggle ?: {}) {
                    Icon(
                        imageVector = if (passwordVisible) Eye else EyeOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF8FAFF),
            unfocusedContainerColor = Color(0xFFF8FAFF),
            errorContainerColor = Color(0xFFFFF0F0),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = LoginColors.OnSurface,
            unfocusedTextColor = LoginColors.OnSurface,
            focusedPlaceholderColor = LoginColors.OnSurfaceVariant.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = LoginColors.OnSurfaceVariant.copy(alpha = 0.7f),
            cursorColor = LoginColors.Primary,
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun InputFieldPreview() {
    var value by remember { mutableStateOf("111111111111111111111111111111111") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        InputField(
            value = value,
            onValueChange = { value = it },
            isError = isError,  // 传递 isError 参数
            errorMessage = "账号不能为空",  // 错误提示信息
        )

        Spacer(modifier = Modifier.height(16.dp))

        InputField(
            value = value,
            onValueChange = { value = it },
            isError = isError,  // 传递 isError 参数
            errorMessage = "密码不能为空",  // 错误提示信息
            isPassword = true,
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible }
        )
    }
}
