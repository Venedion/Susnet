package com.example.susnet.detection

enum class Readiness {
    OK,
    WIFI_OFF,
    NO_PERMISSION,
    COARSE_ONLY,
    LOCATION_SERVICE_OFF,
    NOT_CONNECTED
}

enum class Trigger {
    LAUNCH,
    REFRESH,
    RESUME,
    PERMISSION_RESULT
}

enum class PromptAction {
    NONE,
    REQUEST_PERMISSION,
    SHOW_LOCATION_DIALOG,
    SHOW_APP_SETTINGS_DIALOG
}

object LocationSettingsHelper {

    fun determine(
        wifiEnabled: Boolean,
        hasFine: Boolean,
        hasCoarse: Boolean,
        locationEnabled: Boolean,
        ssidReadable: Boolean
    ): Readiness {
        return when {
            !wifiEnabled -> Readiness.WIFI_OFF
            !hasFine && !hasCoarse -> Readiness.NO_PERMISSION
            !hasFine && hasCoarse -> Readiness.COARSE_ONLY
            !locationEnabled -> Readiness.LOCATION_SERVICE_OFF
            !ssidReadable -> Readiness.NOT_CONNECTED
            else -> Readiness.OK
        }
    }

    fun decidePrompt(
        readiness: Readiness,
        trigger: Trigger,
        permanentlyDenied: Boolean,
        dialogShowing: Boolean
    ): PromptAction {
        if (dialogShowing) return PromptAction.NONE
        if (trigger == Trigger.RESUME) return PromptAction.NONE

        if (trigger == Trigger.PERMISSION_RESULT) {
            return when (readiness) {
                Readiness.NO_PERMISSION, Readiness.COARSE_ONLY -> {
                    if (permanentlyDenied) {
                        PromptAction.SHOW_APP_SETTINGS_DIALOG
                    } else {
                        PromptAction.NONE
                    }
                }
                Readiness.LOCATION_SERVICE_OFF -> PromptAction.SHOW_LOCATION_DIALOG
                else -> PromptAction.NONE
            }
        }

        return when (readiness) {
            Readiness.NO_PERMISSION, Readiness.COARSE_ONLY -> {
                if (permanentlyDenied) {
                    PromptAction.SHOW_APP_SETTINGS_DIALOG
                } else {
                    PromptAction.REQUEST_PERMISSION
                }
            }
            Readiness.LOCATION_SERVICE_OFF -> PromptAction.SHOW_LOCATION_DIALOG
            else -> PromptAction.NONE
        }
    }
}
