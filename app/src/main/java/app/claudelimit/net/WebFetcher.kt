package app.claudelimit.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

enum class FetchKind { SESSION, NETWORK, OTHER }

class FetchException(val kind: FetchKind, message: String) : Exception(message)

data class Fetched(val body: String, val plan: String, val org: String?)

/**
 * claude.ai sits behind Cloudflare, which turns away plain HTTP clients. So the request runs inside an
 * invisible WebView that shares the cookies of the sign-in screen: it behaves like the browser that shows
 * claude.ai/settings/usage. The approach follows the open-source "Limits for Claude" app (MIT).
 */
object WebFetcher {
    private const val START = "https://claude.ai/api/organizations"

    /** Makes the WebView look like Chrome, so Google and Cloudflare treat it like a normal browser. */
    fun cleanUserAgent(ua: String): String = ua.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")

    suspend fun fetch(context: Context, orgHint: String?): Fetched {
        val raw = try {
            withTimeout(45_000) { withContext(Dispatchers.Main) { run(context.applicationContext, orgHint) } }
        } catch (e: TimeoutCancellationException) {
            throw FetchException(FetchKind.NETWORK, "timeout")
        }
        val o = JSONObject(raw)
        if (o.has("error")) throw FetchException(FetchKind.NETWORK, o.optString("error"))
        val status = o.optInt("status")
        // 401 anywhere, or 403 on the organization list, means the session is no longer valid.
        if (status == 401 || (status == 403 && o.optString("stage") == "orgs")) {
            throw FetchException(FetchKind.SESSION, "session")
        }
        if (status != 200) throw FetchException(FetchKind.OTHER, "HTTP $status")
        return Fetched(o.optString("body"), o.optString("plan"), o.optString("org").takeIf { it.isNotBlank() })
    }

    private class Bridge(val onDone: (String) -> Unit) {
        @JavascriptInterface
        fun done(s: String) {
            Handler(Looper.getMainLooper()).post { onDone(s) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(c: Context, orgHint: String?): String = suspendCancellableCoroutine { cont ->
        val wv = WebView(c)
        var finished = false
        fun close() {
            if (!finished) {
                finished = true
                wv.stopLoading()
                wv.destroy()
            }
        }
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = cleanUserAgent(wv.settings.userAgentString)
        wv.addJavascriptInterface(Bridge { s ->
            if (!finished) {
                close()
                if (cont.isActive) cont.resume(s)
            }
        }, "CL")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Skip Chromium's own error page: the script would only fail with "Failed to fetch".
                if (!finished && url.startsWith("https://claude.ai/")) view.evaluateJavascript(script(orgHint), null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame || finished) return
                val msg = JSONObject().put("error", "network: ${error.description} (${error.errorCode})").toString()
                close()
                if (cont.isActive) cont.resume(msg)
            }
        }
        cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { close() } }
        wv.loadUrl(START)
    }

    /** Lists the organizations, picks the right one, and reads its usage. Waits out Cloudflare's "Just a moment" page. */
    private fun script(orgHint: String?): String {
        val hint = if (orgHint == null) "null" else JSONObject.quote(orgHint)
        return """
(function(){
  if (window.__cl) return;
  if ((document.title || '').indexOf('Just a moment') >= 0) return;
  window.__cl = 1;
  (async function(){
    try {
      var org = $hint, plan = '';
      var h = {credentials: 'include', headers: {'Accept': 'application/json'}};
      var r = await fetch('/api/organizations', h);
      if (r.status === 401 || r.status === 403) { CL.done(JSON.stringify({status: r.status, stage: 'orgs'})); return; }
      if (r.ok) {
        var a = await r.json(), sel = null, i;
        var m = document.cookie.match(/(?:^|; )lastActiveOrg=([^;]+)/);
        var pref = org || (m ? decodeURIComponent(m[1]) : null);
        for (i = 0; i < a.length; i++) if (a[i].uuid === pref) sel = a[i];
        if (!sel) for (i = 0; i < a.length; i++) if ((a[i].capabilities || []).indexOf('chat') >= 0) { sel = a[i]; break; }
        if (!sel && a.length) sel = a[0];
        if (sel) { org = sel.uuid; plan = (sel.capabilities || []).join(',') + ',' + (sel.rate_limit_tier || ''); }
      }
      if (!org) { CL.done(JSON.stringify({error: 'no organization'})); return; }
      var u = await fetch('/api/organizations/' + org + '/usage', h);
      var t = await u.text();
      CL.done(JSON.stringify({status: u.status, stage: 'usage', org: org, plan: plan, body: t}));
    } catch (e) { CL.done(JSON.stringify({error: String(e)})); }
  })();
})();
""".trimIndent()
    }
}
