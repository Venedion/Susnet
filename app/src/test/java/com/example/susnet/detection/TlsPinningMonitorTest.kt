package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class TlsPinningMonitorTest {

    @Test
    fun testClassifyNull() {
        assertEquals(TlsPinningMonitor.Outcome.VALID, TlsPinningMonitor.classify(null))
    }

    @Test
    fun testClassifySslPeerUnverifiedException() {
        val ex = SSLPeerUnverifiedException("Peer unverified")
        assertEquals(TlsPinningMonitor.Outcome.CERT_FAILURE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifySslHandshakeException() {
        val ex = SSLHandshakeException("Handshake failed")
        assertEquals(TlsPinningMonitor.Outcome.INCONCLUSIVE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifySslHandshakeExceptionWithCertPathValidatorCause() {
        val ex = SSLHandshakeException("Pin verification failed").apply {
            initCause(CertPathValidatorException("Pin verification failed"))
        }
        assertEquals(TlsPinningMonitor.Outcome.CERT_FAILURE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifyCertPathValidatorExceptionDirect() {
        val ex = CertPathValidatorException("Cert error")
        assertEquals(TlsPinningMonitor.Outcome.CERT_FAILURE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifySslHandshakeExceptionConnectionClosed() {
        val ex = SSLHandshakeException("Connection closed by peer")
        assertEquals(TlsPinningMonitor.Outcome.INCONCLUSIVE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifyCertificateExceptionInCause() {
        val ex = RuntimeException("Wrapper", CertificateException("Cert error"))
        assertEquals(TlsPinningMonitor.Outcome.CERT_FAILURE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifySocketTimeoutException() {
        val ex = SocketTimeoutException("Timeout")
        assertEquals(TlsPinningMonitor.Outcome.INCONCLUSIVE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testClassifyUnknownHostException() {
        val ex = UnknownHostException("Unknown host")
        assertEquals(TlsPinningMonitor.Outcome.INCONCLUSIVE, TlsPinningMonitor.classify(ex))
    }

    @Test
    fun testTlsCardSummaryMappings() {
        // 1. tls CERT_FAILURE dan http INTERCEPTED
        assertEquals(
            "Sertifikat tidak cocok dan HTTP dialihkan",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.CERT_FAILURE, HttpIntegrityMonitor.Outcome.INTERCEPTED)
        )

        // 2. tls CERT_FAILURE (http lainnya)
        assertEquals(
            "Sertifikat tidak cocok",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.CERT_FAILURE, HttpIntegrityMonitor.Outcome.CLEAN)
        )
        assertEquals(
            "Sertifikat tidak cocok",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.CERT_FAILURE, HttpIntegrityMonitor.Outcome.INCONCLUSIVE)
        )

        // 3. http INTERCEPTED (tls bukan CERT_FAILURE)
        assertEquals(
            "Lalu lintas HTTP dialihkan",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.VALID, HttpIntegrityMonitor.Outcome.INTERCEPTED)
        )
        assertEquals(
            "Lalu lintas HTTP dialihkan",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.INCONCLUSIVE, HttpIntegrityMonitor.Outcome.INTERCEPTED)
        )

        // 4. tls VALID dan http CLEAN
        assertEquals(
            "Pin cocok, HTTP aman",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.VALID, HttpIntegrityMonitor.Outcome.CLEAN)
        )

        // 5. tls VALID dan http INCONCLUSIVE
        assertEquals(
            "Pin cocok, HTTP tidak terperiksa",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.VALID, HttpIntegrityMonitor.Outcome.INCONCLUSIVE)
        )

        // 6. tls INCONCLUSIVE dan http CLEAN
        assertEquals(
            "HTTP aman, sertifikat tidak terperiksa",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.INCONCLUSIVE, HttpIntegrityMonitor.Outcome.CLEAN)
        )

        // 7. tls INCONCLUSIVE dan http INCONCLUSIVE
        assertEquals(
            "Tidak dapat diperiksa saat ini",
            ThreatEvaluator.tlsCardSummary(TlsPinningMonitor.Outcome.INCONCLUSIVE, HttpIntegrityMonitor.Outcome.INCONCLUSIVE)
        )
    }
}
