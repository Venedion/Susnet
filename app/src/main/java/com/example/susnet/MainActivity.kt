package com.example.susnet

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
        bottomNav.selectedItemId = R.id.navigation_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    true
                }
                R.id.navigation_history -> {
                    Toast.makeText(this, "Halaman Riwayat", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.navigation_settings -> {
                    Toast.makeText(this, "Halaman Pengaturan", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
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

        val statusLabel: String
        val statusColorRes: Int
        val bgDrawableRes: Int

        when {
            result.threatLevel < 35 -> {
                statusLabel = "Aman"
                statusColorRes = R.color.text_success
                bgDrawableRes = R.drawable.bg_status_card_aman
            }
            result.threatLevel < 65 -> {
                statusLabel = "Waspada"
                statusColorRes = R.color.text_warning
                bgDrawableRes = R.drawable.bg_status_card_waspada
            }
            else -> {
                statusLabel = "Bahaya"
                statusColorRes = R.color.text_danger
                bgDrawableRes = R.drawable.bg_status_card_bahaya
            }
        }

        val color = ContextCompat.getColor(this, statusColorRes)
        findViewById<View>(R.id.statusCard)?.setBackgroundResource(bgDrawableRes)

        findViewById<TextView>(R.id.textStatusLabel).text = statusLabel
        findViewById<TextView>(R.id.textScore).setTextColor(color)
        findViewById<TextView>(R.id.textStatusLabel).setTextColor(color)

        val abnormalCount = listOf(
            result.bssidChangeScore,
            result.tlsChangeScore,
            result.signalInstabilityScore,
            result.latencyDeviationScore,
            result.configChangeScore
        ).count { it >= 35 }

        val detailText = if (abnormalCount == 0) {
            "Semua sinyal terlihat normal"
        } else {
            "$abnormalCount tanda tidak biasa terdeteksi"
        }
        findViewById<TextView>(R.id.textStatusDetail).text = detailText
        findViewById<TextView>(R.id.textStatusDetail).setTextColor(color)

        val extraPill = findViewById<TextView>(R.id.textStatusExtraPill)
        val btnDisconnect = findViewById<View>(R.id.btnDisconnectWifi)

        if (result.threatLevel >= 65) {
            extraPill?.visibility = View.GONE
            btnDisconnect?.visibility = View.VISIBLE
            btnDisconnect?.setOnClickListener {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Buka Pengaturan WiFi di HP Anda", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnDisconnect?.visibility = View.GONE
            extraPill?.visibility = View.VISIBLE
            extraPill?.setTextColor(color)
            extraPill?.text = "GW 192.168.1.1 · DNS 192.168.1.1 · WPA2"
        }

        updateSignalCard(
            R.id.cardBssid,
            "Alamat WiFi",
            R.drawable.ic_card_bssid,
            result.bssidChangeScore,
            "BSSID dikenal",
            "BSSID baru, belum dikenal"
        )
        updateSignalCard(
            R.id.cardTls,
            "Sertifikat situs",
            R.drawable.ic_card_tls,
            result.tlsChangeScore,
            "Pin cocok",
            "Sertifikat tidak cocok"
        )
        updateSignalCard(
            R.id.cardSignal,
            "Sinyal",
            R.drawable.ic_card_signal,
            result.signalInstabilityScore,
            "-52 dBm · stabil",
            "Sinyal tidak stabil"
        )
        updateSignalCard(
            R.id.cardLatency,
            "Latency",
            R.drawable.ic_card_latency,
            result.latencyDeviationScore,
            "38 ms · stabil",
            "Latency tinggi"
        )
        updateSignalCard(
            R.id.cardArp,
            "Lalu lintas data",
            R.drawable.ic_card_network,
            result.configChangeScore,
            "Gateway dan DNS sama",
            "Gateway dan DNS berubah"
        )
    }

    private fun updateSignalCard(
        includeId: Int,
        label: String,
        leadingIconRes: Int,
        score: Int,
        normalSubtext: String,
        abnormalSubtext: String
    ) {
        val cardRoot = findViewById<View>(includeId) ?: return
        cardRoot.findViewById<TextView>(R.id.textCardLabel)?.text = label
        cardRoot.findViewById<ImageView>(R.id.iconCardLeading)?.setImageResource(leadingIconRes)

        val subDetail = cardRoot.findViewById<TextView>(R.id.textCardSubDetail)
        val statusText = cardRoot.findViewById<TextView>(R.id.textCardStatus)
        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconCardStatus)

        if (score < 35) {
            subDetail?.text = normalSubtext
            statusText?.text = "Normal"
            statusText?.setTextColor(ContextCompat.getColor(this, R.color.text_success))
            statusIcon?.setImageResource(R.drawable.ic_status_normal)
        } else {
            subDetail?.text = abnormalSubtext
            statusText?.text = "Berubah"
            statusText?.setTextColor(ContextCompat.getColor(this, R.color.text_warning))
            statusIcon?.setImageResource(R.drawable.ic_status_changed)
        }
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
