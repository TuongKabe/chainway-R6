package com.example.koistock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CountReconcilerTest {
    private val expected = listOf(
        ExpectedItem("S1", "Áo", 3, "A-03"),
        ExpectedItem("S2", "Quần", 2, "A-03"),
        ExpectedItem("S3", "Nón", 1, "B-01"),
    )

    @Test
    fun match_whenCountedEqualsExpectedInZone() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3), expected)
        assertEquals(CountStatus.MATCH, rows.first { it.sku == "S1" }.status)
    }

    @Test
    fun missing_whenExpectedButNotCounted() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3), expected)
        assertEquals(CountStatus.MISSING, rows.first { it.sku == "S2" }.status)
    }

    @Test
    fun misplaced_whenCountedButHomeIsOtherZone() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3, "S3" to 1), expected)
        assertEquals(CountStatus.MISPLACED, rows.first { it.sku == "S3" }.status)
    }

    @Test
    fun extra_whenCountedUnknownSku() {
        val rows = CountReconciler.reconcile("A-03", mapOf("SX" to 1), expected)
        assertEquals(CountStatus.EXTRA, rows.first { it.sku == "SX" }.status)
    }
}
