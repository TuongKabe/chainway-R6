package com.example.koistock.ui.locate

import com.example.koistock.device.RfidReader
import com.example.koistock.domain.EpcCodec
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

class LocateViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
) {
    private val mutableSignal = MutableStateFlow(0)
    val signal: StateFlow<Int> = mutableSignal.asStateFlow()
    private val mutableIntervalMs = MutableStateFlow(BeepCadence.intervalMs(0))
    val intervalMs: StateFlow<Long> = mutableIntervalMs.asStateFlow()

    private var locateJob: Job? = null
    private var beepJob: Job? = null

    fun start(targetEpc: String) {
        reader.startLocate(targetEpc)
        locateJob?.cancel()
        beepJob?.cancel()
        locateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.locateSignal.collect {
                mutableSignal.value = it
                mutableIntervalMs.value = BeepCadence.intervalMs(it)
                restartBeepLoop(it)
            }
        }
    }

    fun startForSku(sku: String) {
        start(EpcCodec.maskForSku(sku))
    }

    fun stop() {
        reader.stopLocate()
        locateJob?.cancel()
        beepJob?.cancel()
        mutableSignal.value = 0
        mutableIntervalMs.value = BeepCadence.intervalMs(0)
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
}
