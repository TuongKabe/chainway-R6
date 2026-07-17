package com.example.koistock.ui.inout

import com.example.koistock.data.model.TxType
import com.example.koistock.data.remote.CommitStockResult
import com.example.koistock.data.remote.StockCommandRepo
import com.example.koistock.data.remote.StockMovement
import com.example.koistock.data.remote.TagRepo
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

enum class InOutMode { IN, OUT }

class InOutViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val stockCommandRepo: StockCommandRepo,
    private val deviceId: String,
    private val newCommandId: () -> String,
    private val scope: CoroutineScope,
    private val profile: ScanProfile = ScanProfile(),
) {
    private val mutableMode = MutableStateFlow(InOutMode.IN)
    val mode: StateFlow<InOutMode> = mutableMode.asStateFlow()

    private val mutablePending = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pending: StateFlow<Map<String, Int>> = mutablePending.asStateFlow()

    private val mutableCommitState = MutableStateFlow<CommitStockResult?>(null)
    val commitState: StateFlow<CommitStockResult?> = mutableCommitState.asStateFlow()

    private val mutableScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = mutableScanning.asStateFlow()

    private val seenEpcs = mutableSetOf<String>()
    private val epcBySku = mutableMapOf<String, MutableList<String>>()
    private var commandId = newCommandId()
    private var scanJob: Job? = null
    private var triggerJob: Job? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reader.applyScanConfig(profile) }
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (!pressed) return@collect
                when (profile.triggerMode) {
                    // Bóp 1 lần: một đợt quét ngắn rồi tự dừng.
                    TriggerMode.SINGLE -> if (mutableScanning.value) stopScan() else burstOnce()
                    // Liên tục: bóp lần 1 bắt đầu, bóp lần 2 kết thúc.
                    TriggerMode.CONTINUOUS -> if (mutableScanning.value) stopScan() else startScan()
                }
            }
        }
    }

    private fun burstOnce() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            startScan()
            delay(SINGLE_BURST_MS)
            stopScan()
        }
    }

    fun setMode(mode: InOutMode) {
        mutableMode.value = mode
    }

    fun startScan() {
        seenEpcs.clear()
        epcBySku.clear()
        mutablePending.value = emptyMap()
        mutableCommitState.value = null
        reader.startInventory()
        mutableScanning.value = true
        scanJob?.cancel()
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { scanned ->
                if (!seenEpcs.add(scanned.epc)) return@collect
                val sku = tagRepo.getByEpc(scanned.epc)?.sku ?: return@collect
                epcBySku.getOrPut(sku) { mutableListOf() }.add(scanned.epc)
                mutablePending.update { current ->
                    current + (sku to ((current[sku] ?: 0) + 1))
                }
            }
        }
    }

    fun stopScan() {
        reader.stopInventory()
        scanJob?.cancel()
        mutableScanning.value = false
    }

    fun clear() {
        triggerJob?.cancel()
        scanJob?.cancel()
    }

    suspend fun confirm() {
        val type = if (mode.value == InOutMode.IN) TxType.IN else TxType.OUT
        val sign = if (mode.value == InOutMode.IN) 1L else -1L
        val movements = epcBySku.flatMap { (sku, epcs) ->
            epcs.map { epc -> StockMovement(sku, epc, sign, null) }
        }
        val result = stockCommandRepo.commit(commandId, type, deviceId, movements)
        mutableCommitState.value = result
        if (result is CommitStockResult.Success) {
            mutablePending.value = emptyMap()
            epcBySku.clear()
            seenEpcs.clear()
            commandId = newCommandId()
        }
    }

    private companion object {
        const val SINGLE_BURST_MS = 1500L
    }
}
