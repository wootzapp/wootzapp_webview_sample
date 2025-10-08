package com.wootz.wootzviewtest

import android.webkit.WebSettings
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import org.chromium.base.PathService
import org.chromium.base.TraceEvent
import org.chromium.components.embedder_support.util.WebResourceResponseInfo
import java.io.IOException
import java.security.Principal


class MainActivity : ComponentActivity() {
    lateinit var browserContext: AwBrowserContext
    private var sInitialized = false

    private val OVERLAY_PERMISSION_REQUEST_CODE = 100
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.i(TAG, "=== MainActivity onCreate started ===")

            super.onCreate(savedInstanceState)

            // Setup comprehensive crash logging
            setupCrashLogging()

            // Log environment details safely
            try {
                Log.i(TAG, "App version: ${applicationContext.packageManager.getPackageInfo(packageName, 0).versionName}")
                Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                Log.i(TAG, "Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Log.i(TAG, "Available Memory: ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB")
                Log.i(TAG, "Max Memory: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging environment details", e)
                logCrashDetails("onCreate_EnvironmentLogging", e)
            }

            try {
                // Initialize WebView with enhanced error handling
                Log.d(TAG, "Initializing WebView...")
                initializeWebView(this)
                Log.d(TAG, "WebView initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: WebView initialization failed", e)
                logCrashDetails("onCreate_WebViewInit", e)
                throw e
            }

            try {
                // Set window flags for lockscreen behavior
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    Log.d(TAG, "Setting up lockscreen behavior for API ${Build.VERSION.SDK_INT}")
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)

                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (keyguardManager != null) {
                        keyguardManager.requestDismissKeyguard(this, null)
                        Log.d(TAG, "Keyguard dismiss requested")
                    } else {
                        Log.w(TAG, "KeyguardManager is null")
                    }
                } else {
                    Log.d(TAG, "Lockscreen setup not needed for API ${Build.VERSION.SDK_INT}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up lockscreen behavior", e)
                logCrashDetails("onCreate_LockscreenSetup", e)
                // Don't throw here, continue with app startup
            }

            try {
                Log.d(TAG, "Starting LockScreenService...")
                startLockScreenService()
                Log.d(TAG, "LockScreenService started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting LockScreenService", e)
                logCrashDetails("onCreate_LockScreenService", e)
                // Don't throw here, continue with app startup
            }

            try {
                Log.d(TAG, "Setting up UI content...")
                setContent {
                    WootzViewTestTheme {
                        LockScreenContent()
                    }
                }
                Log.d(TAG, "UI content setup completed")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: UI content setup failed", e)
                logCrashDetails("onCreate_UIContent", e)
                throw e
            }

            try {
                Log.d(TAG, "Setting up window configuration...")
                setupWindowConfiguration()
                Log.d(TAG, "Window configuration completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up window configuration", e)
                logCrashDetails("onCreate_WindowConfig", e)
                // Don't throw here, app can still function
            }

            Log.i(TAG, "=== MainActivity onCreate completed successfully ===")

        } catch (e: Exception) {
            Log.e(TAG, "=== CRITICAL FAILURE in onCreate ===", e)
            logCrashDetails("onCreate_Critical", e)
            throw e // Re-throw to trigger crash reporting
        }
    }


    private fun setupCrashLogging() {
        Log.i(TAG, "Setting up crash logging...")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "=== UNCAUGHT EXCEPTION DETECTED ===")
            Log.e(TAG, "Thread: ${thread.name}")
            Log.e(TAG, "Exception: ${exception.javaClass.simpleName}")
            Log.e(TAG, "Message: ${exception.message}")
            Log.e(TAG, "Stack trace:", exception)

            logCrashDetails("UncaughtException", exception)

            // Call the default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    fun logCrashDetails(context: String, exception: Throwable) {
        try {
            Log.e(TAG, "=== COMPREHENSIVE CRASH ANALYSIS ===")
            Log.e(TAG, "Crash Context: $context")
            Log.e(TAG, "Timestamp: ${System.currentTimeMillis()}")
            Log.e(TAG, "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")

            // System state
            Log.e(TAG, "App Lifecycle State: ${lifecycle.currentState}")
            val runtime = Runtime.getRuntime()
            val freeMemory = runtime.freeMemory() / (1024 * 1024)
            val totalMemory = runtime.totalMemory() / (1024 * 1024)
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            val usedMemory = totalMemory - freeMemory
            val memoryUsagePercent = (usedMemory * 100) / maxMemory

            Log.e(TAG, "Memory Analysis:")
            Log.e(TAG, "- Available Memory: ${freeMemory} MB")
            Log.e(TAG, "- Used Memory: ${usedMemory} MB")
            Log.e(TAG, "- Total Memory: ${totalMemory} MB")
            Log.e(TAG, "- Max Memory: ${maxMemory} MB")
            Log.e(TAG, "- Memory Usage: ${memoryUsagePercent}%")

            if (memoryUsagePercent > 90) {
                Log.e(TAG, "*** CRITICAL MEMORY USAGE - LIKELY CAUSE OF CRASH ***")
            } else if (memoryUsagePercent > 75) {
                Log.e(TAG, "*** HIGH MEMORY USAGE - POSSIBLE CRASH FACTOR ***")
            }

            // Thread information
            Log.e(TAG, "Thread Analysis:")
            Log.e(TAG, "- Current Thread: ${Thread.currentThread().name}")
            Log.e(TAG, "- Thread ID: ${Thread.currentThread().id}")
            Log.e(TAG, "- Thread Priority: ${Thread.currentThread().priority}")
            Log.e(TAG, "- Thread State: ${Thread.currentThread().state}")
            Log.e(TAG, "- Active Threads: ${Thread.activeCount()}")

            // Exception analysis
            Log.e(TAG, "Exception Analysis:")
            Log.e(TAG, "- Root Exception: ${exception.javaClass.simpleName}")
            Log.e(TAG, "- Root Message: ${exception.message}")

            // Categorize exception type for crash analysis
            val exceptionType = exception.javaClass.simpleName
            when {
                exceptionType.contains("OutOfMemory") -> {
                    Log.e(TAG, "*** MEMORY EXHAUSTION CRASH ***")
                    Log.e(TAG, "App ran out of memory - classic Android crash cause")
                }
                exceptionType.contains("NullPointer") -> {
                    Log.e(TAG, "*** NULL POINTER DEREFERENCE ***")
                    Log.e(TAG, "Accessing null object - common in WebView crashes")
                }
                exceptionType.contains("IllegalState") -> {
                    Log.e(TAG, "*** ILLEGAL STATE EXCEPTION ***")
                    Log.e(TAG, "Component in invalid state - lifecycle issue")
                }
                exceptionType.contains("Security") -> {
                    Log.e(TAG, "*** SECURITY EXCEPTION ***")
                    Log.e(TAG, "Permission or security constraint violation")
                }
                exceptionType.contains("Runtime") -> {
                    Log.e(TAG, "*** RUNTIME EXCEPTION ***")
                    Log.e(TAG, "General runtime failure - investigate stack trace")
                }
                exception.message?.contains("chromium", true) == true ||
                        exception.message?.contains("webview", true) == true -> {
                    Log.e(TAG, "*** WEBVIEW/CHROMIUM RELATED CRASH ***")
                    Log.e(TAG, "Native WebView component failure - critical")
                }
            }

            // WebView state analysis
            Log.e(TAG, "WebView State Analysis:")
            Log.e(TAG, "- WebView Initialized: $sInitialized")

            if (::browserContext.isInitialized) {
                Log.e(TAG, "- Browser Context: Initialized")
                try {
                    Log.e(TAG, "- Browser Context Class: ${browserContext.javaClass.simpleName}")
                } catch (e: Exception) {
                    Log.e(TAG, "- Browser Context: CORRUPTED (${e.message})")
                }
            } else {
                Log.e(TAG, "- Browser Context: NOT INITIALIZED")
                if (context.contains("WebView", true) || context.contains("WootzView", true)) {
                    Log.e(TAG, "*** WebView operation attempted without proper initialization ***")
                }
            }

            // Log the complete exception chain
            Log.e(TAG, "Exception Chain:")
            var currentException: Throwable? = exception
            var level = 0
            while (currentException != null && level < 10) {
                Log.e(TAG, "Level $level:")
                Log.e(TAG, "  Type: ${currentException.javaClass.simpleName}")
                Log.e(TAG, "  Message: ${currentException.message}")

                // Log first few stack trace elements for crash location
                val stackElements = currentException.stackTrace
                if (stackElements.isNotEmpty()) {
                    Log.e(TAG, "  Location: ${stackElements[0].className}.${stackElements[0].methodName}(${stackElements[0].fileName}:${stackElements[0].lineNumber})")

                    // Look for our code in stack trace
                    stackElements.take(5).forEach { element ->
                        if (element.className.contains("wootz", true) ||
                            element.className.contains("MainActivity", true)) {
                            Log.e(TAG, "  *** OUR CODE: ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber}) ***")
                        }
                    }
                }

                currentException = currentException.cause
                level++
            }

            // Context-specific analysis
            Log.e(TAG, "Context-Specific Analysis:")
            when {
                context.contains("onCreate") -> {
                    Log.e(TAG, "- Activity creation crash - app may not start")
                    Log.e(TAG, "- Check resource loading and initialization order")
                }
                context.contains("WebView") || context.contains("WootzView") -> {
                    Log.e(TAG, "- WebView related crash - native component issue")
                    Log.e(TAG, "- May be related to Chrome extension or page loading")
                }
                context.contains("Extension") -> {
                    Log.e(TAG, "- Chrome extension related crash")
                    Log.e(TAG, "- Check extension content and API usage")
                }
                context.contains("UncaughtException") -> {
                    Log.e(TAG, "- Unhandled exception - app will terminate")
                    Log.e(TAG, "- This is the final crash before app death")
                }
            }

            Log.e(TAG, "=== END COMPREHENSIVE CRASH ANALYSIS ===")

        } catch (e: Exception) {
            Log.e(TAG, "Critical error in crash logging itself", e)
            Log.e(TAG, "Original crash context: $context")
            Log.e(TAG, "Original exception: ${exception.message}")
        }
    }

    override fun onStart() {
        Log.d(TAG, "MainActivity onStart()")
        try {
            super.onStart()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart", e)
            logCrashDetails("onStart", e)
            throw e
        }
    }

    override fun onResume() {
        Log.d(TAG, "MainActivity onResume()")
        try {
            super.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
            logCrashDetails("onResume", e)
            throw e
        }
    }

    override fun onPause() {
        Log.d(TAG, "MainActivity onPause()")
        try {
            super.onPause()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause", e)
            logCrashDetails("onPause", e)
            throw e
        }
    }

    override fun onStop() {
        Log.d(TAG, "MainActivity onStop()")
        try {
            super.onStop()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStop", e)
            logCrashDetails("onStop", e)
            throw e
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy()")
        try {
            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
            logCrashDetails("onDestroy", e)
            throw e
        }
    }

    private fun setupWindowConfiguration() {
        Log.d(TAG, "Setting up window configuration...")
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use post to ensure window is ready
                window.decorView.post {
                    window.insetsController?.apply {
                        try {
                            Log.d(TAG, "Hiding system bars...")
                            hide(WindowInsets.Type.statusBars())
                            hide(WindowInsets.Type.navigationBars())
                            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            Log.d(TAG, "System bars hidden successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to configure window insets", e)
                            logCrashDetails("WindowInsets", e)
                        }
                    }
                }
            }

            // Request overlay permission if needed
            if (!hasOverlayPermission()) {
                Log.d(TAG, "Requesting overlay permission...")
                requestOverlayPermission()
            } else {
                Log.d(TAG, "Overlay permission already granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupWindowConfiguration", e)
            logCrashDetails("setupWindowConfiguration", e)
            throw e
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val result = Settings.canDrawOverlays(this)
                Log.d(TAG, "Overlay permission check result: $result")
                result
            } else {
                Log.d(TAG, "Overlay permission not required for API < 23")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking overlay permission", e)
            logCrashDetails("hasOverlayPermission", e)
            false
        }
    }

    private fun requestOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "Starting overlay permission request...")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting overlay permission", e)
            logCrashDetails("requestOverlayPermission", e)
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
//                    url = "https://www.google.com",
                    url = "chrome-extension://nnkbcnehconbbnccjhicpjmpliggdcpe/popup.html",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    fun initializeWebView(context: Context) {
        Log.d(TAG, "Initializing WebView with context: ${context.javaClass.simpleName}")

        try {
            if (!sInitialized) {
                Log.d(TAG, "First time WebView initialization...")

                // Validate context
                if (context.resources == null) {
                    throw IllegalStateException("Context resources are null")
                }

                try {
                    Log.d(TAG, "Setting up AwResource...")
                    AwResource.setResources(context.resources)
                    AwResource.setConfigKeySystemUuidMapping(R.array.config_key_system_uuid_mapping)
                    Log.d(TAG, "AwResource setup completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up AwResource", e)
                    logCrashDetails("initializeWebView_AwResource", e)
                    throw e
                }

                try {
                    Log.d(TAG, "Loading WebView library...")
                    AwBrowserProcess.loadLibrary(null)
                    Log.d(TAG, "WebView library loaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading WebView library", e)
                    logCrashDetails("initializeWebView_LoadLibrary", e)
                    throw e
                }

                try {
                    Log.d(TAG, "Installing draw function table...")
                    WebView.installDrawFnFunctionTable(false)
                    Log.d(TAG, "Draw function table installed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error installing draw function table", e)
                    logCrashDetails("initializeWebView_DrawFnTable", e)
                    throw e
                }

                try {
                    Log.d(TAG, "Starting browser process...")
                    AwBrowserProcess.start()
                    Log.d(TAG, "Browser process started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting browser process", e)
                    logCrashDetails("initializeWebView_BrowserProcess", e)
                    throw e
                }

                try {
                    Log.d(TAG, "Creating browser context...")
                    val defaultContext = AwBrowserContext.getDefault()
                    if (defaultContext == null) {
                        throw IllegalStateException("Default browser context is null")
                    }

                    browserContext = AwBrowserContext(defaultContext.nativeBrowserContextPointer)
                    Log.d(TAG, "Browser context created successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating browser context", e)
                    logCrashDetails("initializeWebView_BrowserContext", e)
                    throw e
                }

                sInitialized = true
                Log.i(TAG, "WebView initialization completed successfully")
            } else {
                Log.d(TAG, "WebView already initialized, skipping...")

                // Verify browserContext is still valid
                if (!::browserContext.isInitialized) {
                    Log.w(TAG, "Browser context not initialized despite sInitialized=true")
                    throw IllegalStateException("Browser context is not initialized")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during WebView initialization", e)
            logCrashDetails("initializeWebView", e)
            sInitialized = false // Reset flag on failure
            throw e
        }
    }
}

class LockScreenService : Service() {
    private val TAG = "LockScreenService"

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                Log.d(TAG, "Screen event received: ${intent?.action}")
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.i(TAG, "Screen turned off, starting MainActivity...")
                        val lockIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        context?.startActivity(lockIntent)
                        Log.d(TAG, "MainActivity launch intent sent")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling screen event", e)
            }
        }
    }

    override fun onCreate() {
        Log.d(TAG, "LockScreenService onCreate()")
        try {
            super.onCreate()
            Log.d(TAG, "Registering screen receiver...")
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            Log.d(TAG, "Screen receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "LockScreenService onDestroy()")
        try {
            super.onDestroy()
            Log.d(TAG, "Unregistering screen receiver...")
            unregisterReceiver(screenReceiver)
            Log.d(TAG, "Screen receiver unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with flags: $flags, startId: $startId")
        return START_STICKY
    }
}

@Composable
fun WootzView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val TAG = "WootzView"

    var webView by remember { mutableStateOf<WebView?>(null) }

    Log.d(TAG, "Creating WootzView for URL: $url")

    LaunchedEffect(Unit) {
        try {
            // Validate inputs
            if (url.isBlank()) {
                throw IllegalArgumentException("URL cannot be blank")
            }

            if (context !is MainActivity) {
                throw IllegalStateException("Context must be MainActivity, got: ${context.javaClass.simpleName}")
            }

            Log.d(TAG, "Creating WebView instance safely...")

            // Create WebView on main thread with proper error handling
            val newWebView = WebView(context, true).apply {
                try {
                    Log.d(TAG, "Configuring WebView settings...")

                    val settings = try {
                        AwSettings(
                            context,
                            false,
                            false,
                            false,
                            true,
                            false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating AwSettings", e)
                        context.logCrashDetails("WootzView_AwSettings", e)
                        throw e
                    }

                    // Initialize settings with error handling
                    try {
                        settings.apply {
                            Log.d(TAG, "Applying WebView settings...")
                            javaScriptEnabled = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            layoutAlgorithm = AwSettings.LAYOUT_ALGORITHM_TEXT_AUTOSIZING
                            allowContentAccess = true
                            allowFileAccess = false // Security: disable file access
                            allowFileAccessFromFileURLs = false // Security: prevent file access
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportMultipleWindows(true)
                            allowContentAccess = true
                            allowUniversalAccessFromFileURLs = false // Keep security
                            allowFileAccessFromFileURLs = false     // Keep security
                            settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE)
                        }
                        Log.d(TAG, "WebView settings applied successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying WebView settings", e)
                        context.logCrashDetails("WootzView_SettingsApply", e)
                        throw e
                    }

                    Log.d(TAG, "Creating safe contents client...")
                    val contentsClient = try {
                        SafeContentsClient()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating contents client", e)
                        context.logCrashDetails("WootzView_ContentsClient", e)
                        throw e
                    }

                    // Get browser context with validation
                    Log.d(TAG, "Getting browser context...")
                    val browserContext = try {
                        context.browserContext
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting browser context", e)
                        context.logCrashDetails("WootzView_GetBrowserContext", e)
                        throw e
                    }

                    Log.d(TAG, "Using browser context: ${browserContext.javaClass.simpleName}")

                    // Create and initialize AwContents with error handling
                    Log.d(TAG, "Creating AwContents...")
                    val awContents = try {
                        AwContents(
                            browserContext,
                            this,  // container view
                            context,
                            getInternalAccessDelegate(),
                            getNativeDrawFunctorFactory(),
                            contentsClient,
                            settings
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating AwContents", e)
                        context.logCrashDetails("WootzView_AwContents", e)
                        throw e
                    }

                    Log.d(TAG, "Initializing WebView with AwContents...")
                    try {
                        initialize(awContents)
                        Log.d(TAG, "WebView initialization completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing WebView", e)
                        context.logCrashDetails("WootzView_Initialize", e)
                        throw e
                    }

                    // Load URL with error handling
                    Log.i(TAG, "Loading URL: $url")
                    try {
                        // Post the URL loading to prevent race conditions
                        post {
                            try {
                                awContents.loadUrl(url)
                                Log.d(TAG, "URL load initiated successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading URL in post: $url", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error posting URL load: $url", e)
                        context.logCrashDetails("WootzView_LoadURL", e)
                    }

                    Log.d(TAG, "WebView creation completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring WebView", e)
                    context.logCrashDetails("WootzView_Configuration", e)
                }
            }

            webView = newWebView

        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating WebView", e)
            if (context is MainActivity) {
                context.logCrashDetails("WootzView_Creation", e)
            }
        }
    }

    // Simple UI - just show the WebView when ready
    if (webView != null) {
        AndroidView(
            factory = {
                Log.d(TAG, "AndroidView factory called")
                webView!!
            },
            modifier = modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        Log.d(TAG, "WootzView DisposableEffect started")
        onDispose {
            try {
                Log.d(TAG, "Disposing WootzView...")
                Log.d(TAG, "WootzView disposed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing WootzView", e)
                if (context is MainActivity) {
                    context.logCrashDetails("WootzView_Dispose", e)
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(TAG, "Boot event received: ${intent.action}")
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.i(TAG, "Device boot completed, starting LockScreenService...")
                context.startService(Intent(context, LockScreenService::class.java))
                Log.d(TAG, "LockScreenService start intent sent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling boot event", e)
        }
    }
}

class SafeContentsClient : AwContentsClient() {
    private val TAG = "SafeContentsClient"

    override fun onReceivedTitle(title: String?) {
        Log.d(TAG, "Page title received: $title")
    }

    override fun onRequestFocus() {
        Log.d(TAG, "Focus requested")
    }

    override fun getVideoLoadingProgressView(): View {
        Log.d(TAG, "Video loading progress view requested")
        return View(null)
    }

    override fun onPageStarted(url: String?) {
        Log.i(TAG, "Page started loading: $url")
    }

    override fun onPageFinished(url: String?) {
        Log.i(TAG, "Page finished loading: $url")
    }

    override fun onPageCommitVisible(url: String?) {
        Log.d(TAG, "Page commit visible: $url")
    }

    override fun onReceivedError(request: AwWebResourceRequest?, error: AwWebResourceError?) {
        Log.e(TAG, "Page error: ${error?.description} for ${request?.url}")
    }

    override fun onRenderProcessGone(detail: AwRenderProcessGoneDetail?): Boolean {
        Log.e(TAG, "Render process crashed - handling gracefully")
        return true // Handle crash gracefully instead of propagating
    }

    override fun onConsoleMessage(message: AwConsoleMessage?): Boolean {
        try {
            message?.let {
                val level = it.messageLevel()?.toString() ?: "UNKNOWN"
                val messageText = it.message() ?: ""

                // Filter out problematic console messages that might cause crashes
                if (messageText.contains("hello textinput", ignoreCase = true) ||
                    messageText.contains("sigsegv", ignoreCase = true) ||
                    messageText.contains("segmentation fault", ignoreCase = true)) {
                    Log.w(TAG, "Filtering potentially problematic console message: $messageText")
                    return true // Suppress this message
                }

                if (level.contains("ERROR", ignoreCase = true)) {
                    Log.e(TAG, "Console Error: $messageText")
                } else {
                    Log.d(TAG, "Console ($level): $messageText")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling console message", e)
            return true // Don't let logging errors crash the app
        }
    }

    override fun onSafeBrowsingHit(
        request: AwWebResourceRequest?,
        threatType: Int,
        callback: Callback<AwSafeBrowsingResponse>?
    ) {
        Log.w(TAG, "Safe browsing hit: ${request?.url}, threat: $threatType")
    }

    override fun onReceivedHttpError(request: AwWebResourceRequest?, response: WebResourceResponseInfo?) {
        Log.e(TAG, "HTTP error: ${response?.statusCode} for ${request?.url}")
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        Log.d(TAG, "Custom view shown")
    }

    override fun onHideCustomView() {
        Log.d(TAG, "Custom view hidden")
    }

    override fun getDefaultVideoPoster(): Bitmap {
        Log.d(TAG, "Default video poster requested")
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun onRendererUnresponsive(process: AwRenderProcess?) {
        Log.w(TAG, "Renderer unresponsive")
    }

    override fun onRendererResponsive(process: AwRenderProcess?) {
        Log.i(TAG, "Renderer responsive")
    }

    override fun hasWebViewClient(): Boolean {
        return true
    }

    override fun getVisitedHistory(callback: Callback<Array<String>>?) {
        Log.d(TAG, "Visited history requested")
    }

    override fun doUpdateVisitedHistory(url: String?, isReload: Boolean) {
        Log.d(TAG, "Update visited history: $url")
    }

    override fun onProgressChanged(progress: Int) {
        if (progress % 25 == 0) { // Log every 25% to reduce noise
            Log.d(TAG, "Loading progress: $progress%")
        }
    }

    override fun shouldInterceptRequest(request: AwWebResourceRequest?): WebResourceResponseInfo? {
        return null
    }

    override fun shouldOverrideKeyEvent(event: KeyEvent?): Boolean {
        return false
    }

    override fun shouldOverrideUrlLoading(request: AwWebResourceRequest?): Boolean {
        Log.d(TAG, "URL loading: ${request?.url}")
        return false
    }

    override fun onLoadResource(url: String?) {
        // Log.v(TAG, "Loading resource: $url") // Too verbose
    }

    override fun onUnhandledKeyEvent(event: KeyEvent?) {
        // Log.d(TAG, "Unhandled key event: ${event?.keyCode}")
    }

    override fun onReceivedHttpAuthRequest(handler: AwHttpAuthHandler?, host: String?, realm: String?) {
        Log.d(TAG, "HTTP auth request for host: $host")
    }

    override fun onReceivedSslError(callback: Callback<Boolean>?, error: SslError?) {
        Log.w(TAG, "SSL error: ${error?.primaryError}")
        callback?.onResult(false) // Don't ignore SSL errors by default
    }

    override fun handleJsAlert(url: String?, message: String?, result: JsResultReceiver?) {
        Log.d(TAG, "JavaScript alert: $message")
        result?.confirm()
    }

    override fun handleJsBeforeUnload(url: String?, message: String?, result: JsResultReceiver?) {
        Log.d(TAG, "JavaScript beforeunload: $message")
        result?.confirm()
    }

    override fun handleJsConfirm(url: String?, message: String?, result: JsResultReceiver?) {
        Log.d(TAG, "JavaScript confirm: $message")
        result?.confirm()
    }

    override fun handleJsPrompt(
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResultReceiver?
    ) {
        Log.d(TAG, "JavaScript prompt: $message")
        result?.confirm(defaultValue ?: "")
    }

    override fun onCreateWindow(isDialog: Boolean, isUserGesture: Boolean): Boolean {
        Log.d(TAG, "Create window request")
        return false // Don't create new windows to prevent crashes
    }

    override fun onCloseWindow() {
        Log.d(TAG, "Close window request")
    }

    // Add other required methods with safe defaults
    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        Log.d(TAG, "Find result: $activeMatchOrdinal/$numberOfMatches")
    }

    override fun onNewPicture(picture: Picture?) {
        // Log.v(TAG, "New picture received") // Too verbose
    }

    override fun onReceivedTouchIconUrl(url: String?, precomposed: Boolean) {
        Log.d(TAG, "Touch icon URL: $url")
    }

    override fun onReceivedIcon(icon: Bitmap?) {
        Log.d(TAG, "Icon received: ${icon?.width}x${icon?.height}")
    }

    override fun onDownloadStart(url: String?, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long) {
        Log.i(TAG, "Download started: $url")
    }

    override fun showFileChooser(callback: Callback<Array<String>>?, params: FileChooserParamsImpl?) {
        Log.d(TAG, "File chooser requested")
        callback?.onResult(arrayOf()) // Return empty array to prevent crashes
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: AwGeolocationPermissions.Callback?
    ) {
        Log.d(TAG, "Geolocation permission request from: $origin")
        callback?.invoke(origin, false, false)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        Log.d(TAG, "Geolocation permission prompt hidden")
    }

    override fun onPermissionRequest(request: AwPermissionRequest?) {
        Log.d(TAG, "Permission request: ${request?.javaClass?.simpleName}")
        request?.resources?.let { resources ->
            // Grant all requested resources (camera, mic, etc.)
            request.grant()
            Log.d(TAG, "Granted resources: ${resources}")
        }
    }

    override fun onPermissionRequestCanceled(request: AwPermissionRequest?) {
        Log.d(TAG, "Permission request canceled")
    }

    override fun onScaleChangedScaled(oldScale: Float, newScale: Float) {
        // Log.v(TAG, "Scale changed: $oldScale -> $newScale") // Too verbose
    }

    override fun onReceivedClientCertRequest(
        callback: AwContentsClientBridge.ClientCertificateRequestCallback?,
        keyTypes: Array<out String>?,
        principals: Array<out Principal>?,
        host: String?,
        port: Int
    ) {
        Log.d(TAG, "Client certificate request for $host:$port")
        callback?.ignore() // Ignore cert requests to prevent hangs
    }

    override fun onReceivedLoginRequest(realm: String?, account: String?, args: String?) {
        Log.d(TAG, "Login request for realm: $realm")
    }

    override fun onFormResubmission(dontResend: Message?, resend: Message?) {
        Log.d(TAG, "Form resubmission request")
        dontResend?.sendToTarget() // Don't resend by default
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