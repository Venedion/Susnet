package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSettingsHelperTest {

    // --- determine tests ---

    @Test
    fun testDetermine_WifiOff() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = false,
            hasFine = true,
            hasCoarse = true,
            locationEnabled = true,
            ssidReadable = true
        )
        assertEquals(Readiness.WIFI_OFF, readiness)
    }

    @Test
    fun testDetermine_NoPermission() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = true,
            hasFine = false,
            hasCoarse = false,
            locationEnabled = true,
            ssidReadable = true
        )
        assertEquals(Readiness.NO_PERMISSION, readiness)
    }

    @Test
    fun testDetermine_CoarseOnly() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = true,
            hasFine = false,
            hasCoarse = true,
            locationEnabled = true,
            ssidReadable = true
        )
        assertEquals(Readiness.COARSE_ONLY, readiness)
    }

    @Test
    fun testDetermine_LocationServiceOff() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = true,
            hasFine = true,
            hasCoarse = true,
            locationEnabled = false,
            ssidReadable = true
        )
        assertEquals(Readiness.LOCATION_SERVICE_OFF, readiness)
    }

    @Test
    fun testDetermine_NotConnected() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = true,
            hasFine = true,
            hasCoarse = true,
            locationEnabled = true,
            ssidReadable = false
        )
        assertEquals(Readiness.NOT_CONNECTED, readiness)
    }

    @Test
    fun testDetermine_Ok() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = true,
            hasFine = true,
            hasCoarse = true,
            locationEnabled = true,
            ssidReadable = true
        )
        assertEquals(Readiness.OK, readiness)
    }

    @Test
    fun testDetermine_PriorityWifiOffOverNoPermission() {
        val readiness = LocationSettingsHelper.determine(
            wifiEnabled = false,
            hasFine = false,
            hasCoarse = false,
            locationEnabled = false,
            ssidReadable = false
        )
        assertEquals(Readiness.WIFI_OFF, readiness)
    }

    // --- decidePrompt tests ---

    @Test
    fun testDecidePrompt_NoPermission_Launch() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.NO_PERMISSION, Trigger.LAUNCH, false, false
        )
        assertEquals(PromptAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun testDecidePrompt_NoPermission_Refresh() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.NO_PERMISSION, Trigger.REFRESH, false, false
        )
        assertEquals(PromptAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun testDecidePrompt_NoPermission_PermanentlyDenied() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.NO_PERMISSION, Trigger.LAUNCH, true, false
        )
        assertEquals(PromptAction.SHOW_APP_SETTINGS_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_LocationServiceOff_Launch() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.LOCATION_SERVICE_OFF, Trigger.LAUNCH, false, false
        )
        assertEquals(PromptAction.SHOW_LOCATION_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_LocationServiceOff_Refresh() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.LOCATION_SERVICE_OFF, Trigger.REFRESH, false, false
        )
        assertEquals(PromptAction.SHOW_LOCATION_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_AllReadiness_Resume() {
        for (readiness in Readiness.values()) {
            val action = LocationSettingsHelper.decidePrompt(
                readiness, Trigger.RESUME, true, false
            )
            assertEquals(PromptAction.NONE, action)
        }
    }

    @Test
    fun testDecidePrompt_DialogShowingTrue() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.LOCATION_SERVICE_OFF, Trigger.LAUNCH, false, true
        )
        assertEquals(PromptAction.NONE, action)
    }

    @Test
    fun testDecidePrompt_Ok_WifiOff_NotConnected() {
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.OK, Trigger.LAUNCH, false, false))
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.WIFI_OFF, Trigger.LAUNCH, false, false))
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.NOT_CONNECTED, Trigger.LAUNCH, false, false))
    }

    // --- decidePrompt tests for PERMISSION_RESULT ---

    @Test
    fun testDecidePrompt_PermissionResult_NoPermission_NotPermanentlyDenied() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.NO_PERMISSION, Trigger.PERMISSION_RESULT, false, false
        )
        assertEquals(PromptAction.NONE, action)
    }

    @Test
    fun testDecidePrompt_PermissionResult_NoPermission_PermanentlyDenied() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.NO_PERMISSION, Trigger.PERMISSION_RESULT, true, false
        )
        assertEquals(PromptAction.SHOW_APP_SETTINGS_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_PermissionResult_CoarseOnly_NotPermanentlyDenied() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.COARSE_ONLY, Trigger.PERMISSION_RESULT, false, false
        )
        assertEquals(PromptAction.NONE, action)
    }

    @Test
    fun testDecidePrompt_PermissionResult_CoarseOnly_PermanentlyDenied() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.COARSE_ONLY, Trigger.PERMISSION_RESULT, true, false
        )
        assertEquals(PromptAction.SHOW_APP_SETTINGS_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_PermissionResult_LocationServiceOff() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.LOCATION_SERVICE_OFF, Trigger.PERMISSION_RESULT, false, false
        )
        assertEquals(PromptAction.SHOW_LOCATION_DIALOG, action)
    }

    @Test
    fun testDecidePrompt_PermissionResult_Ok_WifiOff_NotConnected() {
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.OK, Trigger.PERMISSION_RESULT, false, false))
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.WIFI_OFF, Trigger.PERMISSION_RESULT, false, false))
        assertEquals(PromptAction.NONE, LocationSettingsHelper.decidePrompt(Readiness.NOT_CONNECTED, Trigger.PERMISSION_RESULT, false, false))
    }

    @Test
    fun testDecidePrompt_PermissionResult_DialogShowingTrue() {
        val action = LocationSettingsHelper.decidePrompt(
            Readiness.LOCATION_SERVICE_OFF, Trigger.PERMISSION_RESULT, false, true
        )
        assertEquals(PromptAction.NONE, action)
    }
}
