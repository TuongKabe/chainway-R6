package com.example.koistock.ui.assign

import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.EpcCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssignTagViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableScannedEpc = MutableStateFlow<String?>(null)
    val scannedEpc: StateFlow<String?> = mutableScannedEpc.asStateFlow()

    private val mutableDone = MutableStateFlow(false)
    val done: StateFlow<Boolean> = mutableDone.asStateFlow()

    fun scanBlank() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableScannedEpc.value = reader.scanSingle()?.epc
            mutableDone.value = false
        }
    }

    fun assign(sku: String, encodeStructured: Boolean) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            productRepo.getBySku(sku) ?: return@launch
            val current = mutableScannedEpc.value ?: return@launch
            val timestamp = now()
            val finalEpc = if (encodeStructured) {
                val serial = (tagRepo.listBySku(sku).size + 1).toString().padStart(4, '0')
                val newEpc = EpcCodec.encode(sku, serial)
                reader.writeEpc(current, newEpc)
                newEpc
            } else {
                current
            }

            tagRepo.upsert(
                TagMapping(
                    epc = finalEpc,
                    sku = sku,
                    status = "active",
                    updatedAt = timestamp,
                    origin = "app",
                ),
            )
            reader.beep()
            mutableDone.value = true
        }
    }
}
