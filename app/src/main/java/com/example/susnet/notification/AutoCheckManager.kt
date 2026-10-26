package com.example.susnet.notification

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
                triggerAutoCheck(appContext)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                triggerAutoCheck(appContext)
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
    }

    private fun triggerAutoCheck(context: Context) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(3000L)
            try {
                val prefs = context.getSharedPreferences("susnet_prefs", Context.MODE_PRIVATE)
                val notifyEnabled = prefs.getBoolean("notify_enabled", false)
                if (!notifyEnabled) return@launch

                val (evaluator, _) = ThreatEvaluatorFactory.create(context)
                val result = evaluator.evaluateCurrentNetwork()
                Log.d("SusnetAudit", "AutoCheck evaluation: isConnected=${result.isConnected}, threatLevel=${result.threatLevel}, ssid=${result.currentSsid}")

                if (result.isConnected && result.currentSsid != null) {
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
