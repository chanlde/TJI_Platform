package com.tji.device.ui.components

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.collection.emptyLongSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.R
import com.tji.device.data.model.Login
import com.tji.device.ui.theme.LoginColors
import com.tji.device.ui.theme.TjiPrimarySoft
import com.tji.device.ui.theme.TjiSurfaceSoft
import com.tji.device.util.SecurePrefs
import com.tji.device.util.ToastUtils
import com.tji.device.ui.main.LocalMainViewModel

@Composable
private fun RememberMeAndForgotPassword(
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    onForgotPassword: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onRememberMeChange(!rememberMe) }
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = onRememberMeChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = LoginColors.Primary
                )
            )
            Text(
                text = "记住我",
                fontSize = 12.sp,
                color = LoginColors.TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Text(
            text = "忘记密码？",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LoginColors.Primary,
            modifier = Modifier.clickable { onForgotPassword() }
        )
    }
}

@Composable
fun LoginWidget(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onLogin: (Login) -> Unit = {},
    onDeveloperModeClick: () -> Unit,
    onForgotPassword: () -> Unit = {},
    context: Context
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    val isPreview = LocalInspectionMode.current
    val mainViewModel = if (isPreview) null else LocalMainViewModel.current

    val sharedPreferences = remember(context) { SecurePrefs.userPreferences(context) }
    val savedAccount = sharedPreferences.getString("account", "")
    val savedPassword = sharedPreferences.getString("password", "")
    val savedRememberMe = sharedPreferences.getBoolean("rememberMe", false)

 //   account = "HydroLink_V2-70037A73"
   // password = "admin"


    LaunchedEffect(Unit) {
        if (savedRememberMe) {
            account = savedAccount ?: ""
            password = savedPassword ?: ""
            rememberMe = savedRememberMe
        }
    }

    fun handleLogin() {
        if (isPreview) {
            onLogin(Login(account, password, rememberMe))
            return
        }

        mainViewModel?.login(account, password, rememberMe) { loginSuccess, errorMsg ->
            if (loginSuccess) {
                // 保存账号和密码
                if (rememberMe) {
                    sharedPreferences.edit().apply {
                        putString("account", account)
                        putString("password", password)
                        putBoolean("rememberMe", true)
                        apply()
                    }
                } else {
                    sharedPreferences.edit().clear().apply()
                }
                onLogin(Login(account, password, rememberMe))
            } else {
                if (errorMsg != null) {
                    if (errorMsg.contains("账号")) {
                        accountError = errorMsg
                        passwordError = ""
                    } else if (errorMsg.contains("密码")) {
                        passwordError = errorMsg
                        accountError = ""
                    } else {
                        accountError = errorMsg
                        passwordError = errorMsg
                    }
                } else {
                    accountError = "登录失败，请重试"
                    passwordError = "登录失败，请重试"
                }
                ToastUtils.showToast("登录失败: $errorMsg")
            }
        }
    }

    LoginBackground(modifier = modifier) {
        LoginLayout(
            account = account,
            password = password,
            accountError = accountError,
            passwordError = passwordError,
            passwordVisible = passwordVisible,
            rememberMe = rememberMe,
            isLoading = isLoading,
            onAccountChange = {
                account = it
                accountError = ""
            },
            onPasswordChange = {
                password = it
                passwordError = ""
            },
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            onRememberMeChange = { rememberMe = it },
            onLogin = { handleLogin() },
            onForgotPassword = onForgotPassword,
            onDeveloperModeClick = {
                onDeveloperModeClick()
            }
        )
    }
}
//
//@Composable
//fun LoginWidget(
//    modifier: Modifier = Modifier,
//    isLoading: Boolean = false,
//    onLogin: (Login) -> Unit = {},
//    onDeveloperModeClick: () -> Unit,
//    onForgotPassword: () -> Unit = {},
//    context: Context
//) {
//    var account by remember { mutableStateOf("1234567") }
//    var password by remember { mutableStateOf("1234567") }
//    var rememberMe by remember { mutableStateOf(false) }
//    var passwordVisible by remember { mutableStateOf(false) }
//    var accountError by remember { mutableStateOf("") }
//    var passwordError by remember { mutableStateOf("") }
//    var showAccountDropdown by remember { mutableStateOf(false) }
//    val mainViewModel = LocalMainViewModel.current
//
//    // 获取 SharedPreferences
//    val sharedPreferences = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
//    val savedAccount = sharedPreferences.getString("account", "")
//    val savedPassword = sharedPreferences.getString("password", "")
//    val savedRememberMe = sharedPreferences.getBoolean("rememberMe", false)
//
//    LaunchedEffect(Unit) {
//        if (savedRememberMe) {
//            account = savedAccount ?: ""
//            password = savedPassword ?: ""
//            rememberMe = savedRememberMe
//        }
//    }
//
//    val testAccounts = listOf("12345678", "HydroLink_V3-7003BBF5", "TestAccount_2")
//
//    fun handleLogin() {
//        mainViewModel.login(account, password, rememberMe) { loginSuccess, errorMsg ->
//            if (loginSuccess) {
//
//                if (rememberMe) {
//                    sharedPreferences.edit().apply {
//                        putString("account", account)
//                        putString("password", password)
//                        putBoolean("rememberMe", true)
//                        apply()
//                    }
//                }
//                onLogin(Login(account, password, rememberMe))
//                updateMqttConfig(username = account)
//            } else {
//                if (errorMsg != null) {
//                    if (errorMsg.contains("账号")) {
//                        accountError = errorMsg
//                        passwordError = ""
//                    } else if (errorMsg.contains("密码")) {
//                        passwordError = errorMsg
//                        accountError = ""
//                    } else {
//                        accountError = errorMsg
//                        passwordError = errorMsg
//                    }
//                } else {
//                    accountError = "登录失败，请重试"
//                    passwordError = "登录失败，请重试"
//                }
//                ToastUtils.showToast("登录失败: $errorMsg")
//            }
//        }
//    }
//
//    LoginBackground(modifier = modifier) {
//        LoginCard {
//            Column {
//                 //账号选择下拉框
//                Box(modifier = Modifier.fillMaxWidth()) {
//                    OutlinedTextField(
//                        value = account,
//                        onValueChange = {
//                            account = it
//                            accountError = ""
//                        },
//                        label = { Text("账号") },
//                        isError = accountError.isNotEmpty(),
//                        readOnly = true, // 设置为只读，只能通过下拉选择
//                        trailingIcon = {
//                            IconButton(onClick = { showAccountDropdown = !showAccountDropdown }) {
//                                Icon(
//                                    imageVector = if (showAccountDropdown)
//                                        Icons.Default.ArrowDropDown
//                                    else
//                                        Icons.Default.ArrowDropDown,
//                                    contentDescription = "选择测试账号"
//                                )
//                            }
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//
//                    // 下拉菜单
//                    DropdownMenu(
//                        expanded = showAccountDropdown,
//                        onDismissRequest = { showAccountDropdown = false }
//                    ) {
//                        testAccounts.forEach { testAccount ->
//                            DropdownMenuItem(
//                                text = { Text(testAccount) },
//                                onClick = {
//                                    account = testAccount
//                                    showAccountDropdown = false
//                                }
//                            )
//                        }
//                    }
//                }
//
//                if (accountError.isNotEmpty()) {
//                    Text(
//                        text = accountError,
//                        color = MaterialTheme.colorScheme.error,
//                        style = MaterialTheme.typography.bodySmall,
//                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
//                    )
//                }
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 其他原有的 UI 组件
//                LoginLayout(
//                    account = account,
//                    password = password,
//                    accountError = accountError,
//                    passwordError = passwordError,
//                    passwordVisible = passwordVisible,
//                    rememberMe = rememberMe,
//                    isLoading = isLoading,
//                    onAccountChange = {
//                        account = it
//                        accountError = ""
//                    },
//                    onPasswordChange = {
//                        password = it
//                        passwordError = ""
//                    },
//                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
//                    onRememberMeChange = { rememberMe = it },
//                    onLogin = { handleLogin() },
//                    onForgotPassword = onForgotPassword,
//                    onDeveloperModeClick = onDeveloperModeClick
//                )
//            }
//        }
//    }
//}

@Composable
private fun LoginBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LoginColors.Background,
                        TjiSurfaceSoft,
                        TjiPrimarySoft
                    )
                )
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_login_top),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )
        Image(
            painter = painterResource(id = R.drawable.bg_login_bottom),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
        content()
    }
}

@Composable
private fun LoginCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp.dp

    Card(
        modifier = Modifier
            .then(
                if (isLandscape) {
                    Modifier
                        .widthIn(max = minOf(screenWidth * 0.48f, 520.dp))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                }
            )
            .then(modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = LoginColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        content()
    }
}

@Composable
private fun BoxScope.LoginLayout(
    account: String,
    password: String,
    accountError: String,
    passwordError: String,
    passwordVisible: Boolean,
    rememberMe: Boolean,
    isLoading: Boolean,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onDeveloperModeClick: () -> Unit,
    onForgotPassword: () -> Unit
 ) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoginHero(
                modifier = Modifier.weight(1f),
                compact = true
            )
            LoginCard {
                LoginFormContent(
                    account = account,
                    password = password,
                    accountError = accountError,
                    passwordError = passwordError,
                    passwordVisible = passwordVisible,
                    rememberMe = rememberMe,
                    isLoading = isLoading,
                    onAccountChange = onAccountChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onRememberMeChange = onRememberMeChange,
                    onLogin = onLogin,
                    onForgotPassword = onForgotPassword,
                    isLandscape = true,
                    onDeveloperModeClick = onDeveloperModeClick
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginHero(
                modifier = Modifier.fillMaxWidth(),
                compact = false
            )
            LoginCard {
                LoginFormContent(
                    account = account,
                    password = password,
                    accountError = accountError,
                    passwordError = passwordError,
                    passwordVisible = passwordVisible,
                    rememberMe = rememberMe,
                    isLoading = isLoading,
                    onAccountChange = onAccountChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onRememberMeChange = onRememberMeChange,
                    onLogin = onLogin,
                    onForgotPassword = onForgotPassword,
                    isLandscape = false,
                    onDeveloperModeClick = onDeveloperModeClick
                )
            }
        }
    }
}

@Composable
private fun LoginFormContent(
    account: String,
    password: String,
    accountError: String,
    passwordError: String,
    passwordVisible: Boolean,
    rememberMe: Boolean,
    isLoading: Boolean,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onDeveloperModeClick: () -> Unit,
    onForgotPassword: () -> Unit,
    isLandscape: Boolean
) {
    Column(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
    LoginForm(
        account = account,
        password = password,
        accountError = accountError,
        passwordError = passwordError,
        passwordVisible = passwordVisible,
        rememberMe = rememberMe,
        isLoading = isLoading,
        onAccountChange = onAccountChange,
        onPasswordChange = onPasswordChange,
        onPasswordVisibilityToggle = onPasswordVisibilityToggle,
        onRememberMeChange = onRememberMeChange,
        onLogin = onLogin,
        onForgotPassword = onForgotPassword,
        isLandscape = isLandscape,
        onDeveloperModeClick = onDeveloperModeClick
    )
    }
}

// Logo 或标题 - 横屏时缩小
@Composable
fun LogoOrTitle(isLandscape: Boolean) {
    Text(
        text = "登录",
        style = if (isLandscape)
            MaterialTheme.typography.headlineMedium
        else
            MaterialTheme.typography.headlineLarge,
        color = LoginColors.Primary,
        modifier = Modifier.padding(
            bottom = if (isLandscape) 8.dp else 16.dp
        )
    )
}

// 登录按钮
@Composable
fun LoginButton(
    isLoading: Boolean,
    onLogin: () -> Unit,
    isLandscape: Boolean
) {
    val needUpdate by ToastUtils.needUpdate.collectAsState()

    Button(
        onClick = onLogin,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLandscape) 50.dp else 54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LoginColors.Primary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = if (needUpdate) "需更新到最新版本" else "登录",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun LoginForm(
    account: String,
    password: String,
    accountError: String,
    passwordError: String,
    passwordVisible: Boolean,
    rememberMe: Boolean,
    isLoading: Boolean,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onDeveloperModeClick: () -> Unit,
    onForgotPassword: () -> Unit,
    isLandscape: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 16.dp)
    ) {
        InputField(
            value = account,
            onValueChange = onAccountChange,
            isError = accountError.isNotEmpty(),
            errorMessage = accountError
        )

        InputField(
            value = password,
            onValueChange = onPasswordChange,
            isError = passwordError.isNotEmpty(),
            errorMessage = passwordError,
            isPassword = true,
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = onPasswordVisibilityToggle
        )

        RememberMeAndForgotPassword(rememberMe, onRememberMeChange, onForgotPassword)

        LoginButton(isLoading, onLogin, isLandscape)

        TextButton(onClick = onDeveloperModeClick) {
            Text(
                text = "Wifi模式",
                style = MaterialTheme.typography.bodyMedium,
                color = LoginColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoginHero(
    modifier: Modifier = Modifier,
    compact: Boolean
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(id = R.drawable.img_login_hero),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 180.dp else 156.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable

fun LoginWidgetPreview() {

    LoginWidget(
        isLoading = false,
        onLogin = { /* Handle login */ },
        onDeveloperModeClick = { /* Handle developer mode click */ },
        context = LocalContext.current
    )
}
