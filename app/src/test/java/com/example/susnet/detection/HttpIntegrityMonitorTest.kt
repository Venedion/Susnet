package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HttpIntegrityMonitorTest {

    @Test
    fun testClassify204EmptyReturnsClean() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = 204,
            bodyLength = 0,
            hasRedirect = false,
            error = null
        )
        assertEquals(HttpIntegrityMonitor.Outcome.CLEAN, outcome)
    }

    @Test
    fun testClassify204WithBodyReturnsIntercepted() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = 204,
            bodyLength = 10,
            hasRedirect = false,
            error = null
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INTERCEPTED, outcome)
    }

    @Test
    fun testClassify302ReturnsIntercepted() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = 302,
            bodyLength = 0,
            hasRedirect = true,
            error = null
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INTERCEPTED, outcome)
    }

    @Test
    fun testClassify200WithHtmlReturnsIntercepted() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = 200,
            bodyLength = 250,
            hasRedirect = false,
            error = null
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INTERCEPTED, outcome)
    }

    @Test
    fun testClassify200EmptyReturnsIntercepted() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = 200,
            bodyLength = 0,
            hasRedirect = false,
            error = null
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INTERCEPTED, outcome)
    }

    @Test
    fun testClassifyTimeoutReturnsInconclusive() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = null,
            bodyLength = 0,
            hasRedirect = false,
            error = SocketTimeoutException("Timeout")
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INCONCLUSIVE, outcome)
    }

    @Test
    fun testClassifyUnknownHostExceptionReturnsInconclusive() {
        val outcome = HttpIntegrityMonitor.classify(
            statusCode = null,
            bodyLength = 0,
            hasRedirect = false,
            error = UnknownHostException("Unknown host")
        )
        assertEquals(HttpIntegrityMonitor.Outcome.INCONCLUSIVE, outcome)
    }
}
