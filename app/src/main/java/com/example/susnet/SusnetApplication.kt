package com.example.susnet

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.datatheorem.android.trustkit.TrustKit
import com.example.susnet.notification.AutoCheckManager

class SusnetApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        TrustKit.initializeWithNetworkSecurityConfiguration(this)

        val prefs = getSharedPreferences("susnet_prefs", MODE_PRIVATE)
        val isNightMode = prefs.getBoolean("pref_night_mode", false)
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        AutoCheckManager.start(this)
    }
}
