package com.tji.device.webControl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.tji.device.wifi.WifiData

@Composable
fun WebViewScreen(
    url: String,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val allowedInitialUrl = remember(url) {
        require(DeveloperWebAccessPolicy.isAllowed(url)) {
            "Developer WebView URL is outside the LAN allowlist"
        }
        url
    }

    // 文件选择回调变量
    var filePathCallback by remember { mutableStateOf<ValueCallback<Uri>?>(null) }
    var filesPathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val cancelPendingFileSelection = {
        filePathCallback?.onReceiveValue(null)
        filesPathCallback?.onReceiveValue(null)
        filePathCallback = null
        filesPathCallback = null
    }

    // 文件选择启动器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val pendingSingle = filePathCallback
        val pendingMultiple = filesPathCallback
        filePathCallback = null
        filesPathCallback = null
        if (result.resultCode == Activity.RESULT_OK) {
            // 处理单文件回调 (Android 4.1-5.0)
            pendingSingle?.onReceiveValue(data?.data)

            // 处理多文件回调 (Android 5.0+)
            pendingMultiple?.let { callback ->
                val uris = mutableListOf<Uri>()
                data?.let { intent ->
                    // 处理单个文件
                    intent.dataString?.let { dataString ->
                        uris.add(dataString.toUri())
                    }
                    // 处理多个文件
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    }
                }
                callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
            }
        } else {
            pendingSingle?.onReceiveValue(null)
            pendingMultiple?.onReceiveValue(null)
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = !DeveloperWebAccessPolicy.isAllowed(request.url.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !DeveloperWebAccessPolicy.isAllowed(url)

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    if (!DeveloperWebAccessPolicy.isAllowed(url)) {
                        view.stopLoading()
                        Log.w("WebView", "Blocked navigation outside LAN allowlist")
                        return
                    }
                    super.onPageStarted(view, url, favicon)
                    Log.d("WebView", "Loading started: host=${DeveloperWebAccessPolicy.hostForLog(url)}")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Loading finished: host=${DeveloperWebAccessPolicy.hostForLog(url)}")
                }
            }

            // 关键：设置WebChromeClient来处理文件选择
            webChromeClient = object : WebChromeClient() {
                // For Android 4.1 - 5.0
                fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {
                    cancelPendingFileSelection()
                    filePathCallback = uploadMsg
                    if (!openFileSelector(acceptType, allowMultiple = false, filePickerLauncher)) {
                        cancelPendingFileSelection()
                    }
                }

                // For Android 5.0+
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    cancelPendingFileSelection()
                    filesPathCallback = filePathCallback
                    val acceptTypes = fileChooserParams.acceptTypes
                    val acceptType = if (acceptTypes.isNotEmpty()) acceptTypes[0] else "*/*"
                    val launched = openFileSelector(
                        acceptType = acceptType,
                        allowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                        launcher = filePickerLauncher
                    )
                    if (!launched) {
                        cancelPendingFileSelection()
                    }
                    return launched
                }
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            cancelPendingFileSelection()
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    Column {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            if (view.url != allowedInitialUrl) {
                view.loadUrl(allowedInitialUrl)
            }
        }

        BackHandler {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                onBack?.invoke()
            }
        }
    }
}

/**
 * 根据文件类型打开文件选择器
 */
private fun openFileSelector(
    acceptType: String,
    allowMultiple: Boolean,
    launcher: ActivityResultLauncher<Intent>
): Boolean {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = when {
            acceptType.startsWith("image/") -> "image/*"
            acceptType.startsWith("video/") -> "video/*"
            acceptType.startsWith("audio/") -> "audio/*"
            acceptType == "camera/*" -> {
                // 如果需要相机功能，可以在这里处理
                // 这里简化为选择图片
                "image/*"
            }
            else -> "*/*"
        }
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
    }
    return runCatching {
        launcher.launch(Intent.createChooser(intent, "请选择文件"))
        true
    }.getOrDefault(false)
}
