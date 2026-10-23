package com.example.susnet

enum class StatusLevel {
    AMAN,
    WASPADA,
    BAHAYA;

    companion object {
        fun fromScore(score: Int): StatusLevel {
            return when {
                score < 35 -> AMAN
                score < 65 -> WASPADA
                else -> BAHAYA
            }
        }
    }
}
