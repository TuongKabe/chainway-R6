package com.example.koistock.domain

object EpcCodec {
    private const val Prefix = "KOI"
    private const val Separator = "-"

    fun encode(sku: String, serial: String): String = "$Prefix$Separator$sku$Separator$serial"

    fun maskForSku(sku: String): String = "$Prefix$Separator$sku$Separator"

    fun isStructured(epc: String): Boolean {
        return epc.startsWith("$Prefix$Separator") && epc.count { it == '-' } >= 2
    }

    fun skuOf(epc: String): String? {
        if (!isStructured(epc)) return null
        val parts = epc.split(Separator)
        return if (parts.size >= 3) {
            parts.subList(1, parts.size - 1).joinToString(Separator)
        } else {
            null
        }
    }
}
