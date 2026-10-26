package com.example.susnet.detection

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

class WifiSecurityChecker(private val context: Context) {

    enum class SecurityType {
        OPEN,
        WEP,
        PERSONAL,
        ENTERPRISE,
        ENHANCED_OPEN,
        UNKNOWN
    }

    data class Result(
        val type: SecurityType,
        val label: String,
        val advice: String
    )

    fun check(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Result(
                type = SecurityType.UNKNOWN,
                label = "Tipe keamanan: Tidak dapat ditentukan di versi Android ini",
                advice = ""
            )
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return Result(
                type = SecurityType.UNKNOWN,
                label = "Tipe keamanan: Tidak diketahui",
                advice = ""
            )

        val info = wifiManager.connectionInfo
        val securityTypeConstant = info?.currentSecurityType ?: -1
        val type = mapSecurityType(securityTypeConstant)

        return buildResult(type)
    }

    companion object {
        fun mapSecurityType(securityTypeConstant: Int, sdkInt: Int = Build.VERSION.SDK_INT): SecurityType {
            if (sdkInt < Build.VERSION_CODES.S) {
                return SecurityType.UNKNOWN
            }

            return when (securityTypeConstant) {
                WifiInfo.SECURITY_TYPE_OPEN -> SecurityType.OPEN
                WifiInfo.SECURITY_TYPE_WEP -> SecurityType.WEP
                WifiInfo.SECURITY_TYPE_OWE -> SecurityType.ENHANCED_OPEN
                WifiInfo.SECURITY_TYPE_PSK,
                WifiInfo.SECURITY_TYPE_SAE,
                WifiInfo.SECURITY_TYPE_WAPI_PSK,
                WifiInfo.SECURITY_TYPE_DPP -> SecurityType.PERSONAL
                WifiInfo.SECURITY_TYPE_EAP,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R3,
                WifiInfo.SECURITY_TYPE_WAPI_CERT -> SecurityType.ENTERPRISE
                else -> SecurityType.UNKNOWN
            }
        }

        fun buildResult(type: SecurityType): Result {
            return when (type) {
                SecurityType.OPEN -> Result(
                    type = type,
                    label = "Keamanan: Terbuka (Tanpa kata sandi)",
                    advice = "Jaringan ini tidak memakai kata sandi. Data yang tidak terenkripsi bisa dibaca orang lain di jaringan yang sama. Hindari memasukkan data sensitif kecuali situsnya memakai HTTPS."
                )
                SecurityType.WEP -> Result(
                    type = type,
                    label = "Keamanan: WEP (Usang)",
                    advice = "WEP sudah usang dan mudah dibobol. Anggap jaringan ini sama tidak amannya dengan jaringan terbuka."
                )
                SecurityType.PERSONAL -> Result(
                    type = type,
                    label = "Keamanan: Pribadi (WPA/WPA2/WPA3)",
                    advice = "Jaringan ini terproteksi kata sandi untuk enkripsi standar."
                )
                SecurityType.ENTERPRISE -> Result(
                    type = type,
                    label = "Keamanan: Perusahaan (EAP/WPA3)",
                    advice = "Jaringan ini menggunakan otentikasi tingkat perusahaan."
                )
                SecurityType.ENHANCED_OPEN -> Result(
                    type = type,
                    label = "Keamanan: Enhanced Open (OWE)",
                    advice = "Jaringan tanpa kata sandi tetapi lalu lintas data dienkripsi individual."
                )
                SecurityType.UNKNOWN -> Result(
                    type = type,
                    label = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "Keamanan: Tidak dapat ditentukan di versi Android ini" else "Keamanan: Tidak diketahui",
                    advice = ""
                )
            }
        }
    }
}
