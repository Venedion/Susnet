package com.example.susnet.detection

import android.content.Context
import android.net.wifi.WifiManager
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.SusnetDatabase

object ThreatEvaluatorFactory {

    @Volatile
    private var evaluatorInstance: ThreatEvaluator? = null

    @Volatile
    private var signalMonitorInstance: SignalStabilityMonitor? = null

    @Synchronized
    fun get(context: Context): ThreatEvaluator {
        val existing = evaluatorInstance
        if (existing != null) return existing

        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val baselineStore = BaselineStore(appContext)
        val database = SusnetDatabase.getInstance(appContext)

        val tlsMonitor = TlsPinningMonitor(appContext).apply { start() }
        val signalMonitor = SignalStabilityMonitor(wifiManager)
        signalMonitorInstance = signalMonitor

        val evaluator = ThreatEvaluator(
            evilTwinDetector = EvilTwinDetector(wifiManager, baselineStore),
            tlsPinningMonitor = tlsMonitor,
            httpIntegrityMonitor = HttpIntegrityMonitor(),
            signalStabilityMonitor = signalMonitor,
            latencyMonitor = LatencyMonitor(),
            networkConfigMonitor = NetworkConfigMonitor(appContext, database.networkConfigDao()),
            baselineStore = baselineStore,
            detectionEventDao = database.detectionEventDao(),
            wifiSecurityChecker = WifiSecurityChecker(appContext)
        )

        evaluatorInstance = evaluator
        return evaluator
    }

    @Synchronized
    fun signalMonitor(context: Context): SignalStabilityMonitor {
        val existing = signalMonitorInstance
        if (existing != null) return existing
        get(context)
        return signalMonitorInstance!!
    }
}
