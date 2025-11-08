package de.henosch.bibelvers

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class DatenschutzActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/datenschutz.html")
        }
        setContentView(webView)
    }
}
