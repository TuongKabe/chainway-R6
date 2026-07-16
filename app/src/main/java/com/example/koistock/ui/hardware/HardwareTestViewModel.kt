package com.example.koistock.ui.hardware

import com.example.koistock.device.ConnectionState
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HardwareTestViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
) {
    val connectionState: StateFlow<ConnectionState> = reader.connectionState

    private val mutableSingleScan = MutableStateFlow<String?>(null)
    val singleScan: StateFlow<String?> = mutableSingleScan.asStateFlow()

    private val mutableBattery = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = mutableBattery.asStateFlow()

    private val mutableInventoryTags = MutableStateFlow<List<String>>(emptyList())
    val inventoryTags: StateFlow<List<String>> = mutableInventoryTags.asStateFlow()

    private val mutableLocateSignal = MutableStateFlow(0)
    val locateSignal: StateFlow<Int> = mutableLocateSignal.asStateFlow()

    private val mutableWriteResult = MutableStateFlow<String?>(null)
    val writeResult: StateFlow<String?> = mutableWriteResult.asStateFlow()

    private val mutableTriggerPressed = MutableStateFlow(false)
    val triggerPressed: StateFlow<Boolean> = mutableTriggerPressed.asStateFlow()

    private val mutableLastSeenEpc = MutableStateFlow<String?>(null)
    val lastSeenEpc: StateFlow<String?> = mutableLastSeenEpc.asStateFlow()

    private val mutableLogs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = mutableLogs.asStateFlow()

    private var inventoryJob: Job? = null
    private var locateJob: Job? = null
    private var triggerJob: Job? = null

    init {
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                mutableTriggerPressed.value = pressed
                addLog(if (pressed) "Trigger: nhấn" else "Trigger: nhả")
            }
        }
    }

    fun refreshBattery() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableBattery.value = reader.batteryPercent()
            addLog("Đọc pin: ${mutableBattery.value}%")
        }
    }

    fun scanSingle() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val epc = reader.scanSingle()?.epc
            mutableSingleScan.value = epc
            mutableLastSeenEpc.value = epc
            addLog("Quét đơn: ${epc ?: "không có dữ liệu"}")
        }
    }

    fun startInventory() {
        mutableInventoryTags.value = emptyList()
        reader.startInventory()
        addLog("Bắt đầu inventory")
        inventoryJob?.cancel()
        inventoryJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { tag ->
                mutableLastSeenEpc.value = tag.epc
                mutableInventoryTags.update { current ->
                    if (current.contains(tag.epc)) current else current + tag.epc
                }
                addLog("Inventory: ${tag.epc}")
            }
        }
    }

    fun stopInventory() {
        reader.stopInventory()
        inventoryJob?.cancel()
        addLog("Dừng inventory")
    }

    fun startLocate(targetEpc: String) {
        mutableLocateSignal.value = 0
        reader.startLocate(targetEpc)
        addLog("Bắt đầu locate: $targetEpc")
        locateJob?.cancel()
        locateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.locateSignal.collect { value ->
                mutableLocateSignal.value = value
                addLog("Locate signal: $value")
            }
        }
    }

    fun stopLocate() {
        reader.stopLocate()
        locateJob?.cancel()
        mutableLocateSignal.value = 0
        addLog("Dừng locate")
    }

    fun writeEpc(oldEpc: String, newEpc: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val ok = reader.writeEpc(oldEpc, newEpc)
            mutableWriteResult.value = if (ok) "Ghi EPC thành công" else "Ghi EPC thất bại"
            addLog("Write EPC: $oldEpc -> $newEpc (${if (ok) "OK" else "FAIL"})")
        }
    }

    private fun addLog(message: String) {
        mutableLogs.update { current -> (listOf(message) + current).take(20) }
    }
}
