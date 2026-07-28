package com.example.koistock.ui.locate

import com.example.koistock.data.model.TagMapping
import org.junit.Assert.assertEquals
import org.junit.Test

class EpcLocationTextTest {
    @Test
    fun position_prefersLocationCodeOverBin() {
        val tag = TagMapping("E", "S", locationCode = " A-01 ", bin = "BIN-7")
        assertEquals("A-01", positionText(tag))
    }

    @Test
    fun position_fallsBackToBinThenUnassigned() {
        assertEquals("BIN-7", positionText(TagMapping("E", "S", bin = " BIN-7 ")))
        assertEquals("Chưa gán", positionText(TagMapping("E", "S")))
    }

    @Test
    fun warehouse_usesSeparateWarehouseField() {
        assertEquals("KHO-A", warehouseText(TagMapping("E", "S", warehouse = " KHO-A ")))
        assertEquals("Chưa gán", warehouseText(TagMapping("E", "S")))
    }
}
