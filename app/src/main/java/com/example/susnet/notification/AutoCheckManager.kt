package com.example.susnet.notification

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.example.susnet.StatusLevel
import com.example.susnet.detection.ThreatEvaluatorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutoCheckManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRegistered = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var debounceJob: Job? = null

    @Volatile
    private var lastBssid: String? = null

    @Volatile
    private var lastCheckAt: Long = 0L

    @Synchronized
    fun start(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("susnet_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notify_enabled", false)
        if (!enabled) return

        if (isRegistered) return

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("SusnetAudit", "AutoCheck trigger source=onAvailable")
                triggerAutoCheck(appContext)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                Log.d("SusnetAudit", "AutoCheck trigger source=onCapabilitiesChanged")
                triggerAutoCheck(appContext)
            }

            override fun onLost(network: Network) {
                lastBssid = null
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            isRegistered = true
            Log.d("SusnetAudit", "AutoCheckManager registered network callback")
        } catch (e: Exception) {
            Log.e("SusnetAudit", "Failed to register network callback", e)
        }
    }

    @Synchronized
    fun stop(context: Context) {
        if (!isRegistered) return

        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let {
            try {
                cm?.unregisterNetworkCallback(it)
                Log.d("SusnetAudit", "AutoCheckManager unregistered network callback")
            } catch (e: Exception) {
                Log.e("SusnetAudit", "Failed to unregister network callback", e)
            }
        }
        networkCallback = null
        isRegistered = false
        debounceJob?.cancel()
        lastBssid = null
        lastCheckAt = 0L
    }

    private fun triggerAutoCheck(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(3000L)
            try {
                val prefs = context.getSharedPreferences("susnet_prefs", Context.MODE_PRIVATE)
                val notifyEnabled = prefs.getBoolean("notify_enabled", false)
                if (!notifyEnabled) return@launch

                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val currentBssid = wifiManager?.connectionInfo?.bssid
                val now = System.currentTimeMillis()

                if (AutoCheckPolicy.isBssidInvalid(currentBssid)) {
                    Log.d("SusnetAudit", "AutoCheck skipped reason=bssid_unreadable")
                    return@launch
                }

                if (!AutoCheckPolicy.shouldRun(lastBssid, currentBssid, lastCheckAt, now)) {
                    Log.d("SusnetAudit", "AutoCheck skipped reason=same_network_recent")
                    return@launch
                }

                lastBssid = currentBssid
                lastCheckAt = now

                val evaluator = ThreatEvaluatorFactory.get(context)
                val result = evaluator.evaluateCurrentNetwork()
                Log.d("SusnetAudit", "AutoCheck evaluation: isConnected=${result.isConnected}, threatLevel=${result.threatLevel}, ssid=${result.currentSsid}")

                if (!result.isConnected) {
                    Log.d("SusnetAudit", "AutoCheck ssid_unreadable (mungkin izin lokasi latar belakang)")
                } else if (result.currentSsid != null) {
                    val level = StatusLevel.fromScore(result.threatLevel)
                    if (level != StatusLevel.AMAN) {
                        NotificationHelper.notify(context, result.currentSsid, level)
                    }
                }
            } catch (e: Exception) {
                Log.e("SusnetAudit", "AutoCheck evaluation failed", e)
            }
        }
    }
}
