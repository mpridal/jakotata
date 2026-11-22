package io.github.mpridal.jakotata.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInfoTest {
    private val sha = "a".repeat(64)
    private fun json(version: String, code: Int, min: String = "0.0.0") =
        """{"version":"$version","versionCode":$code,"sha256":"$sha","minSupportedVersion":"$min","notes_cs":"Opravy","notes_en":"Fixes","notes_es":"Arreglos"}"""

    @Test fun parsesValidLatestJson() {
        val r = ReleaseInfo.parse(json("0.1.1", 101), "https://x/apk")!!
        assertEquals(AppVersion(0, 1, 1), r.version)
        assertEquals("Opravy", r.notesFor("cs"))
        assertEquals("Fixes", r.notesFor("de"))
    }

    @Test fun rejectsMismatchedVersionCodeOrBadHash() {
        assertNull(ReleaseInfo.parse(json("0.1.1", 100), "u"))
        assertNull(ReleaseInfo.parse(json("0.1.1", 101).replace(sha, "xyz"), "u"))
        assertNull(ReleaseInfo.parse("not json", "u"))
    }

    @Test fun newerVersionIsAvailableAndMinimumMakesItMandatory() {
        val current = AppVersion(0, 1, 0)
        val r = ReleaseInfo.parse(json("0.1.1", 101), "u")!!
        assertEquals(UpdateCheck.Available(r, mandatory = false), UpdateCheck.evaluate(r, current))
        val mandatory = ReleaseInfo.parse(json("0.2.0", 200, min = "0.1.5"), "u")!!
        assertTrue((UpdateCheck.evaluate(mandatory, current) as UpdateCheck.Available).mandatory)
        assertEquals(UpdateCheck.UpToDate, UpdateCheck.evaluate(r, AppVersion(0, 1, 1)))
    }
}
