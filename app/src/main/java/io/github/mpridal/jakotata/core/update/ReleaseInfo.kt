package io.github.mpridal.jakotata.core.update

import org.json.JSONObject

/** Vydání z GitHub Releases popsané souborem `latest.json` (zadání kap. 9). */
data class ReleaseInfo(
    val version: AppVersion,
    val versionCode: Int,
    val sha256: String,
    val minSupportedVersion: AppVersion,
    val notes: Map<String, String>,
    val apkUrl: String,
) {
    /** Poznámky v jazyce [lang] (cs/en/es), jinak anglicky. */
    fun notesFor(lang: String): String = notes[lang] ?: notes["en"] ?: ""

    companion object {
        /** Parsuje `latest.json`; null, když chybí povinná pole nebo nesedí verze a versionCode. */
        fun parse(json: String, apkUrl: String): ReleaseInfo? = runCatching {
            val o = JSONObject(json)
            val v = AppVersion.parse(o.getString("version")) ?: return null
            val code = o.getInt("versionCode")
            if (code != v.code) return null
            val sha = o.getString("sha256").lowercase()
            if (!Regex("^[0-9a-f]{64}$").matches(sha)) return null
            ReleaseInfo(
                version = v,
                versionCode = code,
                sha256 = sha,
                minSupportedVersion = AppVersion.parse(o.optString("minSupportedVersion", "0.0.0")) ?: AppVersion(0, 0, 0),
                notes = listOf("cs", "en", "es").associateWith { o.optString("notes_$it") },
                apkUrl = apkUrl,
            )
        }.getOrNull()
    }
}

/** Výsledek kontroly aktualizace. */
sealed interface UpdateCheck {
    data object NotChecked : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
    data class Available(val release: ReleaseInfo, val mandatory: Boolean) : UpdateCheck

    companion object {
        /** Porovnání s nainstalovanou verzí (čistá logika, testovaná na JVM). */
        fun evaluate(release: ReleaseInfo, current: AppVersion): UpdateCheck =
            if (release.version > current) Available(release, mandatory = release.minSupportedVersion > current) else UpToDate
    }
}
