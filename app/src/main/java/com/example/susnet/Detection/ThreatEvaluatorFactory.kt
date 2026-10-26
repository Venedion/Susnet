package com.example.susnet.detection

import android.content.Context
import android.net.wifi.WifiManager
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.SusnetDatabase

object ThreatEvaluatorFactory {

    fun create(context: Context, existingMonitor: TlsPinningMonitor? = null): Pair<ThreatEvaluator, TlsPinningMonitor> {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val baselineStore = BaselineStore(appContext)
        val database = SusnetDatabase.getInstance(appContext)

        val monitor = existingMonitor ?: TlsPinningMonitor(appContext).apply { start() }

        val evaluator = ThreatEvaluator(
            evilTwinDetector = EvilTwinDetector(wifiManager, baselineStore),
            tlsPinningMonitor = monitor,
            httpIntegrityMonitor = HttpIntegrityMonitor(),
            signalStabilityMonitor = SignalStabilityMonitor(wifiManager),
            latencyMonitor = LatencyMonitor(),
            networkConfigMonitor = NetworkConfigMonitor(appContext, database.networkConfigDao()),
            baselineStore = baselineStore,
            detectionEventDao = database.detectionEventDao(),
            wifiSecurityChecker = WifiSecurityChecker(appContext)
        )

        return Pair(evaluator, monitor)
    }
}
