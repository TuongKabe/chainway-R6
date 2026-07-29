package com.example.koistock.remote

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RemoteLocatePayloadParser {
    fun parse(data: Map<String, String>): RemoteLocateCommand? {
        val commandId = data["commandId"]?.trim().orEmpty()
        val sku = data["sku"]?.trim().orEmpty()
        val expiresAt = data["expiresAt"]?.trim().orEmpty()
        if (commandId.isEmpty() || sku.isEmpty() || expiresAt.isEmpty()) return null

        val expiresAtEpochMs = expiresAt.toLongOrNull() ?: parseIsoExpiry(expiresAt) ?: return null
        return RemoteLocateCommand(commandId, sku, expiresAtEpochMs)
    }

    private fun parseIsoExpiry(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
        }
    }
}
