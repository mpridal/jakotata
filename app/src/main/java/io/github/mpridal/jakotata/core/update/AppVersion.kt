package io.github.mpridal.jakotata.core.update

/**
 * Verze ve tvaru major.minor.patch (tag `v1.2.3` i `1.2.3`). Základ pro samoaktualizaci (zadání kap. 9).
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    /** versionCode = major·10000 + minor·100 + patch – stejný vzorec jako v Gradle a release.yml. */
    val code: Int get() = major * 10000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int = code.compareTo(other.code)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(text: String): AppVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (ma, mi, pa) = m.destructured
            val v = AppVersion(ma.toInt(), mi.toInt(), pa.toInt())
            return if (v.minor < 100 && v.patch < 100) v else null
        }
    }
}
