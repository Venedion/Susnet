package com.example.susnet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.wifi.WifiManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.SusnetDatabase
import com.example.susnet.detection.EvilTwinDetector
import com.example.susnet.detection.LatencyMonitor
import com.example.susnet.detection.SignalStabilityMonitor
import com.example.susnet.detection.ThreatEvaluator
import com.example.susnet.detection.TlsPinningMonitor
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var threatEvaluator: ThreatEvaluator
    private lateinit var tlsPinningMonitor: TlsPinningMonitor
    private var latestResult: ThreatEvaluator.ThreatResult? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Log.d("MainActivity", "Location permission granted")
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            runEvaluation()
        } else {
            Log.d("MainActivity", "Location permission denied")
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkLocationPermissions()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupThreatEvaluator()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            runEvaluation()
        }

        findViewById<View>(R.id.buttonDetail).setOnClickListener {
            openDetailScreen()
        }
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
        }
        startActivity(intent)
    }

        private fun checkLocationPermissions() {
            val fineCheck =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseCheck =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

            if (fineCheck != PackageManager.PERMISSION_GRANTED || coarseCheck != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                Log.d("MainActivity", "Permissions already granted")
            }
        }

        private fun setupThreatEvaluator() {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val baselineStore = BaselineStore(applicationContext)
            tlsPinningMonitor = TlsPinningMonitor(applicationContext)
            tlsPinningMonitor.start()

            threatEvaluator = ThreatEvaluator(
                evilTwinDetector = EvilTwinDetector(wifiManager, baselineStore),
                tlsPinningMonitor = tlsPinningMonitor,
                signalStabilityMonitor = SignalStabilityMonitor(wifiManager),
                latencyMonitor = LatencyMonitor(),
                baselineStore = baselineStore,
                detectionEventDao = SusnetDatabase.getInstance(applicationContext)
                    .detectionEventDao()
            )
        }

        private fun runEvaluation() {
            lifecycleScope.launch {
                val result = threatEvaluator.evaluateCurrentNetwork()
                updateUi(result)
            }
        }

        private fun updateUi(result: ThreatEvaluator.ThreatResult) {
            latestResult = result
            findViewById<TextView>(R.id.textSsid).text = result.currentSsid ?: "Tidak terhubung"
            findViewById<TextView>(R.id.textScore).text = result.threatLevel.toString()

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
                result.latencyDeviationScore
            ).count { it >= 35 }

            val detailText = if (abnormalCount == 0) {
                "Semua sinyal terlihat normal"
            } else {
                "$abnormalCount tanda tidak biasa terdeteksi"
            }
            findViewById<TextView>(R.id.textStatusDetail).text = detailText
            findViewById<TextView>(R.id.textStatusDetail).setTextColor(color)

            updateSignalCard(
                R.id.cardBssid,
                "Alamat wifi",
                R.drawable.ic_router,
                result.bssidChangeScore
            )
            updateSignalCard(
                R.id.cardTls,
                "Sertifikat situs",
                R.drawable.ic_lock,
                result.tlsChangeScore
            )
            updateSignalCard(
                R.id.cardSignal,
                "Sinyal",
                R.drawable.ic_router,
                result.signalInstabilityScore
            )
            updateSignalCard(
                R.id.cardLatency,
                "Latency",
                R.drawable.ic_router,
                result.latencyDeviationScore
            )
            updateArpCardPlaceholder()
        }

        private fun updateSignalCard(
            includeId: Int,
            label: String,
            leadingIconRes: Int,
            score: Int
        ) {
            val cardRoot = findViewById<View>(includeId)
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

        private fun updateArpCardPlaceholder() {
            val cardRoot = findViewById<View>(R.id.cardArp)
            cardRoot.findViewById<TextView>(R.id.textCardLabel).text = "Lalu lintas data"
            cardRoot.findViewById<ImageView>(R.id.iconCardLeading)
                .setImageResource(R.drawable.ic_share)
            cardRoot.findViewById<TextView>(R.id.textCardStatus).text = "Belum aktif"
            cardRoot.findViewById<ImageView>(R.id.iconCardStatus)
                .setImageResource(R.drawable.ic_check_circle)
        }

        override fun onDestroy() {
            tlsPinningMonitor.stop()
            super.onDestroy()
        }
    }