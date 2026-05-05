package com.tji.device.ui.floating

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt
class FloatingWindowService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val binder = LocalBinder()
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var rootContainer: FrameLayout

    private val viewModelStoreHolder = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreHolder  // 实现 ViewModelStoreOwner

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry


    private val _test= MutableStateFlow(false)
    val test: StateFlow<Boolean> = _test



    private val layoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 320
        }
    }

    private val sizeState = MutableStateFlow(WindowSize(width = 140, height = 140))

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    // 手动通过 ViewModelProvider 获取 ViewModel
    private val viewModel: FloatingWindowViewModel by lazy {
        ViewModelProvider(
            this, FloatingWindowViewModelFactory(
                productRuntimeRegistry = AppContainer.productRuntimeRegistry,
                floatingQuickControlFor = AppContainer::floatingQuickControlFor
            )
        ).get(FloatingWindowViewModel::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingWindowService onCreate")
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupComposeView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingWindowService onStartCommand")
        super.onStartCommand(intent, flags, startId) // ⚠️ 必须调用
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "FloatingWindowService onDestroy")
        runCatching {
            windowManager.removeView(rootContainer)
        }.onFailure {
            Log.w(TAG, "移除悬浮窗失败: ${it.message}")
        }
        viewModelStoreHolder.clear()
        super.onDestroy()
    }

    private fun setupComposeView() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

            setContent {
                var isExpanded by remember { mutableStateOf(false) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(isExpanded, uiState.activeProductType) {
                    val mode = if (isExpanded) FloatingWindowMode.EXPANDED else FloatingWindowMode.ICON
                    updateSizeForMode(mode, uiState.activeProductType)
                }

                // ✅ 直接渲染，不加 Compose 动画
                FloatingWindowContent(
                    uiState = uiState,
                    isExpanded = isExpanded,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onMinimize = {
                        isExpanded = false
                        viewModel.minimize()
                    },
                    onClose = { stopSelf() },
                    onSwitchQuickToggle = viewModel::toggleSwitch,
                    onMove = { dx, dy -> adjustPosition(dx, dy) },
                    onResize = { _, _ -> }
                )

            }
        }

        rootContainer = FrameLayout(this).apply {
            addView(composeView, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
        }

        windowManager.addView(rootContainer, layoutParams)
        updateSizeForMode(FloatingWindowMode.ICON, ProductType.FireBucket)
    }

    private fun updateSizeForMode(mode: FloatingWindowMode, productType: ProductType) {
        val startAt = System.currentTimeMillis()
        val targetSize = when (mode) {
            FloatingWindowMode.ICON -> WindowSize(dpToPx(50), dpToPx(50))
            FloatingWindowMode.EXPANDED -> when (productType) {
                ProductType.FireBucket -> WindowSize(width = dpToPx(360), height = dpToPx(520))
                ProductType.SolarClean -> WindowSize(width = dpToPx(340), height = WRAP_CONTENT)
            }
        }

        // 保存旧的中心点
        val oldWidth = if (layoutParams.width > 0) layoutParams.width else targetSize.width
        val oldHeight = if (layoutParams.height > 0) layoutParams.height else 150
        val oldCenterX = layoutParams.x + oldWidth / 2
        val oldCenterY = layoutParams.y + oldHeight / 2

        // 更新大小
        layoutParams.width = targetSize.width
        layoutParams.height = targetSize.height

        // 调整位置，让中心点保持不变
        layoutParams.x = oldCenterX - targetSize.width / 2
        if (targetSize.height > 0) {
            layoutParams.y = oldCenterY - targetSize.height / 2
        }

        windowManager.updateViewLayout(rootContainer, layoutParams)
        Log.d(
            TAG,
            "update floating window size: mode=$mode product=$productType " +
                    "width=${layoutParams.width} height=${layoutParams.height} cost=${System.currentTimeMillis() - startAt}ms"
        )
    }

    private fun adjustPosition(dx: Float, dy: Float) {
        Log.v(TAG, "adjustPosition dx=$dx dy=$dy")
        layoutParams.x += dx.roundToInt()
        layoutParams.y += dy.roundToInt()
        windowManager.updateViewLayout(rootContainer, layoutParams)
    }

    private fun adjustSize(dx: Float, dy: Float) {
        Log.v(TAG, "adjustSize dx=$dx dy=$dy")
        sizeState.update { size ->
            val newWidth = (size.width + dx).roundToInt().coerceIn(dpToPx(MIN_WIDTH_DP), dpToPx(MAX_WIDTH_DP))
            val newHeight = (size.height + dy).roundToInt().coerceIn(dpToPx(MIN_HEIGHT_DP), dpToPx(MAX_HEIGHT_DP))
            WindowSize(newWidth, newHeight)
        }
        val size = sizeState.value
        layoutParams.width = size.width
        layoutParams.height = size.height
        windowManager.updateViewLayout(rootContainer, layoutParams)
    }

    inner class LocalBinder : Binder() {
        fun getService(): FloatingWindowService = this@FloatingWindowService
    }

    private data class WindowSize(
        val width: Int,
        val height: Int
    )

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val MIN_WIDTH_DP = 240
        private const val MIN_HEIGHT_DP = 220
        private const val MAX_WIDTH_DP = 800
        private const val MAX_HEIGHT_DP = 1200
    }
}
