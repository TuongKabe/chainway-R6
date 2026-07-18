package com.example.koistock.data.remote

interface GsheetWriteRepo {
    suspend fun queueActiveCellWrite(request: ActiveCellWriteRequest): ActiveCellWriteQueueResult
}

data class ActiveCellWriteRequest(
    val requestId: String,
    val deviceId: String,
    val source: String = "assign_tag",
    val epc: String,
    val sku: String,
    val barcode: String,
    val sheetTarget: String = "koistock",
    val actor: String? = null,
    val timestamp: Long,
)

sealed interface ActiveCellWriteQueueResult {
    data class Success(
        val requestId: String,
        val status: String,
        val jobId: String?,
        val message: String?,
    ) : ActiveCellWriteQueueResult

    data class Error(
        val message: String,
    ) : ActiveCellWriteQueueResult
}

class HttpGsheetWriteRepository(
    private val api: KoiApiService,
) : GsheetWriteRepo {
    override suspend fun queueActiveCellWrite(request: ActiveCellWriteRequest): ActiveCellWriteQueueResult {
        return try {
            val dto = api.queueActiveCellWrite(
                ActiveCellWriteRequestDto(
                    requestId = request.requestId,
                    deviceId = request.deviceId,
                    source = request.source,
                    epc = request.epc,
                    sku = request.sku,
                    barcode = request.barcode,
                    sheetTarget = request.sheetTarget,
                    actor = request.actor,
                    timestamp = request.timestamp,
                ),
            )
            ActiveCellWriteQueueResult.Success(
                requestId = dto.requestId,
                status = dto.status,
                jobId = dto.jobId,
                message = dto.message,
            )
        } catch (error: Exception) {
            ActiveCellWriteQueueResult.Error(
                error.message ?: "Không đẩy được lệnh ghi Google Sheet",
            )
        }
    }
}
