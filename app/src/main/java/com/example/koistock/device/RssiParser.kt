package com.example.koistock.device

import kotlin.math.roundToInt

internal object RssiParser {
    private const val DefaultRssi = -70
    private val decimalPattern = Regex("[-+]?\\d+(?:[.,]\\d+)?")
    private val hexPattern = Regex("(?:0[xX][0-9A-Fa-f]{2}|(?=[0-9A-Fa-f]*[A-Fa-f])[0-9A-Fa-f]{2})")

    fun parse(raw: String?): Int {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return DefaultRssi

        if (hexPattern.matches(text)) {
            val hex = text.removePrefix("0x").removePrefix("0X").toInt(16)
            val signed = if (hex > 127) hex - 256 else hex
            return (if (signed > 0) -signed else signed).coerceIn(-100, 0)
        }

        decimalPattern.find(text)?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let { value ->
                val dbm = if (value > 0) -value else value
                return dbm.roundToInt().coerceIn(-100, 0)
            }

        return DefaultRssi
    }
}
