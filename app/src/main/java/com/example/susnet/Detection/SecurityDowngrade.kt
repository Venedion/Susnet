package com.example.susnet.detection

import com.example.susnet.detection.WifiSecurityChecker.SecurityType

object SecurityDowngrade {

    fun rank(type: SecurityType): Int? {
        return when (type) {
            SecurityType.OPEN -> 0
            SecurityType.WEP -> 1
            SecurityType.ENHANCED_OPEN -> 2
            SecurityType.PERSONAL -> 3
            SecurityType.ENTERPRISE -> 4
            SecurityType.UNKNOWN -> null
        }
    }

    fun downgradeScore(baseline: SecurityType?, current: SecurityType): Int {
        if (baseline == null) return 0
        val baselineRank = rank(baseline) ?: return 0
        val currentRank = rank(current) ?: return 0

        if (currentRank >= baselineRank) return 0

        return when (current) {
            SecurityType.OPEN -> 85
            SecurityType.WEP -> 70
            else -> 50
        }
    }

    fun nextBaseline(baseline: SecurityType?, current: SecurityType): SecurityType? {
        if (current == SecurityType.UNKNOWN) return baseline
        if (baseline == null) return current

        val baselineRank = rank(baseline)
        val currentRank = rank(current)

        if (baselineRank != null && currentRank != null && currentRank > baselineRank) {
            return current
        }

        return baseline
    }
}
