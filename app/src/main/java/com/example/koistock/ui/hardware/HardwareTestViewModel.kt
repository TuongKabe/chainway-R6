package com.example.koistock.ui.hardware

import com.example.koistock.device.ConnectionState
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TriggerHistoryRow(
    val pressedAt: Long,
    val releasedAt: Long,
    val durationMs: Long,
    val kindLabel: String,
)

class HardwareTestViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val holdThresholdMs: Long = 1_000L,
    private val triggerReleaseGuardMs: Long = 280L,
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

    private val mutableTriggerHeld = MutableStateFlow(false)
    val triggerHeld: StateFlow<Boolean> = mutableTriggerHeld.asStateFlow()

    private val mutableTriggerCount = MutableStateFlow(0)
    val triggerCount: StateFlow<Int> = mutableTriggerCount.asStateFlow()

    private val mutableLastTriggerAt = MutableStateFlow<Long?>(null)
    val lastTriggerAt: StateFlow<Long?> = mutableLastTriggerAt.asStateFlow()

    private val mutableLastTriggerHoldMs = MutableStateFlow<Long?>(null)
    val lastTriggerHoldMs: StateFlow<Long?> = mutableLastTriggerHoldMs.asStateFlow()

    private val mutableTriggerTestOnly = MutableStateFlow(false)
    val triggerTestOnly: StateFlow<Boolean> = mutableTriggerTestOnly.asStateFlow()

    private val mutableTriggerHistory = MutableStateFlow<List<TriggerHistoryRow>>(emptyList())
    val triggerHistory: StateFlow<List<TriggerHistoryRow>> = mutableTriggerHistory.asStateFlow()

    private val mutableRawSdkLogs = MutableStateFlow<List<String>>(emptyList())
    val rawSdkLogs: StateFlow<List<String>> = mutableRawSdkLogs.asStateFlow()

    private val mutableLastSeenEpc = MutableStateFlow<String?>(null)
    val lastSeenEpc: StateFlow<String?> = mutableLastSeenEpc.asStateFlow()

    private val mutableLogs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = mutableLogs.asStateFlow()

    private var inventoryJob: Job? = null
    private var locateJob: Job? = null
    private var triggerJob: Job? = null
    private var rawKeyJob: Job? = null
    private var triggerDecisionJob: Job? = null
    private var triggerHoldStateJob: Job? = null
    private var triggerReleaseArmJob: Job? = null
    private var inventoryActive = false
    private var inventoryStartedByTrigger = false
    private var triggerReleaseArmed = true
    private var triggerPressedAt: Long? = null

    init {
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                mutableTriggerPressed.value = pressed
                if (pressed) {
                    triggerPressedAt = now()
                    mutableTriggerCount.value += 1
                    mutableLastTriggerAt.value = triggerPressedAt
                    addLog("Trigger: nhấn")
                    onTriggerPressed()
                } else {
                    val pressedAt = triggerPressedAt
                    val releasedAt = now()
                    val durationMs = pressedAt?.let { (releasedAt - it).coerceAtLeast(0L) }
                    mutableLastTriggerHoldMs.value = durationMs
                    if (pressedAt != null && durationMs != null) {
                        val kind = if (durationMs < holdThresholdMs) "Bấm" else "Giữ"
                        mutableTriggerHistory.update { current ->
                            (listOf(
                                TriggerHistoryRow(
                                    pressedAt = pressedAt,
                                    releasedAt = releasedAt,
                                    durationMs = durationMs,
                                    kindLabel = kind,
                                )
                            ) + current).take(12)
                        }
                    }
                    triggerPressedAt = null
                    addLog("Trigger: nhả")
                    onTriggerReleased()
                }
            }
        }
        rawKeyJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.rawKeyEvents.collect { message ->
                mutableRawSdkLogs.update { current ->
                    (listOf("${formatNow(now())}  $message") + current).take(20)
                }
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
        startInventoryInternal(startedByTrigger = false)
    }

    fun stopInventory() {
        stopInventoryInternal()
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

    fun setTriggerTestOnly(enabled: Boolean) {
        mutableTriggerTestOnly.value = enabled
        triggerDecisionJob?.cancel()
        triggerHoldStateJob?.cancel()
        triggerReleaseArmJob?.cancel()
        mutableTriggerHeld.value = false
        triggerReleaseArmed = true
        if (enabled && inventoryActive && inventoryStartedByTrigger) {
            stopInventoryInternal()
        }
        addLog(if (enabled) "Bật Trigger test only" else "Tắt Trigger test only")
    }

    fun clear() {
        triggerDecisionJob?.cancel()
        triggerHoldStateJob?.cancel()
        triggerReleaseArmJob?.cancel()
        inventoryJob?.cancel()
        locateJob?.cancel()
        triggerJob?.cancel()
        rawKeyJob?.cancel()
    }

    private fun onTriggerPressed() {
        triggerDecisionJob?.cancel()
        triggerHoldStateJob?.cancel()
        triggerReleaseArmJob?.cancel()
        mutableTriggerHeld.value = false

        triggerHoldStateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(holdThresholdMs)
            if (mutableTriggerPressed.value) {
                mutableTriggerHeld.value = true
                addLog("Trigger: giữ")
            }
        }

        if (mutableTriggerTestOnly.value) {
            addLog("Trigger test only: ghi nhận nhấn")
            return
        }

        triggerReleaseArmed = false
        triggerReleaseArmJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(triggerReleaseGuardMs)
            triggerReleaseArmed = true
        }
        triggerDecisionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(holdThresholdMs)
            if (!inventoryActive && mutableTriggerPressed.value) {
                inventoryStartedByTrigger = true
                startInventoryInternal(startedByTrigger = true)
            }
        }
    }

    private fun onTriggerReleased() {
        triggerDecisionJob?.cancel()
        triggerHoldStateJob?.cancel()
        val wasHeld = mutableTriggerHeld.value
        mutableTriggerHeld.value = false

        mutableLastTriggerHoldMs.value?.let { holdMs ->
            addLog("Giữ trigger: ${holdMs}ms")
        }
        if (mutableTriggerTestOnly.value) {
            addLog("Trigger test only: ghi nhận nhả")
            return
        }

        when {
            inventoryActive && inventoryStartedByTrigger -> {
                if (triggerReleaseArmed) {
                    stopInventoryInternal()
                } else {
                    addLog("Bỏ qua release sớm của trigger")
                }
            }

            !wasHeld -> {
                scanSingle()
            }
        }
    }

    private fun startInventoryInternal(startedByTrigger: Boolean) {
        mutableInventoryTags.value = emptyList()
        reader.startInventory()
        inventoryActive = true
        inventoryStartedByTrigger = startedByTrigger
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

    private fun stopInventoryInternal() {
        reader.stopInventory()
        inventoryJob?.cancel()
        inventoryJob = null
        inventoryActive = false
        inventoryStartedByTrigger = false
        addLog("Dừng inventory")
    }

    private fun addLog(message: String) {
        mutableLogs.update { current -> (listOf(message) + current).take(20) }
    }

    private fun formatNow(value: Long): String = java.text.SimpleDateFormat(
        "HH:mm:ss.SSS",
        java.util.Locale.US,
    ).format(java.util.Date(value))
}
