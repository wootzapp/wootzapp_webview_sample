package com.wootzapp.webviewtest

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Picture
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.wootz.wootzviewtest.ui.theme.WootzViewTestTheme
import org.chromium.android_webview.AwBrowserContext
import org.chromium.android_webview.AwBrowserProcess
import org.chromium.android_webview.AwConsoleMessage
import org.chromium.android_webview.AwContents
import org.chromium.android_webview.AwContentsClient
import org.chromium.android_webview.AwContentsClientBridge
import org.chromium.android_webview.AwGeolocationPermissions
import org.chromium.android_webview.AwHttpAuthHandler
import org.chromium.android_webview.AwRenderProcess
import org.chromium.android_webview.AwRenderProcessGoneDetail
import org.chromium.android_webview.AwSettings
import org.chromium.android_webview.JsPromptResultReceiver
import org.chromium.android_webview.JsResultReceiver
import org.chromium.android_webview.common.AwResource
import org.chromium.android_webview.permission.AwPermissionRequest
import org.chromium.android_webview.safe_browsing.AwSafeBrowsingResponse
import org.chromium.android_wootzview.WebView
import org.chromium.base.Callback
import org.chromium.components.embedder_support.util.WebResourceResponseInfo
import java.security.Principal

class MainActivity: ComponentActivity() {
    lateinit var browserContext: AwBrowserContext
    private var sInitialized = false

    private val OVERLAY_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WebView
        initializeWebView(this)

        // Set window flags for lockscreen behavior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        setContent {
            WootzViewTestTheme {
                LockScreenContent()
            }
        }

        setupWindowConfiguration()
    }

    private fun setupWindowConfiguration() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use post to ensure window is ready
            window.decorView.post {
                window.insetsController?.apply {
                    try {
                        hide(WindowInsets.Type.statusBars())
                        hide(WindowInsets.Type.navigationBars())
                        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to configure window insets", e)
                    }
                }
            }
        }

        // Request overlay permission if needed
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    // Start service to keep app alive
    private fun startLockScreenService() {
        startService(Intent(this, LockScreenService::class.java))
    }

    @Composable
    private fun LockScreenContent() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                WootzView(
//                    url = "http://google.com/",
                    url = "chrome-extension://allmdcfldidgeghfoioaaiammdlpmnnk/popup.html",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    fun initializeWebView(context: Context) {
        if (!sInitialized) {
            AwResource.setResources(context.resources)
            AwResource.setConfigKeySystemUuidMapping(R.array.config_key_system_uuid_mapping)
            AwBrowserProcess.loadLibrary(null)
            WebView.installDrawFnFunctionTable(false)
            AwBrowserProcess.start()

            browserContext = AwBrowserContext(
                AwBrowserContext.getDefault().nativeBrowserContextPointer
            )
            sInitialized = true
        }
    }
}

class LockScreenService : Service() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val lockIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    context?.startActivity(lockIntent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}

@Composable
fun WootzView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context, true).apply {
            val settings = AwSettings(
                context,
                false,
                false,
                false,
                true,
                false
            )

            // Initialize settings
            settings.apply {
                javaScriptEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                layoutAlgorithm = AwSettings.LAYOUT_ALGORITHM_TEXT_AUTOSIZING
                allowContentAccess = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                domStorageEnabled = true
            }

            val contentsClient = BasicContentsClient()

            // Get browser context from activity
            val browserContext = (context as MainActivity).browserContext

            // Create and initialize AwContents
            val awContents = AwContents(
                browserContext,
                this,  // container view
                context,
                getInternalAccessDelegate(),
                getNativeDrawFunctorFactory(),
                contentsClient,
                settings
            )

            initialize(awContents)

            // Load URL
            awContents.loadUrl(url)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(Intent(context, LockScreenService::class.java))
        }
    }
}

class BasicContentsClient : AwContentsClient() {
    override fun onReceivedTitle(title: String?) {}
    override fun onRequestFocus() {
    }

    override fun getVideoLoadingProgressView(): View {
        return View(null)
    }

    override fun onPageStarted(p0: String?) {
    }

    override fun onPageFinished(p0: String?) {
    }

    override fun onPageCommitVisible(p0: String?) {
    }

    override fun onReceivedError(p0: AwWebResourceRequest?, p1: AwWebResourceError?) {
    }

    override fun onSafeBrowsingHit(
        p0: AwWebResourceRequest?,
        p1: Int,
        p2: Callback<AwSafeBrowsingResponse>?
    ) {
    }

    override fun onReceivedHttpError(p0: AwWebResourceRequest?, p1: WebResourceResponseInfo?) {
    }

    override fun onShowCustomView(p0: View?, p1: CustomViewCallback?) {
    }

    override fun onHideCustomView() {
    }

    override fun getDefaultVideoPoster(): Bitmap {
        return  Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT), 1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun onFindResultReceived(p0: Int, p1: Int, p2: Boolean) {
    }

    override fun onNewPicture(p0: Picture?) {
    }

    override fun onRendererUnresponsive(p0: AwRenderProcess?) {
    }

    override fun onRendererResponsive(p0: AwRenderProcess?) {
    }

    override fun onRenderProcessGone(p0: AwRenderProcessGoneDetail?): Boolean {
        return true
    }

    override fun hasWebViewClient(): Boolean {
        return true
    }

    override fun getVisitedHistory(p0: Callback<Array<String>>?) {
    }

    override fun doUpdateVisitedHistory(p0: String?, p1: Boolean) {
    }

    override fun onProgressChanged(progress: Int) {}
    override fun shouldInterceptRequest(p0: AwWebResourceRequest?): WebResourceResponseInfo? {
        return null

    }

    override fun shouldOverrideKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return false;
    }

    override fun shouldOverrideUrlLoading(p0: AwWebResourceRequest?): Boolean {
        return false;
    }

    override fun onLoadResource(p0: String?) {
    }

    override fun onUnhandledKeyEvent(p0: KeyEvent?) {
    }

    override fun onConsoleMessage(message: AwConsoleMessage?): Boolean {
        Log.d("WootzConsole", message!!.message())
        return true;
    }

    override fun onReceivedHttpAuthRequest(p0: AwHttpAuthHandler?, p1: String?, p2: String?) {
    }

    override fun onReceivedSslError(p0: Callback<Boolean>?, p1: SslError?) {
    }

    override fun onReceivedClientCertRequest(
        p0: AwContentsClientBridge.ClientCertificateRequestCallback?,
        p1: Array<out String>?,
        p2: Array<out Principal>?,
        p3: String?,
        p4: Int
    ) {
    }

    override fun onReceivedLoginRequest(p0: String?, p1: String?, p2: String?) {
    }

    override fun onFormResubmission(p0: Message?, p1: Message?) {
    }

    override fun onDownloadStart(p0: String?, p1: String?, p2: String?, p3: String?, p4: Long) {
    }

    override fun showFileChooser(p0: Callback<Array<String>>?, p1: FileChooserParamsImpl?) {
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: AwGeolocationPermissions.Callback?
    ) {
        callback?.invoke(origin, false, false)
    }

    override fun onGeolocationPermissionsHidePrompt() {
    }

    override fun onPermissionRequest(p0: AwPermissionRequest?) {
    }

    override fun onPermissionRequestCanceled(p0: AwPermissionRequest?) {
    }

    override fun onScaleChangedScaled(p0: Float, p1: Float) {
    }

    override fun handleJsAlert(p0: String?, p1: String?, p2: JsResultReceiver?) {
        Log.d("alert", "alert")
    }

    override fun handleJsBeforeUnload(p0: String?, p1: String?, p2: JsResultReceiver?) {
    }

    override fun handleJsConfirm(p0: String?, p1: String?, p2: JsResultReceiver?) {
    }

    override fun handleJsPrompt(
        p0: String?,
        p1: String?,
        p2: String?,
        p3: JsPromptResultReceiver?
    ) {
    }

    override fun onCreateWindow(p0: Boolean, p1: Boolean): Boolean {
        return true
    }

    override fun onCloseWindow() {
    }

    override fun onReceivedTouchIconUrl(p0: String?, p1: Boolean) {
    }

    override fun onReceivedIcon(p0: Bitmap?) {
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WootzViewTestTheme {
        Greeting("Android")
    }
}