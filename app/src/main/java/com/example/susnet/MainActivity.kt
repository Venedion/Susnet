package com.example.susnet

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.SusnetDatabase
import com.example.susnet.detection.EvilTwinDetector
import com.example.susnet.detection.LatencyMonitor
import com.example.susnet.detection.NetworkConfigMonitor
import com.example.susnet.detection.SignalStabilityMonitor
import com.example.susnet.detection.ThreatEvaluator
import com.example.susnet.detection.TlsPinningMonitor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var threatEvaluator: ThreatEvaluator
    private lateinit var tlsPinningMonitor: TlsPinningMonitor
    private var latestResult: ThreatEvaluator.ThreatResult? = null

    private var isEvaluating = false
    private var lastUpdatedMillis: Long = 0
    private var rotateAnimator: ObjectAnimator? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            runEvaluation()
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk memindai WiFi", Toast.LENGTH_SHORT).show()
            findViewById<TextView>(R.id.textSsid).text = "Izin lokasi diperlukan"
            findViewById<TextView>(R.id.textStatusDetail).text = "Izinkan akses lokasi agar nama dan alamat WiFi bisa dibaca"
            findViewById<TextView>(R.id.textScore).text = "–"
            findViewById<TextView>(R.id.textStatusLabel).text = "Tidak aktif"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            findViewById<View>(R.id.layoutBottomNavContainer)?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        setupThreatEvaluator()
        setupAnimations()
        setupNightMode()
        setupBottomNavigation()

        if (hasLocationPermission()) {
            runEvaluation()
        } else {
            checkLocationPermissions()
        }

        findViewById<View>(R.id.buttonDetail).setOnClickListener {
            openDetailScreen()
        }

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            runEvaluation()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation) ?: return
        val scrollContent = findViewById<View>(R.id.scrollContent)
        val scrollHistory = findViewById<View>(R.id.scrollHistory)
        val scrollSettings = findViewById<View>(R.id.scrollSettings)

        bottomNav.selectedItemId = R.id.navigation_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    scrollContent?.visibility = View.VISIBLE
                    scrollHistory?.visibility = View.GONE
                    scrollSettings?.visibility = View.GONE
                    true
                }
                R.id.navigation_history -> {
                    scrollContent?.visibility = View.GONE
                    scrollHistory?.visibility = View.VISIBLE
                    scrollSettings?.visibility = View.GONE
                    loadHistoryData()
                    true
                }
                R.id.navigation_settings -> {
                    scrollContent?.visibility = View.GONE
                    scrollHistory?.visibility = View.GONE
                    scrollSettings?.visibility = View.VISIBLE
                    setupSettingsTab()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadHistoryData() {
        val container = findViewById<LinearLayout>(R.id.containerHistoryList) ?: return
        val emptyText = findViewById<TextView>(R.id.textHistoryEmpty) ?: return

        lifecycleScope.launch {
            try {
                val db = SusnetDatabase.getInstance(applicationContext)
                val events = db.detectionEventDao().getRecentEvents(20)

                container.removeAllViews()

                if (events.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    emptyText.visibility = View.GONE
                    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

                    for (event in events) {
                        val itemView = layoutInflater.inflate(R.layout.item_history_card, container, false)

                        val timeStr = dateFormat.format(Date(event.timestamp))
                        itemView.findViewById<TextView>(R.id.textHistoryTime)?.text = timeStr

                        val scoreBadge = itemView.findViewById<TextView>(R.id.textHistoryScoreBadge)
                        val statusLevel = StatusLevel.fromScore(event.threatLevel)
                        val statusLabel: String
                        val statusColorRes: Int

                        when (statusLevel) {
                            StatusLevel.AMAN -> {
                                statusLabel = "Aman"
                                statusColorRes = R.color.text_success
                            }
                            StatusLevel.WASPADA -> {
                                statusLabel = "Waspada"
                                statusColorRes = R.color.text_warning
                            }
                            StatusLevel.BAHAYA -> {
                                statusLabel = "Bahaya"
                                statusColorRes = R.color.text_danger
                            }
                        }

                        val color = ContextCompat.getColor(this@MainActivity, statusColorRes)
                        scoreBadge?.text = "Skor ${event.threatLevel} — $statusLabel"
                        scoreBadge?.setTextColor(color)

                        container.addView(itemView)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load history", e)
            }
        }
    }

    private fun setupSettingsTab() {
        val switchDarkMode = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSettingsDarkMode) ?: return
        val isNight = isNightModeActive()

        switchDarkMode.setOnCheckedChangeListener(null)
        switchDarkMode.isChecked = isNight

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("susnet_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("pref_night_mode", isChecked)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun setupNightMode() {
        val btnNightMode = findViewById<ImageButton>(R.id.btnNightMode)
        val isNight = isNightModeActive()
        btnNightMode.setImageResource(if (isNight) R.drawable.ic_light_mode else R.drawable.ic_night_mode)
        btnNightMode.contentDescription = if (isNight) "Mode Terang" else "Mode Malam"

        btnNightMode.setOnClickListener {
            val newNightMode = !isNightModeActive()
            getSharedPreferences("susnet_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("pref_night_mode", newNightMode)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (newNightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun isNightModeActive(): Boolean {
        val prefs = getSharedPreferences("susnet_prefs", MODE_PRIVATE)
        if (prefs.contains("pref_night_mode")) {
            return prefs.getBoolean("pref_night_mode", false)
        }
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (!isEvaluating && hasLocationPermission() && (now - lastUpdatedMillis > 30000)) {
            runEvaluation()
        }
    }

    private fun setupAnimations() {
        val btnRefresh = findViewById<View>(R.id.btnRefresh)
        rotateAnimator = ObjectAnimator.ofFloat(btnRefresh, View.ROTATION, 0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun toggleLoading(loading: Boolean) {
        val btnRefresh = findViewById<View>(R.id.btnRefresh)
        isEvaluating = loading
        if (loading) {
            btnRefresh.isEnabled = false
            btnRefresh.alpha = 0.5f
            rotateAnimator?.start()
        } else {
            btnRefresh.isEnabled = true
            btnRefresh.alpha = 1.0f
            rotateAnimator?.cancel()
            btnRefresh.rotation = 0f
        }
    }

    private fun runEvaluation() {
        if (isEvaluating) return

        lifecycleScope.launch {
            toggleLoading(true)
            try {
                val result = threatEvaluator.evaluateCurrentNetwork()
                lastUpdatedMillis = System.currentTimeMillis()
                updateUi(result)
            } catch (e: Exception) {
                Log.e("MainActivity", "Evaluation failed", e)
                Toast.makeText(this@MainActivity, "Gagal memperbarui, coba lagi", Toast.LENGTH_SHORT).show()
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun updateUi(result: ThreatEvaluator.ThreatResult) {
        latestResult = result
        findViewById<TextView>(R.id.textSsid).text = result.currentSsid ?: "Tidak terhubung"
        findViewById<TextView>(R.id.textScore).text = result.threatLevel.toString()

        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdatedMillis))
        findViewById<TextView>(R.id.textLastUpdated).text = "Diperbarui $timeString"

        val statusLevel = StatusLevel.fromScore(result.threatLevel)
        val palette = StatusPalette.from(statusLevel, this)

        findViewById<View>(R.id.main)?.setBackgroundColor(palette.bg)
        window.statusBarColor = palette.bg
        window.navigationBarColor = palette.navBg

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isDark = isNightModeActive()
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        val navContainer = findViewById<View>(R.id.layoutBottomNavContainer)
        val navBorder = findViewById<View>(R.id.borderTopBottomNav)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        navContainer?.setBackgroundColor(palette.navBg)
        navBorder?.setBackgroundColor(palette.border)

        val navColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                palette.textAccent,
                palette.secondary
            )
        )

        bottomNav?.itemIconTintList = navColorStateList
        bottomNav?.itemTextColor = navColorStateList

        val statusLabel: String
        val bgDrawableRes: Int

        when (statusLevel) {
            StatusLevel.AMAN -> {
                statusLabel = "Aman"
                bgDrawableRes = R.drawable.bg_status_card_aman
            }
            StatusLevel.WASPADA -> {
                statusLabel = "Waspada"
                bgDrawableRes = R.drawable.bg_status_card_waspada
            }
            StatusLevel.BAHAYA -> {
                statusLabel = "Bahaya"
                bgDrawableRes = R.drawable.bg_status_card_bahaya
            }
        }

        findViewById<View>(R.id.statusCard)?.setBackgroundResource(bgDrawableRes)
        findViewById<ScoreGaugeView>(R.id.scoreGaugeView)?.setScore(result.threatLevel, palette)

        findViewById<View>(R.id.layoutStatusBadgePill)?.background?.setTint(palette.chipBg)
        val iconStatus = findViewById<ImageView>(R.id.iconStatus)
        val textStatusLabel = findViewById<TextView>(R.id.textStatusLabel)

        textStatusLabel?.text = statusLabel
        textStatusLabel?.setTextColor(palette.textAccent)

        if (statusLevel == StatusLevel.AMAN) {
            iconStatus?.setImageResource(R.drawable.ic_status_normal)
            iconStatus?.setColorFilter(palette.textAccent)
        } else {
            iconStatus?.setImageResource(R.drawable.ic_status_changed)
            iconStatus?.setColorFilter(palette.textAccent)
        }

        Log.d(
            "SusnetAudit",
            "threatLevel=${result.threatLevel}, bssidScore=${result.bssidChangeScore}, tlsScore=${result.tlsChangeScore}, signalScore=${result.signalInstabilityScore}, latencyScore=${result.latencyDeviationScore}, configScore=${result.configChangeScore}"
        )

        val abnormalCount = listOf(
            result.bssidChangeScore,
            result.tlsChangeScore,
            result.signalInstabilityScore,
            result.latencyDeviationScore,
            result.configChangeScore
        ).count { it >= 35 }

        val detailText = when {
            abnormalCount > 0 -> "$abnormalCount tanda tidak biasa terdeteksi"
            statusLevel == StatusLevel.AMAN -> "Semua sinyal terlihat normal"
            else -> "Beberapa sinyal sedikit menyimpang"
        }
        val textStatusDetail = findViewById<TextView>(R.id.textStatusDetail)
        textStatusDetail?.text = detailText
        textStatusDetail?.setTextColor(palette.secondary)

        val extraPill = findViewById<TextView>(R.id.textStatusExtraPill)
        val btnWaspada = findViewById<Button>(R.id.btnSlotWaspadaDetail)
        val btnBahaya = findViewById<Button>(R.id.btnSlotBahayaDisconnect)

        when (statusLevel) {
            StatusLevel.AMAN -> {
                btnWaspada?.visibility = View.GONE
                btnBahaya?.visibility = View.GONE
                extraPill?.visibility = View.VISIBLE

                extraPill?.setBackgroundResource(R.drawable.bg_network_info_pill)
                extraPill?.background?.setTint(palette.tile)
                extraPill?.setTextColor(palette.ink)

                val gateway = result.gatewayIp
                val dns = if (result.dnsServers.isNullOrEmpty()) "-" else result.dnsServers
                extraPill?.text = if (gateway != null) {
                    "GW $gateway · DNS $dns"
                } else {
                    "Info jaringan tidak tersedia"
                }
            }
            StatusLevel.WASPADA -> {
                extraPill?.visibility = View.GONE
                btnBahaya?.visibility = View.GONE
                btnWaspada?.visibility = View.VISIBLE

                btnWaspada?.backgroundTintList = ColorStateList.valueOf(palette.buttonBg)
                btnWaspada?.setTextColor(palette.buttonText)
                btnWaspada?.setOnClickListener {
                    openDetailScreen()
                }
            }
            StatusLevel.BAHAYA -> {
                extraPill?.visibility = View.GONE
                btnWaspada?.visibility = View.GONE
                btnBahaya?.visibility = View.VISIBLE

                btnBahaya?.backgroundTintList = ColorStateList.valueOf(palette.buttonBg)
                btnBahaya?.setTextColor(palette.buttonText)
                btnBahaya?.setOnClickListener {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Buka Pengaturan WiFi di HP Anda", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val signalNormalText = "${result.rssi} dBm · stabil"
        val signalAbnormalText = "${result.rssi} dBm · naik-turun"

        val latencyNormalText = if (result.latencyMs < 0) "Tidak dapat diukur" else "${result.latencyMs} ms · stabil"
        val latencyAbnormalText = if (result.latencyMs < 0) "Tidak dapat diukur" else "${result.latencyMs} ms · naik-turun"

        val cardBssid = findViewById<View>(R.id.cardBssid)
        val cardTls = findViewById<View>(R.id.cardTls)
        val cardSignal = findViewById<View>(R.id.cardSignal)
        val cardLatency = findViewById<View>(R.id.cardLatency)
        val cardArp = findViewById<View>(R.id.cardArp)

        if (cardBssid != null && cardTls != null && cardSignal != null && cardLatency != null && cardArp != null) {
            bindSignalCard(
                cardBssid,
                "Alamat WiFi",
                R.drawable.ic_card_bssid,
                result.bssidChangeScore,
                "BSSID dikenal",
                "BSSID baru, belum dikenal",
                palette
            )
            bindSignalCard(
                cardTls,
                "Sertifikat situs",
                R.drawable.ic_card_tls,
                result.tlsChangeScore,
                "Pin cocok",
                "Sertifikat tidak cocok",
                palette
            )
            bindSignalCard(
                cardSignal,
                "Sinyal",
                R.drawable.ic_card_signal,
                result.signalInstabilityScore,
                signalNormalText,
                signalAbnormalText,
                palette
            )
            bindSignalCard(
                cardLatency,
                "Latency",
                R.drawable.ic_card_latency,
                result.latencyDeviationScore,
                latencyNormalText,
                latencyAbnormalText,
                palette
            )
            bindSignalCard(
                cardArp,
                "Lalu lintas data",
                R.drawable.ic_card_network,
                result.configChangeScore,
                "Gateway dan DNS sama",
                "Gateway dan DNS berubah",
                palette
            )

            val cardList = listOf(
                cardBssid to result.bssidChangeScore,
                cardTls to result.tlsChangeScore,
                cardSignal to result.signalInstabilityScore,
                cardLatency to result.latencyDeviationScore,
                cardArp to result.configChangeScore
            )

            val sortedCards = cardList.sortedByDescending { it.second >= 35 }
            val container = findViewById<LinearLayout>(R.id.containerSignalCards)
            if (container != null) {
                container.removeAllViews()
                for ((cardView, _) in sortedCards) {
                    container.addView(cardView)
                }
            }
        }
    }

    private fun bindSignalCard(
        cardRoot: View,
        label: String,
        iconRes: Int,
        score: Int,
        normalSubtext: String,
        abnormalSubtext: String,
        palette: StatusPalette
    ) {
        cardRoot.findViewById<TextView>(R.id.textCardLabel)?.text = label
        cardRoot.findViewById<ImageView>(R.id.iconCardLeading)?.setImageResource(iconRes)

        val accentBar = cardRoot.findViewById<View>(R.id.accentBar)
        val tileContainer = cardRoot.findViewById<View>(R.id.tileIconContainer)
        val leadingIcon = cardRoot.findViewById<ImageView>(R.id.iconCardLeading)
        val subDetail = cardRoot.findViewById<TextView>(R.id.textCardSubDetail)
        val statusChip = cardRoot.findViewById<View>(R.id.layoutCardStatusChip)
        val statusText = cardRoot.findViewById<TextView>(R.id.textCardStatus)
        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconCardStatus)

        val density = resources.displayMetrics.density

        val cardDrawable = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(palette.surface)
        }

        val tileDrawable = GradientDrawable().apply {
            cornerRadius = 12f * density
        }

        val chipDrawable = GradientDrawable().apply {
            cornerRadius = 20f * density
        }

        if (score < 35) {
            cardDrawable.setStroke((1f * density).toInt(), palette.borderNormal)
            accentBar?.visibility = View.GONE

            tileDrawable.setColor(palette.tileNormal)
            tileContainer?.background = tileDrawable
            leadingIcon?.setColorFilter(palette.normalTileIcon)

            subDetail?.text = normalSubtext
            subDetail?.setTextColor(palette.secondary)

            chipDrawable.setColor(palette.normalChipBg)
            statusChip?.background = chipDrawable

            statusText?.text = "Normal"
            statusText?.setTextColor(palette.normalChipText)
            statusIcon?.setImageResource(R.drawable.ic_status_normal)
            statusIcon?.setColorFilter(palette.normalChipText)
        } else {
            cardDrawable.setStroke((2f * density).toInt(), palette.borderAbnormal)
            accentBar?.visibility = View.VISIBLE
            accentBar?.setBackgroundColor(palette.accent)

            tileDrawable.setColor(palette.chipBg)
            tileContainer?.background = tileDrawable
            leadingIcon?.setColorFilter(palette.accent)

            subDetail?.text = abnormalSubtext
            subDetail?.setTextColor(palette.textAccent)

            chipDrawable.setColor(palette.chipBg)
            statusChip?.background = chipDrawable

            statusText?.text = "Berubah"
            statusText?.setTextColor(palette.textAccent)
            statusIcon?.setImageResource(R.drawable.ic_status_changed)
            statusIcon?.setColorFilter(palette.textAccent)
        }

        cardRoot.background = cardDrawable
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermissions() {
        val fineCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineCheck != PackageManager.PERMISSION_GRANTED || coarseCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupThreatEvaluator() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val baselineStore = BaselineStore(applicationContext)
        val database = SusnetDatabase.getInstance(applicationContext)
        tlsPinningMonitor = TlsPinningMonitor(applicationContext)
        tlsPinningMonitor.start()

        threatEvaluator = ThreatEvaluator(
            evilTwinDetector = EvilTwinDetector(wifiManager, baselineStore),
            tlsPinningMonitor = tlsPinningMonitor,
            signalStabilityMonitor = SignalStabilityMonitor(wifiManager),
            latencyMonitor = LatencyMonitor(),
            networkConfigMonitor = NetworkConfigMonitor(
                applicationContext,
                database.networkConfigDao()
            ),
            baselineStore = baselineStore,
            detectionEventDao = database.detectionEventDao()
        )
    }

    private fun openDetailScreen() {
        val result = latestResult ?: return
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_SSID, result.currentSsid ?: "Tidak diketahui")
            putExtra(DetailActivity.EXTRA_THREAT_LEVEL, result.threatLevel)
            putExtra(DetailActivity.EXTRA_BSSID_SCORE, result.bssidChangeScore)
            putExtra(DetailActivity.EXTRA_TLS_SCORE, result.tlsChangeScore)
            putExtra(DetailActivity.EXTRA_SIGNAL_SCORE, result.signalInstabilityScore)
            putExtra(DetailActivity.EXTRA_LATENCY_SCORE, result.latencyDeviationScore)
            putExtra(DetailActivity.EXTRA_CONFIG_SCORE, result.configChangeScore)
            putExtra(DetailActivity.EXTRA_BSSID_DETAIL, result.bssidDetail)
            putExtra(DetailActivity.EXTRA_TLS_DETAIL, result.tlsDetail)
            putExtra(DetailActivity.EXTRA_SIGNAL_DETAIL, result.signalDetail)
            putExtra(DetailActivity.EXTRA_LATENCY_DETAIL, result.latencyDetail)
            putExtra(DetailActivity.EXTRA_CONFIG_DETAIL, result.configDetail)
            putExtra(DetailActivity.EXTRA_UPDATED_AT, lastUpdatedMillis)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (::tlsPinningMonitor.isInitialized) {
            tlsPinningMonitor.stop()
        }
        super.onDestroy()
    }
}
