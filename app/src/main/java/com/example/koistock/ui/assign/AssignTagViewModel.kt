package com.example.koistock.ui.assign

import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.remote.ActiveCellWriteQueueResult
import com.example.koistock.data.remote.ActiveCellWriteRequest
import com.example.koistock.data.remote.GsheetWriteRepo
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.TriggerMode
import com.example.koistock.domain.EpcCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AssignResult {
    data class Success(
        val epc: String,
        val sku: String,
        val barcode: String? = null,
        val note: String? = null,
    ) : AssignResult

    data class PartialSuccess(
        val epc: String,
        val sku: String,
        val message: String,
    ) : AssignResult

    data class Error(val message: String) : AssignResult
}

class AssignTagViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val gsheetWriteRepo: GsheetWriteRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
    private val profile: ScanProfile = ScanProfile(),
    private val defaultSheetTarget: String = "koistock",
) {
    private val mutableScannedEpc = MutableStateFlow<String?>(null)
    val scannedEpc: StateFlow<String?> = mutableScannedEpc.asStateFlow()

    private val mutableBarcode = MutableStateFlow("")
    val barcode: StateFlow<String> = mutableBarcode.asStateFlow()

    private val mutableDone = MutableStateFlow(false)
    val done: StateFlow<Boolean> = mutableDone.asStateFlow()

    private val mutableWorking = MutableStateFlow(false)
    val working: StateFlow<Boolean> = mutableWorking.asStateFlow()

    private val mutableResult = MutableStateFlow<AssignResult?>(null)
    val result: StateFlow<AssignResult?> = mutableResult.asStateFlow()

    private var triggerJob: Job? = null
    private var holdJob: Job? = null
    private var holdActive = false

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reader.applyScanConfig(profile) }
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (!pressed) return@collect
                when (profile.triggerMode) {
                    TriggerMode.SINGLE -> scanBlank()
                    TriggerMode.CONTINUOUS -> if (holdActive) stopHold() else startHold()
                }
            }
        }
    }

    private fun startHold() {
        holdActive = true
        holdJob?.cancel()
        holdJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (true) {
                val epc = reader.scanSingle()?.epc
                if (epc != null) {
                    mutableScannedEpc.value = epc
                    mutableDone.value = false
                }
            }
        }
    }

    private fun stopHold() {
        holdActive = false
        holdJob?.cancel()
    }

    fun updateBarcode(value: String) {
        mutableBarcode.value = value.trim()
    }

    fun clearBarcode() {
        mutableBarcode.value = ""
    }

    fun scanBlank() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val epc = reader.scanSingle()?.epc
            mutableScannedEpc.value = epc
            mutableDone.value = false
            if (epc == null) {
                mutableResult.value = AssignResult.Error("Không đọc được tag. Đưa tag lại gần đầu đọc rồi thử lại.")
            }
        }
    }

    fun assign(sku: String, encodeStructured: Boolean, pushBarcodeToSheet: Boolean) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableWorking.value = true
            try {
                val current = mutableScannedEpc.value
                if (current == null) {
                    mutableResult.value = AssignResult.Error("Chưa có EPC. Hãy quét tag trống trước.")
                    return@launch
                }
                val product = runCatching { productRepo.getBySku(sku) }.getOrNull()
                if (product == null) {
                    mutableResult.value = AssignResult.Error("Không tìm thấy SKU $sku trên hệ thống.")
                    return@launch
                }
                val barcodeValue = mutableBarcode.value.trim()
                if (pushBarcodeToSheet && barcodeValue.isBlank()) {
                    mutableResult.value = AssignResult.Error("Chưa có mã vạch. Hãy quét hoặc nhập barcode trước.")
                    return@launch
                }

                val timestamp = now()
                val finalEpc = if (encodeStructured) {
                    val serial = (tagRepo.listBySku(sku).size + 1).toString().padStart(4, '0')
                    val newEpc = EpcCodec.encode(sku, serial)
                    val wrote = reader.writeEpc(current, newEpc)
                    if (!wrote) {
                        mutableResult.value = AssignResult.Error("Ghi EPC lên tag thất bại. Giữ tag gần đầu đọc và thử lại.")
                        return@launch
                    }
                    newEpc
                } else {
                    current
                }

                runCatching {
                    tagRepo.upsert(
                        TagMapping(
                            epc = finalEpc,
                            sku = sku,
                            status = "active",
                            updatedAt = timestamp,
                            origin = "app",
                        ),
                    )
                }.onFailure {
                    mutableResult.value = AssignResult.Error("Lưu mapping lên backend thất bại: ${it.message ?: "lỗi mạng"}")
                    return@launch
                }

                if (pushBarcodeToSheet) {
                    when (
                        val queueResult = gsheetWriteRepo.queueActiveCellWrite(
                            ActiveCellWriteRequest(
                                requestId = UUID.randomUUID().toString(),
                                deviceId = deviceId,
                                epc = finalEpc,
                                sku = sku,
                                barcode = barcodeValue,
                                sheetTarget = defaultSheetTarget,
                                timestamp = timestamp,
                            ),
                        )
                    ) {
                        is ActiveCellWriteQueueResult.Success -> {
                            reader.beep()
                            mutableDone.value = true
                            mutableResult.value = AssignResult.Success(
                                epc = finalEpc,
                                sku = sku,
                                barcode = barcodeValue,
                                note = queueResult.message ?: "Đã xếp lệnh ghi Google Sheet (${queueResult.status}).",
                            )
                        }

                        is ActiveCellWriteQueueResult.Error -> {
                            mutableDone.value = true
                            mutableResult.value = AssignResult.PartialSuccess(
                                epc = finalEpc,
                                sku = sku,
                                message = "Đã gán tag nhưng chưa đẩy được Google Sheet: ${queueResult.message}",
                            )
                        }
                    }
                } else {
                    reader.beep()
                    mutableDone.value = true
                    mutableResult.value = AssignResult.Success(
                        epc = finalEpc,
                        sku = sku,
                        note = "Đã gán tag thành công.",
                    )
                }
            } finally {
                mutableWorking.value = false
            }
        }
    }

    fun acknowledgeResult() {
        mutableResult.value = null
    }

    fun clear() {
        triggerJob?.cancel()
        holdJob?.cancel()
    }
}
