package com.example.koistock.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    private const val BOM = "\uFEFF"
    private const val HEADER = "khu,kệ,sku,tên,soLuongDem,kyVong,chenhLech,trangThai,thoiDiem"

    fun toCsv(rows: List<CountRow>, atMillis: Long): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(atMillis))
        return buildString {
            append(BOM)
            append(HEADER)
            append('\n')
            rows.forEach { row ->
                append(escape(row.locationCode.substringBefore('-')))
                append(',')
                append(escape(row.locationCode))
                append(',')
                append(escape(row.sku))
                append(',')
                append(escape(row.name))
                append(',')
                append(row.counted)
                append(',')
                append(row.expected)
                append(',')
                append(row.counted - row.expected)
                append(',')
                append(row.status.name)
                append(',')
                append(timestamp)
                append('\n')
            }
        }
    }

    private fun escape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
