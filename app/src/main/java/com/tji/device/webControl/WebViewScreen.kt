package com.tji.device.webControl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tji.device.wifi.WifiData

@Composable
fun WebViewScreen(
    url: String,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // 文件选择回调变量
    var filePathCallback by remember { mutableStateOf<ValueCallback<Uri>?>(null) }
    var filesPathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // 文件选择启动器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK) {
            // 处理单文件回调 (Android 4.1-5.0)
            filePathCallback?.let { callback ->
                val uri = data?.data ?: Uri.EMPTY
                callback.onReceiveValue(uri)
                filePathCallback = null
            }

            // 处理多文件回调 (Android 5.0+)
            filesPathCallback?.let { callback ->
                val uris = mutableListOf<Uri>()
                data?.let { intent ->
                    // 处理单个文件
                    intent.dataString?.let { dataString ->
                        uris.add(Uri.parse(dataString))
                    }
                    // 处理多个文件
                    intent.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    }
                }
                callback.onReceiveValue(if (uris.isEmpty()) emptyArray() else uris.toTypedArray())
                filesPathCallback = null
            }
        } else {
            // 取消选择时也要回调
            filePathCallback?.onReceiveValue(Uri.EMPTY)
            filesPathCallback?.onReceiveValue(emptyArray())
            filePathCallback = null
            filesPathCallback = null
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("WebView", "Loading started: $url")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Loading finished: $url")
                }
            }

            // 关键：设置WebChromeClient来处理文件选择
            webChromeClient = object : WebChromeClient() {
                // For Android 4.1 - 5.0
                fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {
                    filePathCallback = uploadMsg
                    openFileSelector(acceptType, filePickerLauncher)
                }

                // For Android 5.0+
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    filesPathCallback = filePathCallback
                    val acceptTypes = fileChooserParams.acceptTypes
                    val acceptType = if (acceptTypes.isNotEmpty()) acceptTypes[0] else "*/*"
                    openFileSelector(acceptType, filePickerLauncher)
                    return true
                }
            }
        }
    }

    Column {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            view.loadUrl(url)
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
    launcher: ActivityResultLauncher<Intent>
) {
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
        // 允许多选
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
    launcher.launch(Intent.createChooser(intent, "请选择文件"))
}