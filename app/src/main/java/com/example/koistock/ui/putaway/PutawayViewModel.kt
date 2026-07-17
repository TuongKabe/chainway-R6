package com.example.koistock.ui.putaway

import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.model.Transaction
import com.example.koistock.data.model.TxType
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.data.remote.TransactionRepo
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.TriggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class PutawayViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val txRepo: TransactionRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
    private val profile: ScanProfile = ScanProfile(),
) {
    private val mutableLocationCode = MutableStateFlow<String?>(null)
    val locationCode: StateFlow<String?> = mutableLocationCode.asStateFlow()

    private val mutableScanned = MutableStateFlow<Set<String>>(emptySet())
    val scanned: StateFlow<Set<String>> = mutableScanned.asStateFlow()

    private val mutableScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = mutableScanning.asStateFlow()

    private var collectJob: Job? = null
    private var triggerJob: Job? = null
    private var availableShelves: Set<String> = emptySet()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reader.applyScanConfig(profile) }
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (!pressed) return@collect
                when (profile.triggerMode) {
                    // Bóp 1 lần: một đợt quét ngắn rồi tự dừng.
                    TriggerMode.SINGLE -> if (mutableScanning.value) stopCollect() else burstOnce()
                    // Liên tục: bóp lần 1 bắt đầu, bóp lần 2 kết thúc.
                    TriggerMode.CONTINUOUS -> if (mutableScanning.value) stopCollect() else startCollect()
                }
            }
        }
    }

    private fun burstOnce() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            startCollect()
            delay(SINGLE_BURST_MS)
            stopCollect()
        }
    }

    fun setAvailableShelves(codes: Set<String>) {
        availableShelves = codes
        if (mutableLocationCode.value !in codes) mutableLocationCode.value = null
    }

    fun setLocationByTag(locationCode: String) {
        mutableLocationCode.value = locationCode.takeIf { it in availableShelves }
    }

    fun startCollect() {
        mutableScanned.value = emptySet()
        reader.startInventory()
        mutableScanning.value = true
        collectJob?.cancel()
        collectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { tag ->
                mutableScanned.update { it + tag.epc }
            }
        }
    }

    fun stopCollect() {
        reader.stopInventory()
        collectJob?.cancel()
        mutableScanning.value = false
    }

    fun clear() {
        triggerJob?.cancel()
        collectJob?.cancel()
    }

    suspend fun applyAssign(): Int {
        val location = mutableLocationCode.value ?: return 0
        val timestamp = now()
        var count = 0

        for (epc in mutableScanned.value) {
            val tag = tagRepo.getByEpc(epc) ?: continue
            val product = productRepo.getBySku(tag.sku) ?: continue
            when (product.trackingMode) {
                TrackingMode.SERIALIZED -> tagRepo.upsert(
                    tag.copy(locationCode = location, updatedAt = timestamp, origin = "app"),
                )

                TrackingMode.BULK -> txRepo.append(
                    Transaction(
                        id = UUID.randomUUID().toString(),
                        type = TxType.MOVE,
                        sku = tag.sku,
                        epc = epc,
                        delta = 0,
                        locationCode = location,
                        deviceId = deviceId,
                        at = timestamp,
                    ),
                )
            }
            count++
        }
        return count
    }

    private companion object {
        const val SINGLE_BURST_MS = 1500L
    }
}
