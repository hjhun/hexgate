package dev.hexgate

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale

/** No Javascript bridge; resources are served only from app assets or a bounded SFTP root. */
@SuppressLint("SetJavaScriptEnabled")
class DocumentPreview @JvmOverloads constructor(context: Context, private val trustedRenderer: Boolean = true, private val visualTheme: AppTheme = AppThemes.resolve("hexgate")) : WebView(context) {
    @Volatile private var page = ""
    @Volatile private var document: RemoteDocument? = null
    private var files: RemoteFiles? = null
    private val resourceSlots = java.util.concurrent.Semaphore(4)
    private var resourceCount = java.util.concurrent.atomic.AtomicInteger()
    private var resourceBytes = java.util.concurrent.atomic.AtomicInteger()
    private var loadGeneration = 0L
    private var contentUrl = ""
    private var renderDispatched = false
    var onOpen: ((String) -> Unit)? = null
    init {
        settings.javaScriptEnabled = trustedRenderer
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        settings.databaseEnabled = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                if (uri.scheme != "https" || uri.host != HOST) return denied()
                return try {
                    val path = uri.path ?: return denied()
                    when {
                        request.isForMainFrame && path == "/remote/" -> WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(page.toByteArray(Charsets.UTF_8)))
                        path.startsWith("/assets/") -> {
                            val asset = path.removePrefix("/assets/")
                            if (asset.contains("..") || asset.contains('/')) return denied()
                            WebResourceResponse(if (asset.endsWith(".js")) "application/javascript" else "text/plain", "UTF-8", context.assets.open(asset))
                        }
                        path.startsWith("/remote/") -> {
                            val doc = document ?: return denied()
                            if (resourceCount.incrementAndGet() > 64 || !resourceSlots.tryAcquire(5,java.util.concurrent.TimeUnit.SECONDS)) return denied()
                            val bytes = try { files?.resource(doc.root, path.removePrefix("/remote/")) ?: return denied() } finally { resourceSlots.release() }
                            if (resourceBytes.addAndGet(bytes.size) > 16 * 1024 * 1024) return denied()
                            val mime = when(path.substringAfterLast('.').lowercase(Locale.ROOT)) {
                                "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"; "css" -> "text/css"; "drawio", "xml" -> "application/xml"; else -> return denied()
                            }
                            WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(bytes))
                        }
                        else -> denied()
                    }
                } catch (_: Exception) { denied() }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return true
                val uri = request.url
                if (request.hasGesture() && uri.scheme == "https" && uri.host == HOST && uri.path.orEmpty().startsWith("/remote/")) {
                    val root = document?.root ?: return true
                    val candidate = root.trimEnd('/') + "/" + uri.path!!.removePrefix("/remote/")
                    if (RemoteFiles.inside(root,candidate)) onOpen?.invoke(candidate)
                }
                return true
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val doc = document ?: return
                if (url != contentUrl || renderDispatched || doc.kind == "html") return
                renderDispatched = true
                val text = if (doc.kind == "mermaid") "```mermaid\n${doc.text}\n```" else doc.text
                val payload = JSONObject().put("kind", doc.kind).put("text", text).put("light",visualTheme.light).toString()
                evaluateJavascript("window.renderDocument($payload)", null)
            }
        }
    }
    fun show(doc: RemoteDocument, remote: RemoteFiles) {
        if (document === doc && files === remote) return
        onResume()
        stopLoading()
        document = doc; files = remote
        resourceCount = java.util.concurrent.atomic.AtomicInteger(); resourceBytes = java.util.concurrent.atomic.AtomicInteger()
        require(trustedRenderer == (doc.kind != "html")) { "HTML and trusted renderer require separate WebViews" }
        val trusted = "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; img-src 'self' data:; font-src data:; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'self'"
        val untrusted = "default-src 'none'; script-src 'none'; style-src 'unsafe-inline' 'self'; img-src 'self' data:; font-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
        val csp = if (doc.kind == "html") untrusted else trusted
        val head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"$csp\"><style>body{font:16px/1.65 sans-serif;background:${visualTheme.css(visualTheme.background)};color:${visualTheme.css(visualTheme.foreground)};padding:20px;overflow-wrap:anywhere}pre{overflow:auto;background:${visualTheme.css(AppThemes.blend(visualTheme.background,visualTheme.foreground,.06f))};padding:12px;border-radius:6px}a{color:${visualTheme.css(visualTheme.accent)}}img,svg{max-width:100%;height:auto}table{border-collapse:collapse}td,th{border:1px solid ${visualTheme.css(visualTheme.accent)};padding:6px}</style>"
        val body = if (doc.kind == "html") doc.text else "<main id=\"document\"></main><script src=\"/assets/renderer-init.js\"></script><script src=\"/assets/drawio-viewer.js\"></script><script src=\"/assets/preview.js\"></script>"
        page = "<!doctype html><html><head>$head</head><body>$body</body></html>"
        contentUrl = "https://$HOST/remote/?load=${++loadGeneration}"
        renderDispatched = false
        loadUrl(contentUrl)
    }
    fun suspendPreview() { contentUrl = ""; renderDispatched = false; stopLoading(); loadUrl("about:blank"); onPause(); document = null; files = null }
    companion object {
        const val HOST = "appassets.androidplatform.net"
        private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
