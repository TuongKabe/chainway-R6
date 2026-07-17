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
    fun rssi_mapsToSignal_closerIsHigher() {
        assertEquals(0, LocateViewModel.rssiToSignal(-80))
        assertEquals(100, LocateViewModel.rssiToSignal(-30))
        assertTrue(LocateViewModel.rssiToSignal(-40) > LocateViewModel.rssiToSignal(-60))
    }

    @Test
    fun signal_updatesFromMatchingInventoryTag() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("E2000ABC", -30)
        runCurrent()
        assertEquals(100, vm.signal.value)
        assertEquals(BeepCadence.intervalMs(100), vm.intervalMs.value)
        vm.stop()
    }

    @Test
    fun signal_ignoresNonTargetTags() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("OTHER", -30)
        runCurrent()
        assertEquals(0, vm.signal.value)
        vm.stop()
    }

    @Test
    fun trigger_togglesLocatingForArmedTarget() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.setTarget("E2000ABC")
        runCurrent()

        reader.emitTrigger(true)
        runCurrent()
        assertTrue(vm.isLocating.value)
        assertEquals(1, reader.inventoryStartCount)

        reader.emitTrigger(true)
        runCurrent()
        assertTrue(!vm.isLocating.value)
        assertEquals(1, reader.inventoryStopCount)
        vm.clear()
    }

    @Test
    fun beep_repeatsFasterWhenSignalIsNear_andStopsAfterStop() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)

        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("E2000ABC", -30)
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
