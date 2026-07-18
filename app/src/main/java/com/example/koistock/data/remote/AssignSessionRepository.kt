package com.example.koistock.data.remote

data class AssignSessionItem(
    val itemCode: String,
    val itemName: String,
)

data class AssignSessionSnapshot(
    val id: String,
    val itemCode: String,
    val requestedBy: String? = null,
    val status: String,
    val scannedEpc: String? = null,
    val scannedSerialNo: String? = null,
    val warehouse: String? = null,
    val locationCode: String? = null,
    val expiresAt: String,
    val note: String? = null,
    val item: AssignSessionItem? = null,
)

sealed interface AssignSessionActionResult {
    data class Success(val session: AssignSessionSnapshot) : AssignSessionActionResult
    data class Error(val message: String) : AssignSessionActionResult
}

interface AssignSessionRepo {
    suspend fun getLatestWaiting(): AssignSessionSnapshot?
    suspend fun submitScan(sessionId: String, epc: String, serialNo: String? = null): AssignSessionActionResult
    suspend fun confirm(sessionId: String): AssignSessionActionResult
}
