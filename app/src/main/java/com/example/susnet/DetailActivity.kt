package com.example.susnet

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        val bgDrawableRes: Int
        val actionText: String
        val iconRes: Int

        when (statusLevel) {
            StatusLevel.AMAN -> {
                statusLabel = "Aman"
                bgDrawableRes = R.drawable.bg_status_card_aman
                actionText = "Tidak ada tindakan khusus yang perlu dilakukan. Jaringan ini terlihat aman digunakan."
                iconRes = R.drawable.ic_status_normal
            }
            StatusLevel.WASPADA -> {
                statusLabel = "Waspada"
                bgDrawableRes = R.drawable.bg_status_card_waspada
                actionText = "Sebaiknya berhati-hati. Hindari memasukkan data penting seperti kata sandi atau info perbankan."
                iconRes = R.drawable.ic_status_changed
            }
            StatusLevel.BAHAYA -> {
                statusLabel = "Bahaya"
                bgDrawableRes = R.drawable.bg_status_card_bahaya
                actionText = "Disarankan memutuskan koneksi dari jaringan ini sekarang, dan periksa kembali sebelum tersambung lagi."
                iconRes = R.drawable.ic_alert_triangle
            }
        }

        val cardDrawable = ContextCompat.getDrawable(this, bgDrawableRes)?.mutate() as? GradientDrawable
        if (cardDrawable != null) {
            cardDrawable.setColor(palette.surface)
            cardDrawable.setStroke((1 * resources.displayMetrics.density).toInt(), palette.border)
            findViewById<View>(R.id.statusBannerCard)?.background = cardDrawable
        } else {
            findViewById<View>(R.id.statusBannerCard)?.setBackgroundResource(bgDrawableRes)
        }

        val badgePill = findViewById<View>(R.id.layoutStatusBadgePill)
        val badgeDrawable = ContextCompat.getDrawable(this, R.drawable.bg_status_badge_pill)?.mutate() as? GradientDrawable
        if (badgeDrawable != null) {
            badgeDrawable.setColor(palette.chipBg)
            badgePill?.background = badgeDrawable
        }

        val iconView = findViewById<ImageView>(R.id.iconDetailStatus)
        iconView?.setImageResource(iconRes)
        iconView?.imageTintList = ColorStateList.valueOf(palette.textAccent)

        val statusLabelView = findViewById<TextView>(R.id.textDetailStatusLabel)
        statusLabelView?.text = statusLabel
        statusLabelView?.setTextColor(palette.textAccent)

        val scoreValueView = findViewById<TextView>(R.id.textDetailScoreValue)
        scoreValueView?.text = threatLevel.toString()
        scoreValueView?.setTextColor(palette.textAccent)

        findViewById<ScoreZoneBarView>(R.id.scoreZoneBarView)?.setScore(threatLevel, palette)

        val actionView = findViewById<TextView>(R.id.textRecommendedAction)
        actionView?.text = actionText
        actionView?.setTextColor(palette.textAccent)

        val btnDisconnect = findViewById<Button>(R.id.btnDetailDisconnectWifi)
        if (statusLevel == StatusLevel.BAHAYA) {
            btnDisconnect?.visibility = View.VISIBLE
            btnDisconnect?.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Buka Pengaturan WiFi di HP Anda", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnDisconnect?.visibility = View.GONE
        }

        val cardActionsContainer = findViewById<View>(R.id.cardActionsContainer)
        val textAction1 = findViewById<TextView>(R.id.textAction1)
        val textAction2 = findViewById<TextView>(R.id.textAction2)
        val textAction3 = findViewById<TextView>(R.id.textAction3)
        val textActionNum1 = findViewById<TextView>(R.id.textActionNum1)
        val textActionNum2 = findViewById<TextView>(R.id.textActionNum2)
        val textActionNum3 = findViewById<TextView>(R.id.textActionNum3)

        val numCircleDrawable1 = ContextCompat.getDrawable(this, R.drawable.bg_number_circle)?.mutate() as? GradientDrawable
        val numCircleDrawable2 = ContextCompat.getDrawable(this, R.drawable.bg_number_circle)?.mutate() as? GradientDrawable
        val numCircleDrawable3 = ContextCompat.getDrawable(this, R.drawable.bg_number_circle)?.mutate() as? GradientDrawable
        numCircleDrawable1?.setColor(palette.chipBg)
        numCircleDrawable2?.setColor(palette.chipBg)
        numCircleDrawable3?.setColor(palette.chipBg)

        textActionNum1?.background = numCircleDrawable1
        textActionNum1?.setTextColor(palette.textAccent)

        textActionNum2?.background = numCircleDrawable2
        textActionNum2?.setTextColor(palette.textAccent)

        textActionNum3?.background = numCircleDrawable3
        textActionNum3?.setTextColor(palette.textAccent)

        when (statusLevel) {
            StatusLevel.AMAN -> {
                cardActionsContainer?.visibility = View.GONE
            }
            StatusLevel.WASPADA -> {
                cardActionsContainer?.visibility = View.VISIBLE
                textAction1?.text = "Hindari memasukkan kata sandi atau info bank selama tanda ini masih ada."
                textAction2?.text = "Pastikan nama WiFi yang benar dengan pengelola tempat."
                textAction3?.text = "Segarkan beberapa menit lagi untuk melihat apakah tandanya hilang."
            }
            StatusLevel.BAHAYA -> {
                cardActionsContainer?.visibility = View.VISIBLE
                textAction1?.text = "Putuskan sambungan dari jaringan ini."
                textAction2?.text = "Jangan memasukkan kata sandi atau info bank selama masih tersambung."
                textAction3?.text = "Periksa nama dan alamat WiFi dengan pengelola tempat sebelum tersambung lagi."
            }
        }

        val signals = listOf(
            SignalInfo(
                label = "Alamat WiFi",
                tileIconRes = R.drawable.ic_card_bssid,
                score = bssidScore,
                normalExplanation = "Alamat perangkat WiFi ini sama seperti yang biasa dikenali untuk jaringan ini.",
                abnormalExplanation = "Alamat perangkat WiFi berbeda dari yang biasa dikenali untuk nama jaringan ini. Ini bisa berarti ada jaringan lain yang meniru nama yang sama.",
                detail = bssidDetail,
                possibleCauses = "• Jaringan tiruan dengan nama yang sama\n• Router atau repeater lain milik jaringan yang sama"
            ),
            SignalInfo(
                label = "Sertifikat situs",
                tileIconRes = R.drawable.ic_card_tls,
                score = tlsScore,
                normalExplanation = "Sertifikat keamanan situs yang dipantau masih sesuai dan belum berubah.",
                abnormalExplanation = "Sertifikat keamanan salah satu situs yang dipantau terdeteksi berbeda dari yang seharusnya. Ini bisa berarti ada pihak lain yang mencoba menyadap koneksi.",
                detail = tlsDetail,
                possibleCauses = "• Percobaan Man-in-the-Middle (MitM)\n• Proxy atau firewall jaringan publik yang membelokkan lalu lintas HTTPS"
            ),
            SignalInfo(
                label = "Sinyal",
                tileIconRes = R.drawable.ic_card_signal,
                score = signalScore,
                normalExplanation = "Kekuatan sinyal WiFi stabil, sesuai kondisi biasanya.",
                abnormalExplanation = "Kekuatan sinyal WiFi naik turun tidak wajar. Ini bisa karena gangguan biasa, atau ada perangkat lain yang memengaruhi koneksi.",
                detail = signalDetail,
                possibleCauses = "• Berpindah posisi atau terhalang dinding\n• Gangguan dari perangkat atau jaringan lain di sekitar"
            ),
            SignalInfo(
                label = "Latency",
                tileIconRes = R.drawable.ic_card_latency,
                score = latencyScore,
                normalExplanation = "Waktu respons koneksi stabil, sesuai kondisi biasanya.",
                abnormalExplanation = "Waktu respons koneksi naik turun tidak wajar. Ini bisa karena jaringan sedang padat, atau ada pihak lain yang menyisip di jalur koneksimu.",
                detail = latencyDetail,
                possibleCauses = "• Jalur lalu lintas data dialihkan atau diinspeksi oleh perangkat tambahan\n• Padatnya penggunaan bandwidth pada jaringan ini"
            ),
            SignalInfo(
                label = "Lalu lintas data",
                tileIconRes = R.drawable.ic_card_network,
                score = configScore,
                normalExplanation = "Jalur koneksi dan server DNS jaringan ini sama seperti yang biasa dikenali, jadi lalu lintas datamu mengikuti jalur yang wajar.",
                abnormalExplanation = "Jalur koneksi atau server DNS jaringan ini berbeda dari yang biasa dikenali. Ini bisa berarti ada jaringan tiruan, atau ada pihak yang membelokkan lalu lintas datamu.",
                detail = configDetail,
                possibleCauses = "• Jaringan tiruan atau DNS yang dibelokkan\n• Router diganti atau berpindah titik akses jaringan"
            )
        )

        // Pindahkan kartu Berubah ke atas daftar
        val sortedSignals = signals.sortedByDescending { it.score >= 35 }

        val containerCards = findViewById<LinearLayout>(R.id.containerExplanationCards)
        containerCards?.removeAllViews()

        for (signal in sortedSignals) {
            val cardRoot = layoutInflater.inflate(R.layout.item_explanation_card, containerCards, false)
            fillExplanationCard(
                cardRoot = cardRoot,
                label = signal.label,
                tileIconRes = signal.tileIconRes,
                score = signal.score,
                normalExplanation = signal.normalExplanation,
                abnormalExplanation = signal.abnormalExplanation,
                detail = signal.detail,
                possibleCauses = signal.possibleCauses,
                palette = palette
            )
            containerCards?.addView(cardRoot)
        }
    }

    private fun fillExplanationCard(
        cardRoot: View,
        label: String,
        tileIconRes: Int,
        score: Int,
        normalExplanation: String,
        abnormalExplanation: String,
        detail: String,
        possibleCauses: String,
        palette: StatusPalette
    ) {
        val isChanged = score >= 35

        val iconTile = cardRoot.findViewById<ImageView>(R.id.iconExplainTile)
        iconTile?.setImageResource(tileIconRes)
        iconTile?.imageTintList = ColorStateList.valueOf(if (isChanged) palette.textAccent else ContextCompat.getColor(this, R.color.text_primary))

        val textLabel = cardRoot.findViewById<TextView>(R.id.textExplainLabel)
        textLabel?.text = label

        val stripeAccent = cardRoot.findViewById<View>(R.id.stripeAccent)
        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconExplainStatus)
        val statusText = cardRoot.findViewById<TextView>(R.id.textExplainStatus)
        val statusBadge = cardRoot.findViewById<View>(R.id.layoutStatusBadge)

        val detailText = cardRoot.findViewById<TextView>(R.id.textExplainDetail)
        val expandableContent = cardRoot.findViewById<View>(R.id.layoutExpandableContent)
        val arrowIcon = cardRoot.findViewById<ImageView>(R.id.iconArrowExpand)

        val detectedLayout = cardRoot.findViewById<View>(R.id.layoutDetectedDetails)
        val detectedContent = cardRoot.findViewById<TextView>(R.id.textDetectedContent)
        val causesLayout = cardRoot.findViewById<View>(R.id.layoutPossibleCauses)
        val causesContent = cardRoot.findViewById<TextView>(R.id.textPossibleCausesContent)

        val containerCards = findViewById<ViewGroup>(R.id.containerExplanationCards)

        val materialCard = cardRoot as? com.google.android.material.card.MaterialCardView

        if (isChanged) {
            stripeAccent?.visibility = View.VISIBLE
            stripeAccent?.setBackgroundColor(palette.accent)
            materialCard?.setCardBackgroundColor(palette.surface)
            materialCard?.strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.accent, 140)
            materialCard?.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()

            statusIcon?.setImageResource(R.drawable.ic_status_changed)
            statusIcon?.imageTintList = ColorStateList.valueOf(palette.textAccent)
            statusText?.text = "Berubah"
            statusText?.setTextColor(palette.textAccent)

            val badgeDrawable = ContextCompat.getDrawable(this, R.drawable.bg_status_badge_pill)?.mutate() as? GradientDrawable
            badgeDrawable?.setColor(palette.chipBg)
            statusBadge?.background = badgeDrawable

            detailText?.text = abnormalExplanation

            if (detail.isNotBlank()) {
                detectedLayout?.visibility = View.VISIBLE
                detectedContent?.text = detail

                val boxDrawable = ContextCompat.getDrawable(this, R.drawable.bg_detected_box)?.mutate() as? GradientDrawable
                boxDrawable?.setColor(palette.tile)
                boxDrawable?.setStroke((1 * resources.displayMetrics.density).toInt(), palette.border)
                detectedLayout?.background = boxDrawable
            } else {
                detectedLayout?.visibility = View.GONE
            }

            causesLayout?.visibility = View.VISIBLE
            causesContent?.text = possibleCauses

            expandableContent?.visibility = View.VISIBLE
            arrowIcon?.rotation = 180f
        } else {
            stripeAccent?.visibility = View.GONE
            materialCard?.setCardBackgroundColor(palette.surface)
            materialCard?.strokeColor = ContextCompat.getColor(this, R.color.surface_card_border)
            materialCard?.strokeWidth = (1f * resources.displayMetrics.density).toInt()

            val normalColor = ContextCompat.getColor(this, R.color.text_success)
            val normalChipBg = ContextCompat.getColor(this, R.color.normal_chip_bg)

            statusIcon?.setImageResource(R.drawable.ic_status_normal)
            statusIcon?.imageTintList = ColorStateList.valueOf(normalColor)
            statusText?.text = "Normal"
            statusText?.setTextColor(normalColor)

            val badgeDrawable = ContextCompat.getDrawable(this, R.drawable.bg_status_badge_pill)?.mutate() as? GradientDrawable
            badgeDrawable?.setColor(normalChipBg)
            statusBadge?.background = badgeDrawable

            detailText?.text = normalExplanation
            detectedLayout?.visibility = View.GONE
            causesLayout?.visibility = View.GONE

            expandableContent?.visibility = View.GONE
            arrowIcon?.rotation = 0f
        }

        val headerLayout = cardRoot.findViewById<View>(R.id.layoutCardHeader)
        headerLayout?.setOnClickListener {
            if (containerCards != null) {
                TransitionManager.beginDelayedTransition(containerCards, AutoTransition().apply {
                    duration = 200
                })
            }
            if (expandableContent?.visibility == View.VISIBLE) {
                expandableContent.visibility = View.GONE
                arrowIcon?.animate()?.rotation(0f)?.setDuration(200)?.start()
            } else {
                expandableContent?.visibility = View.VISIBLE
                arrowIcon?.animate()?.rotation(180f)?.setDuration(200)?.start()
            }
        }
    }

    private data class SignalInfo(
        val label: String,
        val tileIconRes: Int,
        val score: Int,
        val normalExplanation: String,
        val abnormalExplanation: String,
        val detail: String,
        val possibleCauses: String
    )

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
