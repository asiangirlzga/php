package com.example.phpserverapp

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var phpServer: PhpServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        phpServer = PhpServer(applicationContext)

        CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            val ready = withContext(Dispatchers.IO) {
                phpServer.start()
                phpServer.waitUntilReady()
            }
            progressBar.visibility = View.GONE
            if (ready) {
                webView.loadUrl("http://${PhpServer.HOST}:${PhpServer.PORT}/")
            } else {
                webView.loadData(
                    "<h3>Could not start the local PHP server.</h3>" +
                        "<p>Check Logcat (tag PhpServer) for details.</p>",
                    "text/html",
                    "UTF-8"
                )
            }
        }
    }

    override fun onDestroy() {
        phpServer.stop()
        super.onDestroy()
    }
}
