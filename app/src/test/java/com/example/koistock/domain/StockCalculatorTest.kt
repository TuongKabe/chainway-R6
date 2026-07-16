package com.example.koistock.domain

import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.Transaction
import com.example.koistock.data.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class StockCalculatorTest {
    private fun tx(type: TxType, sku: String, delta: Long) =
        Transaction("id-${System.nanoTime()}", type, sku, null, delta, null, "d", 0)

    @Test
    fun bulk_sumsDeltas() {
        val list = listOf(tx(TxType.IN, "B1", 10), tx(TxType.OUT, "B1", -3), tx(TxType.IN, "B2", 5))
        assertEquals(7L, StockCalculator.bulkQuantity(list, "B1"))
    }

    @Test
    fun bulk_adjustOverrides_isTreatedAsDelta() {
        val list = listOf(tx(TxType.IN, "B1", 10), tx(TxType.ADJUST, "B1", -2))
        assertEquals(8L, StockCalculator.bulkQuantity(list, "B1"))
    }

    @Test
    fun serialized_countsActiveTagsOfSku() {
        val tags = listOf(
            TagMapping("e1", "S1", status = "active"),
            TagMapping("e2", "S1", status = "void"),
            TagMapping("e3", "S1", status = "active"),
            TagMapping("e4", "S2", status = "active"),
        )
        assertEquals(2, StockCalculator.serializedQuantity(tags, "S1"))
    }

    @Test
    fun serialized_filtersByLocation_whenGiven() {
        val tags = listOf(
            TagMapping("e1", "S1", status = "active", locationCode = "A-03"),
            TagMapping("e2", "S1", status = "active", locationCode = "A-05"),
        )
        assertEquals(1, StockCalculator.serializedQuantity(tags, "S1", "A-03"))
    }
}
