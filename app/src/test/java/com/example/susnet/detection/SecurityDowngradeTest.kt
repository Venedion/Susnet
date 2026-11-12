package com.example.susnet.detection

import com.example.susnet.detection.WifiSecurityChecker.SecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecurityDowngradeTest {

    // --- rank tests ---

    @Test
    fun testRank_Open() {
        assertEquals(0, SecurityDowngrade.rank(SecurityType.OPEN))
    }

    @Test
    fun testRank_Wep() {
        assertEquals(1, SecurityDowngrade.rank(SecurityType.WEP))
    }

    @Test
    fun testRank_EnhancedOpen() {
        assertEquals(2, SecurityDowngrade.rank(SecurityType.ENHANCED_OPEN))
    }

    @Test
    fun testRank_Personal() {
        assertEquals(3, SecurityDowngrade.rank(SecurityType.PERSONAL))
    }

    @Test
    fun testRank_Enterprise() {
        assertEquals(4, SecurityDowngrade.rank(SecurityType.ENTERPRISE))
    }

    @Test
    fun testRank_Unknown() {
        assertNull(SecurityDowngrade.rank(SecurityType.UNKNOWN))
    }

    // --- downgradeScore tests ---

    @Test
    fun testDowngradeScore_PersonalToOpen() {
        assertEquals(85, SecurityDowngrade.downgradeScore(SecurityType.PERSONAL, SecurityType.OPEN))
    }

    @Test
    fun testDowngradeScore_PersonalToWep() {
        assertEquals(70, SecurityDowngrade.downgradeScore(SecurityType.PERSONAL, SecurityType.WEP))
    }

    @Test
    fun testDowngradeScore_EnterpriseToPersonal() {
        assertEquals(50, SecurityDowngrade.downgradeScore(SecurityType.ENTERPRISE, SecurityType.PERSONAL))
    }

    @Test
    fun testDowngradeScore_PersonalToPersonal() {
        assertEquals(0, SecurityDowngrade.downgradeScore(SecurityType.PERSONAL, SecurityType.PERSONAL))
    }

    @Test
    fun testDowngradeScore_WepToPersonal() {
        assertEquals(0, SecurityDowngrade.downgradeScore(SecurityType.WEP, SecurityType.PERSONAL))
    }

    @Test
    fun testDowngradeScore_BaselineNull() {
        assertEquals(0, SecurityDowngrade.downgradeScore(null, SecurityType.PERSONAL))
    }

    @Test
    fun testDowngradeScore_CurrentUnknown() {
        assertEquals(0, SecurityDowngrade.downgradeScore(SecurityType.PERSONAL, SecurityType.UNKNOWN))
    }

    @Test
    fun testDowngradeScore_OpenToOpen() {
        assertEquals(0, SecurityDowngrade.downgradeScore(SecurityType.OPEN, SecurityType.OPEN))
    }

    // --- nextBaseline tests ---

    @Test
    fun testNextBaseline_NullPlusPersonal() {
        assertEquals(SecurityType.PERSONAL, SecurityDowngrade.nextBaseline(null, SecurityType.PERSONAL))
    }

    @Test
    fun testNextBaseline_PersonalPlusOpen() {
        assertEquals(SecurityType.PERSONAL, SecurityDowngrade.nextBaseline(SecurityType.PERSONAL, SecurityType.OPEN))
    }

    @Test
    fun testNextBaseline_WepPlusPersonal() {
        assertEquals(SecurityType.PERSONAL, SecurityDowngrade.nextBaseline(SecurityType.WEP, SecurityType.PERSONAL))
    }

    @Test
    fun testNextBaseline_PersonalPlusUnknown() {
        assertEquals(SecurityType.PERSONAL, SecurityDowngrade.nextBaseline(SecurityType.PERSONAL, SecurityType.UNKNOWN))
    }
}
