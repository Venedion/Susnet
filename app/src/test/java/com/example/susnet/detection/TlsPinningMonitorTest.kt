package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        assertEquals(TlsPinningMonitor.Outcome.CERT_FAILURE, TlsPinningMonitor.classify(ex))
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
}
