package io.github.zoychikf1.bikemeteo

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A minimal animated GIF viewer. Chromium WebView draws all GIF frames, while
 * its built-in page zoom handles pinch-to-zoom and two-dimensional panning.
 */
class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this)
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            webViewClient = WebViewClient()

            settings.apply {
                javaScriptEnabled = false
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = false
                allowContentAccess = false
            }
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val refresh = TextView(this).apply {
            text = "↻"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Обновить анимацию"
            elevation = dp(4).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.argb(165, 0, 0, 0))
                cornerRadius = dp(24).toFloat()
            }
            setOnClickListener { loadRadar() }
        }

        val refreshParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(12)
            marginEnd = dp(12)
        }
        root.addView(refresh, refreshParams)
        setContentView(root)

        // An HTML image element supports animated GIFs without re-encoding.
        loadRadar()
    }

    private fun loadRadar() {
        // Unique URL plus LOAD_NO_CACHE bypasses WebView's cached GIF response.
        val gifUrl = "https://goldwing.dedyn.io/index.php?t=${System.currentTimeMillis()}"
        val page = """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1,
                minimum-scale=0.25, maximum-scale=8.0, user-scalable=yes">
              <style>
                html, body { margin: 0; padding: 0; min-height: 100%; background: #000; }
                img { display: block; width: 100%; height: auto; max-width: none; }
              </style>
            </head>
            <body>
              <img src="$gifUrl" alt="Метеорадар">
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(
            "https://goldwing.dedyn.io/",
            page,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
