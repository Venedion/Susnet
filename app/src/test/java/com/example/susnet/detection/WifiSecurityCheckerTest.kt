package com.example.susnet.detection

import android.net.wifi.WifiInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSecurityCheckerTest {

    @Test
    fun testMapOpenReturnsOpen() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_OPEN, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.OPEN, type)
    }

    @Test
    fun testMapWepReturnsWep() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_WEP, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.WEP, type)
    }

    @Test
    fun testMapPskReturnsPersonal() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_PSK, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.PERSONAL, type)
    }

    @Test
    fun testMapOweReturnsEnhancedOpen() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_OWE, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.ENHANCED_OPEN, type)
    }

    @Test
    fun testMapEapReturnsEnterprise() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_EAP, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.ENTERPRISE, type)
    }

    @Test
    fun testMapUnknownConstantReturnsUnknown() {
        val type = WifiSecurityChecker.mapSecurityType(999, sdkInt = 31)
        assertEquals(WifiSecurityChecker.SecurityType.UNKNOWN, type)
    }

    @Test
    fun testMapBelowApi31ReturnsUnknown() {
        val type = WifiSecurityChecker.mapSecurityType(WifiInfo.SECURITY_TYPE_PSK, sdkInt = 30)
        assertEquals(WifiSecurityChecker.SecurityType.UNKNOWN, type)
    }
}
