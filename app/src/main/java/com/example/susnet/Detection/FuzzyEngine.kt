package com.example.susnet.detection

private enum class Level { LOW, MEDIUM, HIGH }
private enum class Threat { AMAN, WASPADA, BAHAYA }

private data class Rule(
    val bssid: Level? = null,
    val tls: Level? = null,
    val signal: Level? = null,
    val latency: Level? = null,
    val consequent: Threat
)

object FuzzyEngine {

    private fun trapezoid(x: Double, a: Double, b: Double, c: Double, d: Double): Double {
        if (x <= a || x >= d) return 0.0
        if (x in b..c) return 1.0
        return if (x < b) (x - a) / (b - a) else (d - x) / (d - c)
    }

    private fun low(x: Double) = trapezoid(x, 0.0, 0.0, 20.0, 50.0)
    private fun medium(x: Double) = trapezoid(x, 20.0, 50.0, 50.0, 80.0)
    private fun high(x: Double) = trapezoid(x, 50.0, 80.0, 100.0, 100.0)

    private fun degrees(x: Int): Map<Level, Double> = mapOf(
        Level.LOW to low(x.toDouble()),
        Level.MEDIUM to medium(x.toDouble()),
        Level.HIGH to high(x.toDouble())
    )

    private val rules = listOf(
        Rule(bssid = Level.HIGH, consequent = Threat.BAHAYA),
        Rule(tls = Level.HIGH, consequent = Threat.BAHAYA),
        Rule(bssid = Level.MEDIUM, tls = Level.MEDIUM, consequent = Threat.BAHAYA),
        Rule(signal = Level.HIGH, tls = Level.MEDIUM, consequent = Threat.BAHAYA),
        Rule(signal = Level.HIGH, bssid = Level.MEDIUM, consequent = Threat.BAHAYA),
        Rule(latency = Level.HIGH, tls = Level.MEDIUM, consequent = Threat.BAHAYA),
        Rule(latency = Level.HIGH, bssid = Level.MEDIUM, consequent = Threat.BAHAYA),
        Rule(bssid = Level.MEDIUM, consequent = Threat.WASPADA),
        Rule(tls = Level.MEDIUM, consequent = Threat.WASPADA),
        Rule(signal = Level.HIGH, consequent = Threat.WASPADA),
        Rule(signal = Level.MEDIUM, consequent = Threat.WASPADA),
        Rule(latency = Level.HIGH, consequent = Threat.WASPADA),
        Rule(latency = Level.MEDIUM, consequent = Threat.WASPADA),
        Rule(bssid = Level.LOW, tls = Level.LOW, signal = Level.LOW, latency = Level.LOW, consequent = Threat.AMAN)
    )

    private fun firingStrength(
        rule: Rule,
        bssidDeg: Map<Level, Double>,
        tlsDeg: Map<Level, Double>,
        signalDeg: Map<Level, Double>,
        latencyDeg: Map<Level, Double>
    ): Double {
        val parts = mutableListOf<Double>()
        rule.bssid?.let { parts.add(bssidDeg.getValue(it)) }
        rule.tls?.let { parts.add(tlsDeg.getValue(it)) }
        rule.signal?.let { parts.add(signalDeg.getValue(it)) }
        rule.latency?.let { parts.add(latencyDeg.getValue(it)) }
        return parts.minOrNull() ?: 0.0
    }

    private fun outputMembership(x: Double, threat: Threat): Double = when (threat) {
        Threat.AMAN -> trapezoid(x, 0.0, 0.0, 20.0, 50.0)
        Threat.WASPADA -> trapezoid(x, 20.0, 50.0, 50.0, 80.0)
        Threat.BAHAYA -> trapezoid(x, 50.0, 80.0, 100.0, 100.0)
    }

    fun computeThreatLevel(
        bssidChangeScore: Int,
        tlsChangeScore: Int,
        signalInstabilityScore: Int,
        latencyDeviationScore: Int
    ): Int {
        val bssidDeg = degrees(bssidChangeScore)
        val tlsDeg = degrees(tlsChangeScore)
        val signalDeg = degrees(signalInstabilityScore)
        val latencyDeg = degrees(latencyDeviationScore)

        val ruleStrengths = rules.map { rule ->
            rule.consequent to firingStrength(rule, bssidDeg, tlsDeg, signalDeg, latencyDeg)
        }

        val strengthPerThreat = Threat.values().associateWith { threat ->
            ruleStrengths.filter { it.first == threat }.maxOfOrNull { it.second } ?: 0.0
        }

        var numerator = 0.0
        var denominator = 0.0

        for (i in 0..100) {
            val x = i.toDouble()
            val aggregated = Threat.values().maxOf { threat ->
                minOf(strengthPerThreat.getValue(threat), outputMembership(x, threat))
            }
            numerator += x * aggregated
            denominator += aggregated
        }

        return if (denominator == 0.0) 0 else (numerator / denominator).toInt()
    }
}