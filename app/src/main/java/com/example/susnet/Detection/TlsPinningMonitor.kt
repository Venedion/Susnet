package com.example.susnet.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.datatheorem.android.trustkit.reporting.BackgroundReporter
import com.datatheorem.android.trustkit.reporting.PinningFailureReport

class TlsPinningMonitor(private val context: Context) {

    @Volatile
    private var pendingFailureCount = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val report = intent?.getSerializableExtra(BackgroundReporter.EXTRA_REPORT) as? PinningFailureReport
            if (report != null) {
                pendingFailureCount++
            }
        }
    }

    fun start() {
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, IntentFilter(BackgroundReporter.REPORT_VALIDATION_EVENT))
    }

    fun stop() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
    }

    fun consumeTlsChangeScore(): Int {
        val failures = pendingFailureCount
        pendingFailureCount = 0
        return if (failures > 0) 90 else 0
    }
}
