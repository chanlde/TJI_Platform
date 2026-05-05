package com.tji.device.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tji.device.di.AppContainer
import com.tji.device.ui.components.LoginWidget
import com.tji.device.data.viewmodel.MainViewModel
import com.tji.device.service.MqttService
import com.tji.device.ui.floating.FloatingWindowService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.ContextCompat
import com.tji.network.utils.NetWorkUtils.Companion.updateUrl
import com.tji.device.util.ToastUtils
import com.tji.device.util.ToastUtils.linkUrl
import com.tji.device.util.ToastUtils.switchUrl
import com.tji.device.util.OverlayPermissionHelper
import com.tji.device.webControl.WebViewScreen
import com.tji.device.wifi.WifiData
import kotlinx.coroutines.delay

val LocalMainViewModel = compositionLocalOf<MainViewModel> { error("MainViewModel not provided") }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var wifiData: WifiData
    private var isMqttServiceStarted = false  // ✅ 添加标志
    private var lastAppState: AppState = AppState.LOGIN
    private var pendingFloatingWindowStart = false
    private var overlayPermissionGranted by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "位置权限已授权")
        } else {
            Log.d("MainActivity", "位置权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiData = WifiData(this)
        overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(this)
        requestLocationPermission()

        val mainViewModel: MainViewModel by viewModels(factoryProducer = {
            AppContainer.mainViewModelFactory
        })

        if (!isMqttServiceStarted) {
            startMqttService()
        }

        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMainViewModel provides mainViewModel) {
                    Log.d("MainActivity", "setContent -> 渲染 AppNavigation")
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        var appState by remember { mutableStateOf(AppState.LOGIN) }
        var floatingWindowEnabled by remember { mutableStateOf(true) }
        val needUpdate by ToastUtils.needUpdate.collectAsState()
        val mainViewModel = LocalMainViewModel.current
        
        // ✅ 监听状态变化，只在进入 MAIN 时启动一次
        LaunchedEffect(appState, floatingWindowEnabled, overlayPermissionGranted) {
            lastAppState = appState
            if (appState == AppState.MAIN && floatingWindowEnabled) {
                Log.d("MainActivity", "AppNavigation -> 进入 MAIN")
                ensureFloatingWindowService()
            } else {
                Log.d("MainActivity", "AppNavigation -> 进入 $appState, 停止悬浮窗")
                stopFloatingWindowService()
            }
        }

        when (appState) {
            AppState.DEVELOPER -> DeveloperScreen(onBack = { appState = AppState.LOGIN })
            AppState.MAIN -> MainScreen(
                onBack = { appState = AppState.LOGIN },
                isFloatingWindowEnabled = floatingWindowEnabled,
                hasFloatingWindowPermission = overlayPermissionGranted,
                onFloatingWindowEnabledChange = { enabled ->
                    floatingWindowEnabled = enabled
                    if (enabled) {
                        ensureFloatingWindowService()
                    } else {
                        stopFloatingWindowService()
                    }
                },
                onOpenFloatingWindowPermission = {
                    OverlayPermissionHelper.requestOverlayPermission(this)
                }
            )
            AppState.LOGIN -> LoginScreen(
                needUpdate = needUpdate,
                onLogin = { appState = AppState.MAIN },
                onDeveloperModeClick = { appState = AppState.DEVELOPER }
            )
        }
    }

    private fun startMqttService() {
        if (isMqttServiceStarted) {
            Log.w("MainActivity", "MqttService 已启动，跳过")
            return
        }

        Log.d("MainActivity", "启动 MqttService")
        val serviceIntent = Intent(this, MqttService::class.java)
        startService(serviceIntent)
        isMqttServiceStarted = true
    }

    private fun stopMqttService() {
        if (!isMqttServiceStarted) {
            return
        }

        Log.d("MainActivity", "停止 MqttService")
        val serviceIntent = Intent(this, MqttService::class.java)
        stopService(serviceIntent)
        isMqttServiceStarted = false
    }

    private fun ensureFloatingWindowService() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            pendingFloatingWindowStart = true
            Log.d("MainActivity", "缺少悬浮窗权限，前往设置")
            OverlayPermissionHelper.requestOverlayPermission(this)
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
    private fun DeveloperScreen(onBack: () -> Unit) {
        val context = LocalContext.current

        val url = try {
            if (hasLocationPermission(context)) {
                val wifiInfo = wifiData.getCurrentWifiInfo()

                if ((wifiInfo.ssid)!!.contains("HydroLink", ignoreCase = true)) {
                    linkUrl
                } else {
                    switchUrl
                }
            } else {
                switchUrl
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "权限被拒绝: ${e.message}")
            switchUrl
        }
        WebViewScreen(url = url, onBack = onBack)
    }

    @Composable
    private fun LoginScreen(
        needUpdate: Boolean,
        onLogin: () -> Unit,
        onDeveloperModeClick: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? MainActivity ?: return

        LoginWidget(
            onLogin = {
                if (needUpdate) {
                    activity.openUrl(updateUrl)
                } else {
                    onLogin()
                }
            },
            onDeveloperModeClick = onDeveloperModeClick,
            context = context
        )
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 确保启动新的任务栈
        startActivity(intent)
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MainActivity", "已有位置权限")
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val serviceIntent = Intent(this, MqttService::class.java)
        stopService(serviceIntent)
        Log.d("MainActivity", "MqttService 在 Activity 销毁时停止")
        stopFloatingWindowService()
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(this)
        if (
            pendingFloatingWindowStart &&
            OverlayPermissionHelper.canDrawOverlays(this) &&
            lastAppState == AppState.MAIN
        ) {
            ensureFloatingWindowService()
        }
    }

    private enum class AppState {
        LOGIN,
        MAIN,
        DEVELOPER
    }
}
