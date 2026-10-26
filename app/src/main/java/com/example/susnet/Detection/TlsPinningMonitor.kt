package com.example.susnet.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.datatheorem.android.trustkit.TrustKit
import com.datatheorem.android.trustkit.reporting.BackgroundReporter
import com.datatheorem.android.trustkit.reporting.PinningFailureReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class TlsPinningMonitor(private val context: Context) {

    data class Result(val score: Int, val detail: String)

    enum class Outcome { VALID, CERT_FAILURE, INCONCLUSIVE }

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

    suspend fun probe(): Throwable? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://learn.uph.edu")
            val connection = url.openConnection() as HttpsURLConnection
            connection.sslSocketFactory = TrustKit.getInstance().getSSLSocketFactory("learn.uph.edu")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD"
            connection.connect()
            connection.disconnect()
            null
        } catch (e: Throwable) {
            e
        }
    }

    suspend fun evaluate(): Result {
        val error = probe()
        val outcome = classify(error)
        val failures = pendingFailureCount
        val host = lastFailedHost
        pendingFailureCount = 0
        lastFailedHost = null

        return when {
            outcome == Outcome.CERT_FAILURE || failures > 0 -> {
                Result(
                    90,
                    "Sertifikat keamanan situs ${host ?: "learn.uph.edu"} tidak cocok dengan pin yang tersimpan. " +
                            "Ini bisa berarti ada pihak yang menyisipkan sertifikat lain di tengah koneksi, " +
                            "atau situs tersebut memang mengganti sertifikatnya."
                )
            }
            outcome == Outcome.VALID -> {
                Result(0, "Sertifikat situs yang dipantau cocok dengan pin yang tersimpan.")
            }
            else -> {
                Result(0, "Pemeriksaan sertifikat tidak dapat dilakukan (situs tidak merespons atau tidak ada internet). Hasil ini bukan jaminan aman.")
            }
        }
    }

    companion object {
        fun classify(error: Throwable?): Outcome {
            if (error == null) return Outcome.VALID

            var cause: Throwable? = error
            while (cause != null) {
                if (cause is SSLPeerUnverifiedException ||
                    cause is SSLHandshakeException ||
                    cause is CertificateException
                ) {
                    return Outcome.CERT_FAILURE
                }
                cause = cause.cause
            }

            return Outcome.INCONCLUSIVE
        }
    }
}
