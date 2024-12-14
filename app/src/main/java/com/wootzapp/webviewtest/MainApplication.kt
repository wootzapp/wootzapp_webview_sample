package com.wootzapp.webviewtest

import android.app.Application
import android.content.Context
import android.util.Log
import org.chromium.android_webview.AwLocaleConfig
import org.chromium.base.CommandLine
import org.chromium.base.ContextUtils
import org.chromium.base.PathUtils
import org.chromium.ui.base.ResourceBundle

class MainApplication : Application() {
    override fun attachBaseContext(context: Context) {
        Log.d("a", "1")
        super.attachBaseContext(context)
        ContextUtils.initApplicationContext(this)
        PathUtils.setPrivateDataDirectorySuffix("webview", "WebView")
//        CommandLine.initFromFile("/data/local/tmp/webview-command-line")
        CommandLine.init(arrayOf("--v", "2"))
        Log.d("a", AwLocaleConfig.getWebViewSupportedPakLocales().contentToString())
        ResourceBundle.setAvailablePakLocales(AwLocaleConfig.getWebViewSupportedPakLocales())
//        AwBrowserProcess.setWebViewPackageName("org.chromium.android_webview")

    }
}