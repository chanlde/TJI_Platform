package com.tji.device.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.tji.device.di.AppContainer
import com.tji.device.ui.components.LoginWidget
import com.tji.device.data.model.AuthState
import com.tji.device.data.viewmodel.MainViewModel
import com.tji.device.data.viewmodel.LoginViewModel
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.ui.floating.FloatingWindowService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tji.device.ui.AppUiNotifier
import com.tji.device.ui.floating.OverlayPermissionController
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.update.AppUpdateInstallResult
import com.tji.device.update.AppUpdateValidation
import com.tji.device.update.VerifiedAppUpdateInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalMainViewModel = compositionLocalOf<MainViewModel> { error("MainViewModel not provided") }
val LocalLoginViewModel = compositionLocalOf<LoginViewModel> { error("LoginViewModel not provided") }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var lastAppState: AppState = AppState.LOGIN
    private var pendingFloatingWindowStart = false
    private var overlayPermissionGranted by mutableStateOf(false)
    private val verifiedAppUpdateInstaller by lazy { VerifiedAppUpdateInstaller(this) }
    private var appUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overlayPermissionGranted = OverlayPermissionController.isGranted(this)
        val mainViewModel: MainViewModel by viewModels(factoryProducer = {
            AppContainer.mainViewModelFactory
        })
        val loginViewModel: LoginViewModel by viewModels(factoryProducer = {
            AppContainer.mainViewModelFactory
        })

        setContent {
            BucketTheme {
                CompositionLocalProvider(
                    LocalMainViewModel provides mainViewModel,
                    LocalLoginViewModel provides loginViewModel
                ) {
                    Log.d("MainActivity", "setContent -> 渲染 AppNavigation")
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        val loginViewModel = LocalLoginViewModel.current
        val loginUiState by loginViewModel.uiState.collectAsStateWithLifecycle()
        val connectionMode by AppContainer.fireBucketConnectionMode.mode.collectAsStateWithLifecycle()
        val directState by AppContainer.directFireBucketState.state.collectAsStateWithLifecycle()
        var updateBlockedLogin by rememberSaveable { mutableStateOf(false) }
        var directControlRequested by rememberSaveable { mutableStateOf(false) }
        var floatingWindowEnabled by remember { mutableStateOf(true) }
        val appUpdateCandidate by AppUiNotifier.appUpdateCandidate.collectAsStateWithLifecycle()
        val needUpdate = appUpdateCandidate != null
        val appState = resolveAppState(
            authState = loginUiState.authState,
            updateBlockedLogin = updateBlockedLogin,
            directControlRequested = directControlRequested
        )
        val leaveCurrentSession = {
            AppContainer.useCloudFireBucketControl()
            loginViewModel.logout(onComplete = ::restartAfterLogout)
        }

        LaunchedEffect(loginUiState.authState) {
            if (loginUiState.authState is AuthState.LoggedOut) updateBlockedLogin = false
        }

        LaunchedEffect(appState) {
            if (appState == AppState.DIRECT_LINK) {
                AppContainer.useDirectFireBucketControl()
            } else if (AppContainer.fireBucketConnectionMode.current == FireBucketConnectionMode.DIRECT_LINK) {
                AppContainer.leaveDirectFireBucketControl()
            }
        }
        
        // 4G 与直连共用同一个产品悬浮窗服务。
        LaunchedEffect(
            appState,
            floatingWindowEnabled,
            overlayPermissionGranted,
            directState.buckets.isNotEmpty()
        ) {
            lastAppState = appState
            val hasFloatingTarget = appState == AppState.MAIN ||
                (appState == AppState.DIRECT_LINK && directState.buckets.isNotEmpty())
            if (hasFloatingTarget && floatingWindowEnabled) {
                Log.d("MainActivity", "AppNavigation -> 进入 $appState")
                ensureFloatingWindowService()
            } else {
                Log.d("MainActivity", "AppNavigation -> 进入 $appState, 停止悬浮窗")
                stopFloatingWindowService()
            }
        }

        when (appState) {
            AppState.MAIN -> CloudMainScreen(
                connectionMode = connectionMode,
                floatingWindowEnabled = floatingWindowEnabled,
                onFloatingWindowEnabledChange = { floatingWindowEnabled = it },
                leaveCurrentSession = leaveCurrentSession,
                onDirectControlRequested = { directControlRequested = true }
            )
            AppState.LOGIN -> LoginScreen(
                appUpdateCandidate = appUpdateCandidate,
                onLogin = { blockedByUpdate -> updateBlockedLogin = blockedByUpdate },
                onDirectControl = { directControlRequested = true }
            )
            AppState.DIRECT_LINK -> DirectMainScreen(
                floatingWindowEnabled = floatingWindowEnabled,
                onFloatingWindowEnabledChange = { floatingWindowEnabled = it },
                directTargetAvailable = directState.buckets.isNotEmpty(),
                onLeaveDirectControl = { directControlRequested = false }
            )
        }
    }

    @Composable
    private fun CloudMainScreen(
        connectionMode: FireBucketConnectionMode,
        floatingWindowEnabled: Boolean,
        onFloatingWindowEnabledChange: (Boolean) -> Unit,
        leaveCurrentSession: () -> Unit,
        onDirectControlRequested: () -> Unit
    ) {
        MainScreen(
            onBack = leaveCurrentSession,
            isFloatingWindowEnabled = floatingWindowEnabled,
            hasFloatingWindowPermission = overlayPermissionGranted,
            onFloatingWindowEnabledChange = { enabled ->
                onFloatingWindowEnabledChange(enabled)
                if (enabled) {
                    ensureFloatingWindowService()
                } else {
                    stopFloatingWindowService()
                }
            },
            onOpenFloatingWindowPermission = {
                OverlayPermissionController.openSettingsIfNeeded(this)
            },
            connectionMode = connectionMode,
            onConnectionModeChange = { requestedMode ->
                when (requestedMode) {
                    FireBucketConnectionMode.CLOUD -> {
                        AppContainer.useCloudFireBucketControl()
                        AppUiNotifier.showShortMessage("控制链路已切换为 4G")
                    }
                    FireBucketConnectionMode.DIRECT_LINK -> {
                        onDirectControlRequested()
                        AppUiNotifier.showShortMessage("控制链路已切换为数传")
                    }
                }
            }
        )
    }

    @Composable
    private fun DirectMainScreen(
        floatingWindowEnabled: Boolean,
        onFloatingWindowEnabledChange: (Boolean) -> Unit,
        directTargetAvailable: Boolean,
        onLeaveDirectControl: () -> Unit
    ) {
        val leaveDirectControl = {
            onLeaveDirectControl()
            AppContainer.leaveDirectFireBucketControl()
        }
        DirectLinkControlRoute(
            onBack = leaveDirectControl,
            isFloatingWindowEnabled = floatingWindowEnabled,
            hasFloatingWindowPermission = overlayPermissionGranted,
            onFloatingWindowEnabledChange = { enabled ->
                onFloatingWindowEnabledChange(enabled)
                if (enabled && directTargetAvailable) {
                    ensureFloatingWindowService()
                } else {
                    stopFloatingWindowService()
                }
            },
            onOpenFloatingWindowPermission = {
                OverlayPermissionController.openSettingsIfNeeded(this)
            },
            onConnectionModeChange = { requestedMode ->
                if (requestedMode == FireBucketConnectionMode.CLOUD) {
                    leaveDirectControl()
                    AppUiNotifier.showShortMessage("控制链路已切换为 4G")
                }
            }
        )
    }

    /**
     * `recreate()` 会保留 Activity 的 ViewModelStore，无法结束旧账号的产品/音频任务。
     * 登出完成后重建单 Activity 任务栈，确保所有 Activity 作用域 ViewModel 执行 onCleared。
     */
    private fun restartAfterLogout() {
        startActivity(Intent.makeRestartActivityTask(componentName))
    }

    private fun ensureFloatingWindowService() {
        if (!OverlayPermissionController.isGranted(this)) {
            pendingFloatingWindowStart = true
            Log.d("MainActivity", "缺少悬浮窗权限，前往设置")
            OverlayPermissionController.openSettingsIfNeeded(this)
            return
        }
        Log.d("MainActivity", "启动 FloatingWindowService")
        pendingFloatingWindowStart = false
        val intent = Intent(this, FloatingWindowService::class.java)
        startService(intent)
    }

    private fun stopFloatingWindowService() {
        Log.d("MainActivity", "停止 FloatingWindowService")
        val intent = Intent(this, FloatingWindowService::class.java)
        stopService(intent)
        pendingFloatingWindowStart = false
    }

    @Composable
    private fun LoginScreen(
        appUpdateCandidate: AppUpdateValidation.Available?,
        onLogin: (blockedByUpdate: Boolean) -> Unit,
        onDirectControl: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? MainActivity ?: return
        val loginState by LocalLoginViewModel.current.uiState.collectAsStateWithLifecycle()

        LoginWidget(
            isLoading = loginState.isLoading,
            onLogin = {
                if (appUpdateCandidate != null) {
                    onLogin(true)
                    activity.startVerifiedAppUpdate(appUpdateCandidate)
                } else {
                    onLogin(false)
                }
            },
            onDirectControl = onDirectControl,
            context = context
        )
    }

    private fun startVerifiedAppUpdate(candidate: AppUpdateValidation.Available) {
        if (appUpdateJob?.isActive == true) {
            AppUiNotifier.showShortMessage("App 更新正在处理中")
            return
        }
        appUpdateJob = lifecycleScope.launch {
            AppUiNotifier.showShortMessage("正在下载并校验 App 更新")
            when (val result = verifiedAppUpdateInstaller.downloadVerifyAndOpen(candidate)) {
                AppUpdateInstallResult.InstallerOpened -> Unit
                AppUpdateInstallResult.PermissionRequested -> {
                    AppUiNotifier.showShortMessage("请允许安装未知应用后再次点击更新")
                }
                is AppUpdateInstallResult.Failed -> {
                    AppUiNotifier.showShortMessage(result.message)
                }
            }
            appUpdateJob = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFloatingWindowService()
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = OverlayPermissionController.isGranted(this)
        val directTargetAvailable =
            AppContainer.directFireBucketState.state.value.buckets.isNotEmpty()
        if (
            pendingFloatingWindowStart &&
            OverlayPermissionController.isGranted(this) &&
            (lastAppState == AppState.MAIN ||
                (lastAppState == AppState.DIRECT_LINK && directTargetAvailable))
        ) {
            ensureFloatingWindowService()
        }
    }

}

internal enum class AppState {
    LOGIN,
    MAIN,
    DIRECT_LINK
}

internal fun resolveAppState(
    authState: AuthState,
    updateBlockedLogin: Boolean,
    directControlRequested: Boolean = false
): AppState = when {
    directControlRequested -> AppState.DIRECT_LINK
    (authState is AuthState.LoggedIn || authState is AuthState.LoggingOut) && !updateBlockedLogin -> AppState.MAIN
    else -> AppState.LOGIN
}
