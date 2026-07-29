package com.example.koistock.remote

data class RemoteLocateCommand(
    val commandId: String,
    val sku: String,
    val expiresAtEpochMs: Long,
) {
    fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs > expiresAtEpochMs
}
