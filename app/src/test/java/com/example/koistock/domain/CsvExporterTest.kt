package com.example.koistock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun exportsScannedTagsAndDbStockAsSeparateUnits() {
        val csv = CsvExporter.toCsv(
            listOf(
                CountRow(
                    sku = "S1",
                    name = "Áo",
                    scannedTagCount = 1,
                    dbStockQty = 10,
                    unit = "cái",
                    locationCode = "A-01",
                    status = CountStatus.MATCH,
                ),
            ),
            atMillis = 0,
        )

        assertTrue(csv.contains("soTagDaQuet,tonDb,donVi,trangThai"))
        assertTrue(csv.contains("S1,Áo,1,10,cái,MATCH"))
        assertFalse(csv.contains("chenhLech"))
    }
}
