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
    /** Beeper giả để đếm số tiếng phát ra qua loa điện thoại. */
    private class FakeBeeper : com.example.koistock.device.Beeper {
        var count = 0
        override fun beep() { count += 1 }
        override fun release() = Unit
    }

    @Test
    fun cadence_isMonotonic_closerMeansShorter() {
        val far = BeepCadence.intervalMs(0)
        val mid = BeepCadence.intervalMs(50)
        val near = BeepCadence.intervalMs(100)
        assertTrue(far > mid && mid > near)
        // Theo công thức demo hãng: xa ~300ms, gần nhất bị chặn sàn để không thành tiếng rè.
        assertEquals(300L, far)
        assertEquals(100L, mid)
        assertEquals(45L, near)
    }

    @Test
    fun rssi_mapsToSignal_closerIsHigher() {
        // Dải thực đo trên R6: -75 (xa nhất) -> 0, -29 (sát thẻ) -> 100.
        assertEquals(0, LocateViewModel.rssiToSignal(-75))
        assertEquals(100, LocateViewModel.rssiToSignal(-29))
        // Giữa dải ~ -52 -> quanh 50.
        assertEquals(50, LocateViewModel.rssiToSignal(-52))
        assertTrue(LocateViewModel.rssiToSignal(-40) > LocateViewModel.rssiToSignal(-60))
    }

    @Test
    fun rssi_outsideMeasuredRange_isClamped() {
        assertEquals(0, LocateViewModel.rssiToSignal(-90))
        assertEquals(100, LocateViewModel.rssiToSignal(-20))
    }

    @Test
    fun signal_updatesFromMatchingInventoryTag() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("E2000ABC", -29)
        runCurrent()
        assertEquals(100, vm.signal.value)
        assertEquals(BeepCadence.intervalMs(100), vm.intervalMs.value)
        vm.stop()
    }

    @Test
    fun signal_matchesTargetCaseInsensitive() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.start("e2000abc") // mục tiêu lưu chữ thường
        runCurrent()
        reader.emitTag("E2000ABC", -29) // reader trả chữ hoa
        runCurrent()
        assertEquals(100, vm.signal.value)
        assertEquals(1, vm.rawReads.value)
        vm.stop()
    }

    @Test
    fun signal_ignoresNonTargetTags() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope)
        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("OTHER", -29)
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
        val beeper = FakeBeeper()
        val vm = LocateViewModel(reader, this.backgroundScope, beeper = beeper)

        vm.start("E2000ABC")
        runCurrent()
        reader.emitTag("E2000ABC", -29)
        runCurrent()

        advanceTimeBy(250)
        runCurrent()
        assertTrue(beeper.count >= 2)

        val beforeStop = beeper.count
        vm.stop()
        advanceTimeBy(500)
        runCurrent()

        assertEquals(beforeStop, beeper.count)
    }

    @Test
    fun beep_onlyWhenTargetTagMatches() = runTest {
        val reader = FakeRfidReader()
        val beeper = FakeBeeper()
        val vm = LocateViewModel(reader, this.backgroundScope, beeper = beeper)

        vm.start("E2000ABC")
        runCurrent()

        // Thẻ khác: không được phát tiếng.
        reader.emitTag("OTHER-TAG", -29)
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(0, beeper.count)

        // Đúng thẻ mục tiêu: bắt đầu phát tiếng.
        reader.emitTag("E2000ABC", -29)
        runCurrent()
        advanceTimeBy(150)
        runCurrent()
        assertTrue(beeper.count > 0)
        vm.stop()
    }

    @Test
    fun locate_mutesHardwareBeepWhileRunning() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, this.backgroundScope, beeper = FakeBeeper())

        vm.start("E2000ABC")
        runCurrent()
        // Buzzer R6 phải tắt để không kêu với mọi thẻ.
        assertTrue(!reader.readBeepEnabled)

        vm.stop()
        runCurrent()
        assertTrue(reader.readBeepEnabled)
    }
}
