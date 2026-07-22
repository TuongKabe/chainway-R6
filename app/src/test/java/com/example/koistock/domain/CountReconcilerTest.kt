package com.example.koistock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CountReconcilerTest {
    @Test
    fun oneTagMatchesSkuWithDbStockTen() {
        val row = CountReconciler.reconcile(
            scope = CountScope.EntireWarehouse,
            countedBySku = mapOf("S1" to 1),
            expected = listOf(ExpectedItem("S1", "Áo", 10, "", "cái")),
            skusWithStockAnywhere = setOf("S1"),
        ).single()

        assertEquals(CountStatus.MATCH, row.status)
        assertEquals(1, row.scannedTagCount)
        assertEquals(10, row.dbStockQty)
        assertEquals("cái", row.unit)
    }

    @Test
    fun unscannedPositiveScopedStockIsMissing() {
        val row = CountReconciler.reconcile(
            CountScope.EntireWarehouse,
            emptyMap(),
            listOf(ExpectedItem("S1", "Áo", 10, "", "cái")),
            setOf("S1"),
        ).single()

        assertEquals(CountStatus.MISSING, row.status)
    }

    @Test
    fun scannedSkuWithStockElsewhereIsMisplacedInSpecificScope() {
        val row = CountReconciler.reconcile(
            CountScope.Location("A", setOf("A", "A-01")),
            mapOf("S1" to 1),
            emptyList(),
            setOf("S1"),
        ).single()

        assertEquals(CountStatus.MISPLACED, row.status)
        assertEquals("A", row.locationCode)
    }

    @Test
    fun scannedUnknownSkuIsExtra() {
        val row = CountReconciler.reconcile(
            CountScope.Location("A", setOf("A")),
            mapOf("SX" to 1),
            emptyList(),
            emptySet(),
        ).single()

        assertEquals(CountStatus.EXTRA, row.status)
    }

    @Test
    fun entireWarehouseNeverMarksScannedRowMisplaced() {
        val row = CountReconciler.reconcile(
            CountScope.EntireWarehouse,
            mapOf("S1" to 1),
            emptyList(),
            setOf("S1"),
        ).single()

        assertNotEquals(CountStatus.MISPLACED, row.status)
        assertEquals(CountStatus.EXTRA, row.status)
    }
}
