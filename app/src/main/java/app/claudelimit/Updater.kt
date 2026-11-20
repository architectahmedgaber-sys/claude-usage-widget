package app.claudelimit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Looks for a newer release on GitHub, downloads its APK and opens Android's installer. */
object Updater {
    data class Release(val version: String, val apkUrl: String)

    private const val API = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    /** The newest release if it is newer than this app, otherwise null (also null when offline). */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "ClaudeLimit")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val j = JSONObject(text)
            val v = j.getString("tag_name").removePrefix("v")
            val assets = j.getJSONArray("assets")
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            if (url.isNotEmpty() && isNewer(v, BuildConfig.VERSION_NAME)) Release(v, url) else null
        }.getOrNull()
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun openFollowing(url: String, hops: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "ClaudeLimit")
        val code = c.responseCode
        if (code in 300..399 && hops > 0) {
            val next = c.getHeaderField("Location") ?: error("redirect")
            c.disconnect()
            return openFollowing(next, hops - 1)
        }
        if (code != 200) error("HTTP $code")
        return c
    }

    /** True when Android lets the app open the installer; otherwise sends the person to the switch for it. */
    fun canInstall(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    /** Downloads the APK and opens the installer. [progress] gets 0..1. */
    suspend fun downloadAndInstall(context: Context, r: Release, progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "claude-limit-${r.version}.apk")
            val c = openFollowing(r.apkUrl, 5)
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
            c.disconnect()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(i) }
        }.isSuccess
    }
}
