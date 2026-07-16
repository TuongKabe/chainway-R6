package com.example.koistock.ui.locate

import com.example.koistock.device.FakeRfidReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocateViewModelTest {
    @Test
    fun cadence_isMonotonic_closerMeansShorter() {
        val far = BeepCadence.intervalMs(0)
        val mid = BeepCadence.intervalMs(50)
        val near = BeepCadence.intervalMs(100)
        assertTrue(far > mid && mid > near)
        assertTrue(near <= 100)
        assertTrue(far >= 1000)
    }

    @Test
    fun signal_updatesFromReader() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this)
        vm.start("KOI-SKU1-1")
        runCurrent()
        reader.emitLocate(80)
        runCurrent()
        assertEquals(80, vm.signal.value)
        assertEquals(BeepCadence.intervalMs(80), vm.intervalMs.value)
        vm.stop()
    }

    @Test
    fun startForSku_usesMaskTarget() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this)
        vm.startForSku("SKU1")
        runCurrent()
        assertEquals("KOI-SKU1-", reader.locateTarget)
        vm.stop()
    }

    @Test
    fun beep_repeatsFasterWhenSignalIsNear_andStopsAfterStop() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this)

        vm.start("KOI-SKU1-1")
        runCurrent()
        reader.emitLocate(100)
        runCurrent()

        advanceTimeBy(250)
        runCurrent()
        assertTrue(reader.beepCount >= 2)

        val beforeStop = reader.beepCount
        vm.stop()
        advanceTimeBy(500)
        runCurrent()

        assertEquals(beforeStop, reader.beepCount)
    }
}
