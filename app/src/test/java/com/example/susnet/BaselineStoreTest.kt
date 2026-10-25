package com.example.susnet

import com.example.susnet.data.BaselineStore
import com.example.susnet.data.Confidence
import org.junit.Assert.assertEquals
import org.junit.Test

class BaselineStoreTest {

    private val now = System.currentTimeMillis()
    private val dayMs = 24 * 60 * 60 * 1000L

    @Test
    fun test1_seenCount5_age1Day_returnsMedium() {
        val firstSeen = now - (1 * dayMs)
        assertEquals(Confidence.MEDIUM, BaselineStore.computeConfidence(5, firstSeen, now))
    }

    @Test
    fun test2_seenCount5_age3Days_returnsHigh() {
        val firstSeen = now - (3 * dayMs)
        assertEquals(Confidence.HIGH, BaselineStore.computeConfidence(5, firstSeen, now))
    }

    @Test
    fun test3_seenCount10_age2Days_returnsMedium() {
        val firstSeen = now - (2 * dayMs)
        assertEquals(Confidence.MEDIUM, BaselineStore.computeConfidence(10, firstSeen, now))
    }

    @Test
    fun test4_seenCount1_age10Days_returnsLow() {
        val firstSeen = now - (10 * dayMs)
        assertEquals(Confidence.LOW, BaselineStore.computeConfidence(1, firstSeen, now))
    }

    @Test
    fun test5_seenCount2_age0_returnsMedium() {
        assertEquals(Confidence.MEDIUM, BaselineStore.computeConfidence(2, now, now))
    }

    @Test
    fun test6_seenCount5_exactly3Days_returnsHigh() {
        val firstSeen = now - BaselineStore.MIN_TRUST_AGE_MS
        assertEquals(Confidence.HIGH, BaselineStore.computeConfidence(5, firstSeen, now))
    }

    @Test
    fun test7_seenCount5_3DaysMinus1Ms_returnsMedium() {
        val firstSeen = now - (BaselineStore.MIN_TRUST_AGE_MS - 1L)
        assertEquals(Confidence.MEDIUM, BaselineStore.computeConfidence(5, firstSeen, now))
    }
}
