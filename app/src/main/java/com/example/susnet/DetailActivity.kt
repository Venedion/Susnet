package com.example.susnet

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        val updatedAt = intent.getLongExtra(EXTRA_UPDATED_AT, System.currentTimeMillis())
        val timeString = if (updatedAt > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAt))
        } else {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
        findViewById<TextView>(R.id.textDetailSsid).text = "$ssid · $timeString"

        val statusLevel = StatusLevel.fromScore(threatLevel)
        val palette = StatusPalette.from(statusLevel, this)

        findViewById<View>(R.id.detailRoot)?.setBackgroundColor(palette.bg)
        window.statusBarColor = palette.bg
        window.navigationBarColor = palette.navBg

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        val statusLabel: String
        val statusColorRes: Int
        val bgDrawableRes: Int
        val actionText: String

        when (statusLevel) {
            StatusLevel.AMAN -> {
                statusLabel = "Aman"
                statusColorRes = R.color.text_success
                bgDrawableRes = R.drawable.bg_status_card_aman
                actionText = "Tidak ada tindakan khusus yang perlu dilakukan. Jaringan ini terlihat aman digunakan."
            }
            StatusLevel.WASPADA -> {
                statusLabel = "Waspada"
                statusColorRes = R.color.text_warning
                bgDrawableRes = R.drawable.bg_status_card_waspada
                actionText = "Sebaiknya berhati-hati. Hindari memasukkan data penting seperti kata sandi atau info perbankan."
            }
            StatusLevel.BAHAYA -> {
                statusLabel = "Bahaya"
                statusColorRes = R.color.text_danger
                bgDrawableRes = R.drawable.bg_status_card_bahaya
                actionText = "Disarankan memutuskan koneksi dari jaringan ini sekarang, dan periksa kembali sebelum tersambung lagi."
            }
        }

        val statusColor = ContextCompat.getColor(this, statusColorRes)
        findViewById<View>(R.id.statusBannerCard)?.setBackgroundResource(bgDrawableRes)

        val statusLabelView = findViewById<TextView>(R.id.textDetailStatusLabel)
        statusLabelView.text = statusLabel
        statusLabelView.setTextColor(statusColor)

        val scoreView = findViewById<TextView>(R.id.textDetailScore)
        scoreView.text = "Skor $threatLevel / 100"
        scoreView.setTextColor(statusColor)

        val actionView = findViewById<TextView>(R.id.textRecommendedAction)
        actionView.text = actionText
        actionView.setTextColor(statusColor)

        val btnDisconnect = findViewById<Button>(R.id.btnDetailDisconnectWifi)
        if (statusLevel == StatusLevel.BAHAYA) {
            btnDisconnect.visibility = View.VISIBLE
            btnDisconnect.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Buka Pengaturan WiFi di HP Anda", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnDisconnect.visibility = View.GONE
        }

        fillExplanationCard(
            includeId = R.id.explainBssid,
            label = "Alamat WiFi",
            score = bssidScore,
            normalExplanation = "Alamat perangkat WiFi ini sama seperti yang biasa dikenali untuk jaringan ini.",
            abnormalExplanation = "Alamat perangkat WiFi berbeda dari yang biasa dikenali untuk nama jaringan ini. Ini bisa berarti ada jaringan lain yang meniru nama yang sama.",
            detail = bssidDetail,
            possibleCauses = "• Jaringan tiruan dengan nama yang sama\n• Router atau repeater lain milik jaringan yang sama"
        )

        fillExplanationCard(
            includeId = R.id.explainTls,
            label = "Sertifikat situs",
            score = tlsScore,
            normalExplanation = "Sertifikat keamanan situs yang dipantau masih sesuai dan belum berubah.",
            abnormalExplanation = "Sertifikat keamanan salah satu situs yang dipantau terdeteksi berbeda dari yang seharusnya. Ini bisa berarti ada pihak lain yang mencoba menyadap koneksi.",
            detail = tlsDetail,
            possibleCauses = "• Percobaan Man-in-the-Middle (MitM)\n• Proxy atau firewall jaringan publik yang membelokkan lalu lintas HTTPS"
        )

        fillExplanationCard(
            includeId = R.id.explainSignal,
            label = "Sinyal",
            score = signalScore,
            normalExplanation = "Kekuatan sinyal WiFi stabil, sesuai kondisi biasanya.",
            abnormalExplanation = "Kekuatan sinyal WiFi naik turun tidak wajar. Ini bisa karena gangguan biasa, atau ada perangkat lain yang memengaruhi koneksi.",
            detail = signalDetail,
            possibleCauses = "• Berpindah posisi atau terhalang dinding\n• Gangguan dari perangkat atau jaringan lain di sekitar"
        )

        fillExplanationCard(
            includeId = R.id.explainLatency,
            label = "Latency",
            score = latencyScore,
            normalExplanation = "Waktu respons koneksi stabil, sesuai kondisi biasanya.",
            abnormalExplanation = "Waktu respons koneksi naik turun tidak wajar. Ini bisa karena jaringan sedang padat, atau ada pihak lain yang menyisip di jalur koneksimu.",
            detail = latencyDetail,
            possibleCauses = "• Jalur lalu lintas data dialihkan atau diinspeksi oleh perangkat tambahan\n• Padatnya penggunaan bandwidth pada jaringan ini"
        )

        fillExplanationCard(
            includeId = R.id.explainArp,
            label = "Lalu lintas data",
            score = configScore,
            normalExplanation = "Jalur koneksi dan server DNS jaringan ini sama seperti yang biasa dikenali, jadi lalu lintas datamu mengikuti jalur yang wajar.",
            abnormalExplanation = "Jalur koneksi atau server DNS jaringan ini berbeda dari yang biasa dikenali. Ini bisa berarti ada jaringan tiruan, atau ada pihak yang membelokkan lalu lintas datamu.",
            detail = configDetail,
            possibleCauses = "• Jaringan tiruan atau DNS yang dibelokkan\n• Router diganti atau berpindah titik akses jaringan"
        )
    }

    private fun fillExplanationCard(
        includeId: Int,
        label: String,
        score: Int,
        normalExplanation: String,
        abnormalExplanation: String,
        detail: String,
        possibleCauses: String
    ) {
        val cardRoot = findViewById<View>(includeId) ?: return
        cardRoot.findViewById<TextView>(R.id.textExplainLabel)?.text = label

        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconExplainStatus)
        val statusText = cardRoot.findViewById<TextView>(R.id.textExplainStatus)
        val detailText = cardRoot.findViewById<TextView>(R.id.textExplainDetail)

        val detectedLayout = cardRoot.findViewById<View>(R.id.layoutDetectedDetails)
        val detectedContent = cardRoot.findViewById<TextView>(R.id.textDetectedContent)
        val causesLayout = cardRoot.findViewById<View>(R.id.layoutPossibleCauses)
        val causesContent = cardRoot.findViewById<TextView>(R.id.textPossibleCausesContent)

        if (score < 35) {
            val normalColor = ContextCompat.getColor(this, R.color.text_success)
            statusIcon?.setImageResource(R.drawable.ic_status_normal)
            statusText?.text = "Normal"
            statusText?.setTextColor(normalColor)
            detailText?.text = normalExplanation
            detectedLayout?.visibility = View.GONE
            causesLayout?.visibility = View.GONE
        } else {
            val warningColor = ContextCompat.getColor(this, R.color.text_warning)
            statusIcon?.setImageResource(R.drawable.ic_status_changed)
            statusText?.text = "Berubah"
            statusText?.setTextColor(warningColor)
            detailText?.text = abnormalExplanation

            if (detail.isNotBlank()) {
                detectedLayout?.visibility = View.VISIBLE
                detectedContent?.text = detail
            } else {
                detectedLayout?.visibility = View.GONE
            }

            causesLayout?.visibility = View.VISIBLE
            causesContent?.text = possibleCauses
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
        const val EXTRA_UPDATED_AT = "extra_updated_at"
    }
}
