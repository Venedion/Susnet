package com.example.susnet

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val ssid = intent.getStringExtra(EXTRA_SSID) ?: "Tidak diketahui"
        val threatLevel = intent.getIntExtra(EXTRA_THREAT_LEVEL, 0)
        val bssidScore = intent.getIntExtra(EXTRA_BSSID_SCORE, 0)
        val tlsScore = intent.getIntExtra(EXTRA_TLS_SCORE, 0)
        val signalScore = intent.getIntExtra(EXTRA_SIGNAL_SCORE, 0)
        val latencyScore = intent.getIntExtra(EXTRA_LATENCY_SCORE, 0)
        val configScore = intent.getIntExtra(EXTRA_CONFIG_SCORE, 0)

        val bssidDetail = intent.getStringExtra(EXTRA_BSSID_DETAIL).orEmpty()
        val tlsDetail = intent.getStringExtra(EXTRA_TLS_DETAIL).orEmpty()
        val signalDetail = intent.getStringExtra(EXTRA_SIGNAL_DETAIL).orEmpty()
        val latencyDetail = intent.getStringExtra(EXTRA_LATENCY_DETAIL).orEmpty()
        val configDetail = intent.getStringExtra(EXTRA_CONFIG_DETAIL).orEmpty()

        findViewById<TextView>(R.id.textDetailSsid).text = ssid

        val statusLabel: String
        val statusColorRes: Int
        val actionText: String

        when {
            threatLevel < 35 -> {
                statusLabel = "Aman"
                statusColorRes = R.color.text_success
                actionText = "Tidak ada tindakan khusus yang perlu dilakukan. Jaringan ini terlihat aman digunakan."
            }
            threatLevel < 65 -> {
                statusLabel = "Waspada"
                statusColorRes = R.color.text_warning
                actionText = "Sebaiknya berhati-hati. Hindari memasukkan data penting seperti kata sandi atau info bank sampai kamu yakin jaringan ini aman."
            }
            else -> {
                statusLabel = "Bahaya"
                statusColorRes = R.color.text_danger
                actionText = "Disarankan memutuskan koneksi dari jaringan ini sekarang, dan periksa kembali sebelum tersambung lagi."
            }
        }

        val statusColor = ContextCompat.getColor(this, statusColorRes)
        findViewById<TextView>(R.id.textDetailScore).text = "Skor $threatLevel — $statusLabel"
        findViewById<TextView>(R.id.textDetailScore).setTextColor(statusColor)
        findViewById<TextView>(R.id.textRecommendedAction).text = actionText
        findViewById<TextView>(R.id.textRecommendedAction).setTextColor(statusColor)

        fillExplanationCard(
            includeId = R.id.explainBssid,
            iconRes = R.drawable.ic_router,
            label = "Alamat wifi",
            score = bssidScore,
            normalExplanation = "Alamat perangkat WiFi ini sama seperti yang biasa dikenali untuk jaringan ini.",
            abnormalExplanation = "Alamat perangkat WiFi berbeda dari yang biasa dikenali untuk nama jaringan ini. Ini bisa berarti ada jaringan lain yang meniru nama yang sama.",
            detail = bssidDetail
        )

        fillExplanationCard(
            includeId = R.id.explainTls,
            iconRes = R.drawable.ic_lock,
            label = "Sertifikat situs",
            score = tlsScore,
            normalExplanation = "Sertifikat keamanan situs yang dipantau masih sesuai dan belum berubah.",
            abnormalExplanation = "Sertifikat keamanan salah satu situs yang dipantau terdeteksi berbeda dari yang seharusnya. Ini bisa berarti ada pihak lain yang mencoba menyadap koneksi.",
            detail = tlsDetail
        )

        fillExplanationCard(
            includeId = R.id.explainSignal,
            iconRes = R.drawable.ic_router,
            label = "Sinyal",
            score = signalScore,
            normalExplanation = "Kekuatan sinyal WiFi stabil, sesuai kondisi biasanya.",
            abnormalExplanation = "Kekuatan sinyal WiFi naik turun tidak wajar. Ini bisa karena gangguan biasa, atau ada perangkat lain yang memengaruhi koneksi.",
            detail = signalDetail
        )

        fillExplanationCard(
            includeId = R.id.explainLatency,
            iconRes = R.drawable.ic_router,
            label = "Latency",
            score = latencyScore,
            normalExplanation = "Waktu respons koneksi stabil, sesuai kondisi biasanya.",
            abnormalExplanation = "Waktu respons koneksi naik turun tidak wajar. Ini bisa karena jaringan sedang padat, atau ada pihak lain yang menyisip di jalur koneksimu.",
            detail = latencyDetail
        )

        fillExplanationCard(
            includeId = R.id.explainArp,
            iconRes = R.drawable.ic_share,
            label = "Lalu lintas data",
            score = configScore,
            normalExplanation = "Jalur koneksi dan server DNS jaringan ini sama seperti yang biasa dikenali, jadi lalu lintas datamu mengikuti jalur yang wajar.",
            abnormalExplanation = "Jalur koneksi atau server DNS jaringan ini berbeda dari yang biasa dikenali. Ini bisa berarti ada jaringan tiruan, atau ada pihak yang membelokkan lalu lintas datamu.",
            detail = configDetail
        )
    }

    private fun fillExplanationCard(
        includeId: Int,
        iconRes: Int,
        label: String,
        score: Int,
        normalExplanation: String,
        abnormalExplanation: String,
        detail: String
    ) {
        val cardRoot = findViewById<View>(includeId)
        cardRoot.findViewById<ImageView>(R.id.iconExplainLeading).setImageResource(iconRes)
        cardRoot.findViewById<TextView>(R.id.textExplainLabel).text = label

        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconExplainStatus)
        val statusText = cardRoot.findViewById<TextView>(R.id.textExplainStatus)
        val detailText = cardRoot.findViewById<TextView>(R.id.textExplainDetail)

        val explanation: String
        if (score < 35) {
            val normalColor = ContextCompat.getColor(this, R.color.text_success)
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(normalColor)
            statusText.text = "Normal"
            statusText.setTextColor(normalColor)
            explanation = normalExplanation
        } else {
            val warningColor = ContextCompat.getColor(this, R.color.text_warning)
            statusIcon.setImageResource(R.drawable.ic_alert_triangle)
            statusIcon.setColorFilter(warningColor)
            statusText.text = "Berubah"
            statusText.setTextColor(warningColor)
            explanation = abnormalExplanation
        }

        detailText.text = if (detail.isBlank()) {
            explanation
        } else {
            "$explanation\n\nYang terdeteksi: $detail"
        }
    }

    companion object {
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_THREAT_LEVEL = "extra_threat_level"
        const val EXTRA_BSSID_SCORE = "extra_bssid_score"
        const val EXTRA_TLS_SCORE = "extra_tls_score"
        const val EXTRA_SIGNAL_SCORE = "extra_signal_score"
        const val EXTRA_LATENCY_SCORE = "extra_latency_score"
        const val EXTRA_CONFIG_SCORE = "extra_config_score"
        const val EXTRA_BSSID_DETAIL = "extra_bssid_detail"
        const val EXTRA_TLS_DETAIL = "extra_tls_detail"
        const val EXTRA_SIGNAL_DETAIL = "extra_signal_detail"
        const val EXTRA_LATENCY_DETAIL = "extra_latency_detail"
        const val EXTRA_CONFIG_DETAIL = "extra_config_detail"
    }
}
