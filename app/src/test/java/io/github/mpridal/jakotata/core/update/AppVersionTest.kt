package io.github.mpridal.jakotata.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun parsesTagAndPlainVersion() {
        assertEquals(AppVersion(0, 1, 0), AppVersion.parse("v0.1.0"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse(" 1.2.3 "))
    }

    @Test fun rejectsGarbage() {
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("v1.2.3-beta"))
        assertNull(AppVersion.parse("1.100.0"))
    }

    @Test fun versionCodeMatchesSpecFormula() {
        assertEquals(100, AppVersion(0, 1, 0).code)
        assertEquals(10203, AppVersion(1, 2, 3).code)
    }

    @Test fun ordersByVersion() {
        assertTrue(AppVersion.parse("v0.1.1")!! > AppVersion.parse("v0.1.0")!!)
        assertTrue(AppVersion.parse("v0.10.0")!! > AppVersion.parse("v0.9.9")!!)
    }
}
