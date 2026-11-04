package io.github.zoychikf1.bikemeteo

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * GIF bytes are downloaded by Kotlin and stored in internal app storage.
 * WebView decodes the local GIF and provides pinch zoom and scrolling.
 */
class MainActivity : Activity() {
    companion object {
        private const val SOURCE_URL = "https://goldwing.dedyn.io/index.php"
        private const val LOCAL_ORIGIN = "https://appassets.androidplatform.net/"
        private const val LOCAL_GIF_PATH = "/radar.gif"
        private const val AUTO_REFRESH_MS = 600_000L
        private const val MANUAL_REFRESH_MS = 300_000L
        private const val MAX_GIF_BYTES = 40L * 1024L * 1024L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newSingleThreadExecutor()
    private val cachedGif by lazy { File(filesDir, "radar.gif") }

    private lateinit var webView: WebView
    private lateinit var ageLabel: TextView
    private lateinit var refreshButton: TextView
    private var pageReady = false
    private var downloading = false
    private var lastDownloadFailed = false
    private var imageRevision = 0L
    private var screenVisible = false

    private val ageTicker = object : Runnable {
        override fun run() {
            if (!screenVisible) return
            updateControls()
            handler.postDelayed(this, 1_000L)
        }
    }

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
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    showCachedGif()
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.scheme != "https" ||
                        uri.host != "appassets.androidplatform.net" ||
                        uri.path != LOCAL_GIF_PATH
                    ) return null

                    return try {
                        WebResourceResponse(
                            "image/gif", null, 200, "OK",
                            mapOf("Cache-Control" to "no-store"),
                            FileInputStream(cachedGif)
                        )
                    } catch (_: Exception) {
                        WebResourceResponse(
                            "text/plain", "UTF-8", 404, "Not Found",
                            mapOf("Cache-Control" to "no-store"),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
            }

            settings.apply {
                // JavaScript only runs in our built-in HTML, not on a server page.
                javaScriptEnabled = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                // Zoom and panning are bounded by the GIF edges in local HTML.
                // Native page zoom permits scrolling into the black document area.
                setSupportZoom(false)
                builtInZoomControls = false
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

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 0, 0, 0))
                cornerRadius = dp(22).toFloat()
            }
        }
        ageLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            text = "Загрузка…"
        }
        refreshButton = TextView(this).apply {
            text = "↻"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Обновить метеорадар"
            setOnClickListener { if (isRefreshAllowed()) downloadGif() }
        }

        controls.addView(
            ageLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        controls.addView(refreshButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(12)
                marginEnd = dp(12)
            }
        )
        setContentView(root)

        // The page never references the real server. All GIF requests are local.
        val page = """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1,
                maximum-scale=1, user-scalable=no">
              <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%;
                             overflow: hidden; background: #000; touch-action: none; }
                #stage { position: fixed; inset: 0; overflow: hidden; touch-action: none; }
                #radar { display: none; width: 100%; height: auto; max-width: none;
                         transform-origin: 0 0; touch-action: none;
                         user-select: none; -webkit-user-drag: none; }
                #placeholder { padding: 48px 20px; color: #ddd; text-align: center;
                               font: 15px sans-serif; }
              </style>
            </head>
            <body>
              <div id="stage">
                <div id="placeholder">Загрузка метеорадара…</div>
                <img id="radar" alt="Анимированный метеорадар" draggable="false">
              </div>
              <script>
                (function() {
                  var stage = document.getElementById('stage');
                  var img = document.getElementById('radar');
                  var placeholder = document.getElementById('placeholder');
                  var zoom = 1, x = 0, y = 0, gesture = null;
                  var MAX_ZOOM = 8;

                  function constrain() {
                    if (img.style.display === 'none') return;
                    var iw = img.offsetWidth * zoom;
                    var ih = img.offsetHeight * zoom;
                    var vw = stage.clientWidth;
                    var vh = stage.clientHeight;

                    // No black margins may enter the screen on a zoomed axis.
                    x = iw <= vw ? 0 : Math.max(vw - iw, Math.min(0, x));
                    // A square GIF is shorter than a portrait screen at 1x.
                    // Anchor it to the top until enlarged enough to fill height.
                    y = ih <= vh ? 0 : Math.max(vh - ih, Math.min(0, y));
                  }

                  function paint() {
                    constrain();
                    img.style.transform = 'translate3d(' + x + 'px,' + y +
                                          'px,0) scale(' + zoom + ')';
                  }

                  function midpoint(a, b) {
                    var r = stage.getBoundingClientRect();
                    return { x: (a.clientX + b.clientX) / 2 - r.left,
                             y: (a.clientY + b.clientY) / 2 - r.top };
                  }

                  function start(touches) {
                    if (!img.naturalWidth) return;
                    if (touches.length >= 2) {
                      var a = touches[0], b = touches[1];
                      var mid = midpoint(a, b);
                      gesture = {
                        kind: 'pinch', scale: zoom,
                        distance: Math.max(1, Math.hypot(
                          a.clientX - b.clientX, a.clientY - b.clientY)),
                        sourceX: (mid.x - x) / zoom,
                        sourceY: (mid.y - y) / zoom
                      };
                    } else if (touches.length === 1) {
                      gesture = {
                        kind: 'pan', touchX: touches[0].clientX,
                        touchY: touches[0].clientY, x: x, y: y
                      };
                    } else {
                      gesture = null;
                    }
                  }

                  function touchMove(event) {
                    event.preventDefault();
                    if (!gesture || !img.naturalWidth) return;
                    if (event.touches.length >= 2 && gesture.kind === 'pinch') {
                      var a = event.touches[0], b = event.touches[1];
                      var mid = midpoint(a, b);
                      var distance = Math.hypot(a.clientX - b.clientX,
                                                a.clientY - b.clientY);
                      zoom = Math.max(1, Math.min(MAX_ZOOM,
                          gesture.scale * distance / gesture.distance));
                      x = mid.x - gesture.sourceX * zoom;
                      y = mid.y - gesture.sourceY * zoom;
                      paint();
                    } else if (event.touches.length === 1 &&
                               gesture.kind === 'pan') {
                      x = gesture.x + event.touches[0].clientX - gesture.touchX;
                      y = gesture.y + event.touches[0].clientY - gesture.touchY;
                      paint();
                    }
                  }

                  stage.addEventListener('touchstart', function(event) {
                    event.preventDefault();
                    start(event.touches);
                  }, {passive: false});
                  stage.addEventListener('touchmove', touchMove, {passive: false});
                  stage.addEventListener('touchend', function(event) {
                    event.preventDefault();
                    start(event.touches);
                  }, {passive: false});
                  stage.addEventListener('touchcancel', function(event) {
                    event.preventDefault();
                    start(event.touches);
                  }, {passive: false});
                  window.addEventListener('resize', paint);

                  window.showRadar = function(revision) {
                    img.onload = function() {
                      img.style.display = 'block';
                      placeholder.style.display = 'none';
                      // A fresh GIF does not reset pan and pinch scale.
                      paint();
                    };
                    img.onerror = function() {
                      placeholder.textContent =
                        'Не удалось прочитать сохранённое изображение';
                    };
                    img.src = '/radar.gif?v=' + revision;
                  };
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(LOCAL_ORIGIN, page, "text/html", "UTF-8", null)
        updateControls()
    }

    override fun onResume() {
        super.onResume()
        screenVisible = true
        handler.removeCallbacks(ageTicker)
        handler.post(ageTicker)

        // Only an absent or ten-minute-old cache causes an HTTP request.
        val age = cacheAgeMs()
        if (!downloading && (age == null || age >= AUTO_REFRESH_MS)) {
            downloadGif()
        }
    }

    override fun onPause() {
        screenVisible = false
        handler.removeCallbacks(ageTicker)
        super.onPause()
    }

    private fun cacheAgeMs(): Long? {
        if (!cachedGif.isFile || cachedGif.length() == 0L) return null
        val modified = cachedGif.lastModified()
        val now = System.currentTimeMillis()
        // Invalid or future timestamps must not keep the cache fresh forever.
        if (modified <= 0L || modified > now) return Long.MAX_VALUE
        return now - modified
    }

    private fun isRefreshAllowed(): Boolean {
        if (downloading) return false
        val age = cacheAgeMs()
        return age == null || age >= MANUAL_REFRESH_MS
    }

    private fun updateControls() {
        val age = cacheAgeMs()
        val ageText = when {
            age == null -> "Нет изображения"
            age < 60_000L -> "Только что"
            age < 3_600_000L -> (age / 60_000L).toString() + " мин назад"
            else -> (age / 3_600_000L).toString() + " ч " +
                (age % 3_600_000L / 60_000L).toString() + " мин назад"
        }
        ageLabel.text = when {
            downloading && age == null -> "Загрузка…"
            downloading -> "Обновление… · " + ageText
            lastDownloadFailed && age == null -> "Нет соединения"
            lastDownloadFailed -> "Нет сети · " + ageText
            else -> ageText
        }
        refreshButton.isEnabled = isRefreshAllowed()
        refreshButton.alpha = if (refreshButton.isEnabled) 1.0f else 0.3f
    }

    private fun showCachedGif() {
        if (!pageReady || cacheAgeMs() == null || isDestroyed) return
        imageRevision++
        // Replacing img.src keeps WebView page zoom and scroll position.
        webView.evaluateJavascript("window.showRadar(" + imageRevision + ");", null)
    }

    private fun downloadGif() {
        if (downloading) return
        downloading = true
        lastDownloadFailed = false
        updateControls()
        downloadExecutor.execute {
            val error = try {
                fetchGifAtomically()
                null
            } catch (e: Exception) {
                e
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                downloading = false
                lastDownloadFailed = (error != null)
                if (error == null) showCachedGif()
                updateControls()
            }
        }
    }

    private fun fetchGifAtomically() {
        val temp = File(filesDir, "radar.gif.download")
        var connection: HttpURLConnection? = null
        try {
            // Cache-bust only when a real HTTP download is needed.
            val uri = SOURCE_URL + "?t=" + System.currentTimeMillis()
            connection = URL(uri).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 25_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("Accept", "image/gif")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP " + connection.responseCode)
            }

            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val block = ByteArray(16_384)
                    while (true) {
                        val n = input.read(block)
                        if (n < 0) break
                        total += n
                        if (total > MAX_GIF_BYTES) {
                            throw IllegalStateException("GIF exceeds cache limit")
                        }
                        output.write(block, 0, n)
                    }
                    output.fd.sync()
                }
            }

            val header = ByteArray(6)
            val count = FileInputStream(temp).use { it.read(header) }
            val signature = String(header, Charsets.US_ASCII)
            if (total < 6 || count != 6 ||
                (signature != "GIF87a" && signature != "GIF89a")
            ) {
                throw IllegalStateException("Server did not send a GIF")
            }

            // Atomic replacement preserves the old image if a download fails.
            Os.rename(temp.absolutePath, cachedGif.absolutePath)
            cachedGif.setLastModified(System.currentTimeMillis())
        } finally {
            connection?.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        screenVisible = false
        handler.removeCallbacks(ageTicker)
        downloadExecutor.shutdownNow()
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
