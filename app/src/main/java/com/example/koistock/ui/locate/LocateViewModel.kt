package com.example.koistock.ui.locate

import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BeepCadence {
    fun intervalMs(signal0to100: Int): Long {
        val signal = signal0to100.coerceIn(0, 100)
        return 1200L - ((1200L - 60L) * signal / 100L)
    }
}

/**
 * Định vị bằng cơ chế inventory (giống "Quét theo khu" đang chạy tốt trên R6):
 * bật inventory, lọc EPC mục tiêu, đổi RSSI thành cường độ tín hiệu 0..100.
 * RSSI càng cao (gần 0 dBm) nghĩa là tag càng gần.
 */
class LocateViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
) {
    private val mutableSignal = MutableStateFlow(0)
    val signal: StateFlow<Int> = mutableSignal.asStateFlow()
    private val mutableIntervalMs = MutableStateFlow(BeepCadence.intervalMs(0))
    val intervalMs: StateFlow<Long> = mutableIntervalMs.asStateFlow()
    private val mutableIsLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = mutableIsLocating.asStateFlow()
    private val mutableTargetEpc = MutableStateFlow<String?>(null)
    val targetEpc: StateFlow<String?> = mutableTargetEpc.asStateFlow()

    private var armedEpc: String? = null
    private var locateJob: Job? = null
    private var beepJob: Job? = null
    private var triggerJob: Job? = null

    init {
        // Bấm cò trên R6: đang dò -> dừng, chưa dò -> bắt đầu với tag đã chọn.
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (pressed) {
                    if (mutableIsLocating.value) {
                        stop()
                    } else {
                        armedEpc?.let { start(it) }
                    }
                }
            }
        }
    }

    /** Đặt tag mục tiêu để cò có thể bắt đầu dò. */
    fun setTarget(epc: String?) {
        armedEpc = epc
    }

    fun start(targetEpc: String) {
        armedEpc = targetEpc
        mutableTargetEpc.value = targetEpc
        mutableIsLocating.value = true
        mutableSignal.value = 0
        mutableIntervalMs.value = BeepCadence.intervalMs(0)
        locateJob?.cancel()
        beepJob?.cancel()
        reader.startInventory()
        locateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { scanned ->
                if (scanned.epc != targetEpc) return@collect
                val value = rssiToSignal(scanned.rssi)
                mutableSignal.value = value
                mutableIntervalMs.value = BeepCadence.intervalMs(value)
                restartBeepLoop(value)
            }
        }
    }

    fun stop() {
        reader.stopInventory()
        locateJob?.cancel()
        beepJob?.cancel()
        mutableIsLocating.value = false
        mutableSignal.value = 0
        mutableIntervalMs.value = BeepCadence.intervalMs(0)
    }

    fun clear() {
        triggerJob?.cancel()
        stop()
    }

    private fun restartBeepLoop(signal: Int) {
        beepJob?.cancel()
        if (signal <= 0) return
        beepJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (true) {
                reader.beep()
                delay(mutableIntervalMs.value)
            }
        }
    }

    companion object {
        private const val FAR_DBM = -80
        private const val NEAR_DBM = -30

        /** Đổi RSSI (dBm) sang tín hiệu 0..100. -80dBm→0, -30dBm→100. */
        fun rssiToSignal(rssi: Int): Int {
            val clamped = rssi.coerceIn(FAR_DBM, NEAR_DBM)
            return ((clamped - FAR_DBM) * 100 / (NEAR_DBM - FAR_DBM)).coerceIn(0, 100)
        }
    }
}
