package com.example.susnet.com.example.susnet

import android.app.Application
import com.datatheorem.android.trustkit.TrustKit

class SusnetApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        TrustKit.initializeWithNetworkSecurityConfiguration(this)
    }
}