package com.example.susnet.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.datatheorem.android.trustkit.reporting.BackgroundReporter
import com.datatheorem.android.trustkit.reporting.PinningFailureReport

class TlsPinningMonitor(private val context: Context) {

    data class Result(val score: Int, val detail: String)

    @Volatile
    private var pendingFailureCount = 0

    @Volatile
    private var lastFailedHost: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val report = intent?.getSerializableExtra(BackgroundReporter.EXTRA_REPORT) as? PinningFailureReport
            if (report != null) {
                lastFailedHost = report.serverHostname
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

    fun consumeTlsResult(): Result {
        val failures = pendingFailureCount
        val host = lastFailedHost
        pendingFailureCount = 0
        lastFailedHost = null

        return if (failures > 0) {
            Result(
                90,
                "Sertifikat keamanan situs ${host ?: "yang dipantau"} tidak cocok dengan pin yang tersimpan " +
                        "($failures kegagalan verifikasi). Ini bisa berarti ada pihak yang menyisipkan " +
                        "sertifikat lain di tengah koneksi, atau situs tersebut memang mengganti sertifikatnya."
            )
        } else {
            Result(0, "Tidak ada kegagalan verifikasi sertifikat yang dilaporkan pada situs yang dipantau.")
        }
    }
}