package com.example.susnet.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpIntegrityMonitor {

    data class Result(val score: Int, val detail: String)

    enum class Outcome { CLEAN, INTERCEPTED, INCONCLUSIVE }

    suspend fun evaluate(): Result = withContext(Dispatchers.IO) {
        var statusCode: Int? = null
        var bodyLength = 0
        var hasRedirect = false
        var error: Throwable? = null

        try {
            val url = URL("http://connectivitycheck.gstatic.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.connect()

            statusCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (statusCode in 300..399 || !location.isNullOrEmpty()) {
                hasRedirect = true
            }

            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            if (stream != null) {
                val buffer = ByteArray(512)
                val readBytes = stream.read(buffer, 0, 512)
                bodyLength = if (readBytes > 0) readBytes else 0
            }
            connection.disconnect()
        } catch (e: Throwable) {
            error = e
        }

        val outcome = classify(statusCode, bodyLength, hasRedirect, error)
        buildResult(outcome)
    }

    companion object {
        fun classify(
            statusCode: Int?,
            bodyLength: Int,
            hasRedirect: Boolean,
            error: Throwable?
        ): Outcome {
            if (error != null) return Outcome.INCONCLUSIVE
            if (statusCode == 204 && bodyLength == 0 && !hasRedirect) return Outcome.CLEAN
            if (statusCode != null && statusCode in 300..399) return Outcome.INTERCEPTED
            if (hasRedirect) return Outcome.INTERCEPTED
            if (statusCode == 200 && bodyLength > 0) return Outcome.INTERCEPTED
            return Outcome.INTERCEPTED
        }

        fun buildResult(outcome: Outcome): Result {
            return when (outcome) {
                Outcome.CLEAN -> Result(
                    0,
                    "Lalu lintas HTTP biasa aman dan tidak dialihkan."
                )
                Outcome.INTERCEPTED -> Result(
                    60,
                    "Permintaan HTTP biasa dialihkan atau isinya berubah. Ini umum pada WiFi yang meminta login (hotel, kafe, kampus), tetapi juga bisa berarti ada pihak yang mengubah lalu lintas. Jangan memasukkan data sensitif sebelum yakin."
                )
                Outcome.INCONCLUSIVE -> Result(
                    0,
                    "Pemeriksaan tidak dapat dilakukan (tidak ada respons). Hasil ini bukan jaminan aman."
                )
            }
        }
    }
}
