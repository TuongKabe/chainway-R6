package com.example.koistock.data.remote

import com.example.koistock.data.model.TxType

data class StockMovement(
    val sku: String,
    val epc: String?,
    val delta: Long,
    val locationCode: String?,
)

sealed interface CommitStockResult {
    data class Success(val commandId: String) : CommitStockResult
    data class InsufficientStock(val sku: String, val available: Long, val requested: Long) : CommitStockResult
    data class InvalidTag(val epc: String, val reason: String) : CommitStockResult
}

interface StockCommandRepo {
    suspend fun commit(
        commandId: String,
        type: TxType,
        deviceId: String,
        movements: List<StockMovement>,
    ): CommitStockResult
}
