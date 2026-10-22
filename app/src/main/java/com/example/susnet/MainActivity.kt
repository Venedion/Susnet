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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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
        val statusIconRes: Int

        when {
            result.threatLevel < 35 -> {
                statusLabel = "Aman"
                statusColorRes = R.color.text_success
                statusIconRes = R.drawable.ic_check_circle
            }
            result.threatLevel < 65 -> {
                statusLabel = "Waspada"
                statusColorRes = R.color.text_warning
                statusIconRes = R.drawable.ic_alert_triangle
            }
            else -> {
                statusLabel = "Bahaya"
                statusColorRes = R.color.text_danger
                statusIconRes = R.drawable.ic_alert_triangle
            }
        }

        findViewById<TextView>(R.id.textStatusLabel).text = statusLabel
        findViewById<ImageView>(R.id.iconStatus).setImageResource(statusIconRes)
        val color = ContextCompat.getColor(this, statusColorRes)
        findViewById<TextView>(R.id.textScore).setTextColor(color)
        findViewById<TextView>(R.id.textStatusLabel).setTextColor(color)
        findViewById<ImageView>(R.id.iconStatus).setColorFilter(color)

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

        updateSignalCard(R.id.cardBssid, "Alamat wifi", R.drawable.ic_router, result.bssidChangeScore)
        updateSignalCard(R.id.cardTls, "Sertifikat situs", R.drawable.ic_lock, result.tlsChangeScore)
        updateSignalCard(R.id.cardSignal, "Sinyal", R.drawable.ic_router, result.signalInstabilityScore)
        updateSignalCard(R.id.cardLatency, "Latency", R.drawable.ic_router, result.latencyDeviationScore)
        updateSignalCard(R.id.cardArp, "Lalu lintas data", R.drawable.ic_share, result.configChangeScore)
    }

    private fun updateSignalCard(
        includeId: Int,
        label: String,
        leadingIconRes: Int,
        score: Int
    ) {
        val cardRoot = findViewById<View>(includeId) ?: return
        cardRoot.findViewById<TextView>(R.id.textCardLabel).text = label
        cardRoot.findViewById<ImageView>(R.id.iconCardLeading).setImageResource(leadingIconRes)

        val statusText = cardRoot.findViewById<TextView>(R.id.textCardStatus)
        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconCardStatus)

        if (score < 35) {
            statusText.text = "Normal"
            statusIcon.setImageResource(R.drawable.ic_check_circle)
        } else {
            statusText.text = "Berubah"
            statusIcon.setImageResource(R.drawable.ic_alert_triangle)
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
