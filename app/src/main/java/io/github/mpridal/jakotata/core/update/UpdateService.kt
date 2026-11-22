package io.github.mpridal.jakotata.core.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Samoaktualizace z GitHub Releases (zadání kap. 9, port z Deskovek): kontrola na pozadí max. 1× za 6 h,
 * timeout 4 s, chyby tiše; nikdy neblokuje kameru. Stažení do `cache/updates/`, ověření SHA-256,
 * instalace systémovým instalátorem. Profily jsou v `files/`, aktualizace je nesmaže.
 */
class UpdateService(private val context: Context, private val current: AppVersion) {
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    suspend fun check(force: Boolean, autoEnabled: Boolean): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                if (!autoEnabled) return@withContext UpdateCheck.NotChecked
                val last = prefs.getLong(LAST_CHECK, 0L)
                if (System.currentTimeMillis() - last < INTERVAL_MS) return@withContext UpdateCheck.NotChecked
            }
            val release = fetchLatest() ?: return@withContext UpdateCheck.Failed("no usable release")
            val result = UpdateCheck.evaluate(release, current)
            if (result is UpdateCheck.UpToDate) prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
            result
        } catch (e: Exception) {
            Log.i(TAG, "Update check failed: $e")
            UpdateCheck.Failed(e.toString())
        }
    }

    private fun fetchLatest(): ReleaseInfo? {
        val api = JSONObject(get("https://api.github.com/repos/$OWNER/$REPO/releases/latest"))
        val tag = AppVersion.parse(api.getString("tag_name")) ?: return null
        val assets = api.getJSONArray("assets")
        var json: String? = null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.getString("name")) {
                "latest.json" -> json = a.getString("browser_download_url")
                "jakotata-$tag.apk" -> apk = a.getString("browser_download_url")
            }
        }
        if (json == null || apk == null) return null
        return ReleaseInfo.parse(get(json), apk)?.takeIf { it.version == tag }
    }

    /** Stáhne APK, ověří SHA-256 a spustí systémovou instalaci. false = chyba (soubor se nepoužije). */
    suspend fun downloadAndInstall(release: ReleaseInfo, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "jakotata-${release.version}.apk")
            val conn = open(release.apkUrl, timeoutMs = 15_000)
            val total = conn.contentLengthLong.coerceAtLeast(1)
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(done.toFloat() / total)
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != release.sha256) {
                Log.w(TAG, "SHA-256 mismatch: $sha != ${release.sha256}")
                return@withContext false
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            false
        }
    }

    private fun get(url: String): String = open(url, TIMEOUT_MS).inputStream.use { it.readBytes().decodeToString() }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "JakoTata-Android")
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode for $url")
        }

    private companion object {
        const val TAG = "UpdateService"
        const val OWNER = "mpridal"
        const val REPO = "jakotata"
        const val LAST_CHECK = "last_check_ms"
        const val INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val TIMEOUT_MS = 4000
    }
}
