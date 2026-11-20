package app.claudelimit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.claudelimit.net.WebFetcher
import app.claudelimit.work.RefreshWorker
import app.claudelimit.work.Ticker

/**
 * The real claude.ai sign-in page; it closes by itself as soon as the session cookie appears.
 *
 * Signing in with email is easiest this way: the link in the email opens in the browser and shows a 6-digit
 * code, which is typed into the code box on this page. Nothing needs to be set up. If the link does open here
 * (allowed in Android settings, or copied to the clipboard), that works too.
 * The flow follows the open-source "Limits for Claude" app (MIT).
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView
    private var done = false
    private var lastClip: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Tx.ar = Store.settings.value.lang == "AR"
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = Tx.t(
                "١) اكتب إيميلك واضغط المتابعة بالإيميل.\n٢) افتح الإيميل واضغط الرابط: هيفتح في المتصفح ويطلعلك كود من 6 أرقام.\n٣) ارجع هنا واكتب الكود في الخانة.\nلو بتدخل بجوجل: اضغط المتابعة بجوجل.",
                "1) Enter your email and continue with email.\n2) Open the email and tap the link: it opens in your browser and shows a 6-digit code.\n3) Come back here and type the code in the box.\nWith Google: tap Continue with Google.",
            )
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setPadding(pad, pad / 2, pad, pad / 2)
        })
        root.addView(Button(this).apply {
            text = Tx.t("افتح الإيميل", "Open email app")
            setOnClickListener { openMailApp() }
        })
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // Android draws apps edge to edge, so keep the page clear of the status bar and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = WebFetcher.cleanUserAgent(web.settings.userAgentString)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                check(url)
            }
        }
        // Going to the mail app and back can reset the page, so the page is restored when we return.
        val link = linkFrom(intent)
        if (link == null && savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            // restored
        } else {
            web.loadUrl(link ?: "https://claude.ai/login")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun openMailApp() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(this, Tx.t("افتح تطبيق الإيميل بنفسك.", "Open your email app yourself."), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { web.loadUrl(it) }
    }

    /** The clipboard can be read only while the window has focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) checkClipboard()
    }

    private fun linkFrom(i: Intent?): String? =
        i?.data?.toString()?.takeIf { it.startsWith("https://claude.ai/") }

    private fun checkClipboard() {
        val clip = try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        } catch (e: Exception) {
            null
        }
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        val url = Regex("https://claude\\.ai/\\S+").find(text)?.value ?: return
        if (url == lastClip) return
        lastClip = url
        web.loadUrl(url)
    }

    private fun check(url: String) {
        if (done) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        if ("sessionKey=" in cookies && url.startsWith("https://claude.ai") && "/login" !in url && "/magic-link" !in url) {
            done = true
            CookieManager.getInstance().flush()
            Store.setSignedIn(this, true)
            Ticker.apply(this, Store.settings.value.refreshMinutes)
            RefreshWorker.refreshNow(this)
            if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
