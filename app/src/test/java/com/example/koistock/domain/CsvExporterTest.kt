package com.example.koistock.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun csv_startsWithBom_andHeader() {
        val csv = CsvExporter.toCsv(emptyList(), 0)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("khu,kệ,sku,tên,soLuongDem,kyVong,chenhLech,trangThai,thoiDiem"))
    }

    @Test
    fun csv_writesRowWithDeltaAndStatus() {
        val row = CountRow("S1", "Áo", 2, 3, "A-03", CountStatus.MISSING)
        val csv = CsvExporter.toCsv(listOf(row), 1000)
        val line = csv.lines().first { it.startsWith("A,") }
        assertTrue(line.contains(",S1,Áo,2,3,-1,MISSING,"))
    }

    @Test
    fun csv_escapesCommaInName() {
        val row = CountRow("S1", "Áo, size L", 1, 1, "A-03", CountStatus.MATCH)
        val csv = CsvExporter.toCsv(listOf(row), 0)
        assertTrue(csv.contains("\"Áo, size L\""))
    }
}
