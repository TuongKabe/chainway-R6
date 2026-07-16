package com.example.koistock.ui.hardware

import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HardwareTestViewModelTest {
    @Test
    fun refreshBattery_readsBatteryPercent() = runTest {
        val reader = FakeRfidReader().apply { setBattery(77) }
        val vm = HardwareTestViewModel(reader, this)

        vm.refreshBattery()

        assertEquals(77, vm.battery.value)
        assertTrue(vm.logs.value.first().contains("Đọc pin"))
        vm.clear()
    }

    @Test
    fun scanSingle_readsSingleTag() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val vm = HardwareTestViewModel(reader, this)

        vm.scanSingle()

        assertEquals("E2000ABC", vm.singleScan.value)
        assertEquals("E2000ABC", vm.lastSeenEpc.value)
        vm.clear()
    }

    @Test
    fun inventory_collectsUniqueEpcs() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        vm.startInventory()
        advanceUntilIdle()
        reader.emitTag("E1")
        reader.emitTag("E1")
        reader.emitTag("E2")
        advanceUntilIdle()

        assertEquals(listOf("E1", "E2"), vm.inventoryTags.value)
        assertEquals("E2", vm.lastSeenEpc.value)
        vm.stopInventory()
        vm.clear()
    }

    @Test
    fun locate_updatesSignal() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        vm.startLocate("E1")
        advanceUntilIdle()
        reader.emitLocate(64)
        advanceUntilIdle()

        assertEquals(64, vm.locateSignal.value)
        vm.stopLocate()
        vm.clear()
    }

    @Test
    fun writeEpc_updatesResult() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        vm.writeEpc("OLD", "NEW")

        assertEquals("OLD" to "NEW", reader.lastWrittenEpc)
        assertEquals("Ghi EPC thành công", vm.writeResult.value)
        vm.clear()
    }

    @Test
    fun trigger_updatesDebugState_andCounter() = runTest {
        var fakeNow = 1234L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        reader.emitTrigger(true)
        advanceTimeBy(100)
        reader.emitTrigger(false)
        advanceUntilIdle()

        fakeNow = 5678L
        reader.emitTrigger(true)
        advanceTimeBy(100)
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertFalse(vm.triggerPressed.value)
        assertEquals(2, vm.triggerCount.value)
        assertEquals(5678L, vm.lastTriggerAt.value)
        assertTrue(vm.logs.value.any { it.contains("Trigger") })
        vm.clear()
    }

    @Test
    fun trigger_shortPress_scansSingleTag() = runTest {
        val reader = FakeRfidReader().apply {
            scannedSingle = ScannedTag("E2000TRIGGER", -32)
        }
        val vm = HardwareTestViewModel(reader, this)

        reader.emitTrigger(true)
        advanceTimeBy(100)
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertEquals("E2000TRIGGER", vm.singleScan.value)
        assertEquals(1, reader.singleScanCount)
        assertEquals(0, reader.inventoryStartCount)
        vm.clear()
    }

    @Test
    fun trigger_hold_startsAndStopsInventory() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        reader.emitTrigger(true)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        assertTrue(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStartCount)
        assertEquals(0, reader.singleScanCount)

        reader.emitTrigger(false)
        advanceUntilIdle()

        assertEquals(1, reader.inventoryStopCount)
        assertFalse(reader.inventoryRunning)
        vm.clear()
    }

    @Test
    fun trigger_hold_updatesHeldState_beforeRelease() = runTest {
        var fakeNow = 5_000L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        reader.emitTrigger(true)
        advanceTimeBy(1_100)
        runCurrent()

        assertTrue(vm.triggerPressed.value)
        assertTrue(vm.triggerHeld.value)

        fakeNow = 6_100L
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertFalse(vm.triggerPressed.value)
        assertFalse(vm.triggerHeld.value)
        assertEquals(1_100L, vm.lastTriggerHoldMs.value)
        vm.clear()
    }

    @Test
    fun trigger_hold_afterThreshold_startsInventory_andStopsOnRelease() = runTest {
        var fakeNow = 0L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        reader.emitTrigger(true)
        advanceTimeBy(1_100)
        fakeNow = 1_100L
        runCurrent()

        assertTrue(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStartCount)

        fakeNow = 1_400L
        reader.emitTrigger(false)
        runCurrent()

        assertFalse(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStopCount)
        vm.clear()
    }

    @Test
    fun triggerTestOnly_shortPress_updatesDebugOnly_withoutScanning() = runTest {
        var fakeNow = 1_000L
        val reader = FakeRfidReader().apply {
            scannedSingle = ScannedTag("E2000TRIGGER", -32)
        }
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        vm.setTriggerTestOnly(true)
        reader.emitTrigger(true)
        advanceTimeBy(100)
        fakeNow = 1_100L
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertFalse(vm.triggerPressed.value)
        assertEquals(1, vm.triggerCount.value)
        assertEquals(100L, vm.lastTriggerHoldMs.value)
        assertEquals(0, reader.singleScanCount)
        assertEquals(0, reader.inventoryStartCount)
        assertTrue(vm.logs.value.any { it.contains("Trigger test only") })
        vm.clear()
    }

    @Test
    fun triggerTestOnly_hold_doesNotStartInventory() = runTest {
        var fakeNow = 2_000L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        vm.setTriggerTestOnly(true)
        reader.emitTrigger(true)
        advanceTimeBy(1_100)
        advanceUntilIdle()

        assertFalse(reader.inventoryRunning)
        assertEquals(0, reader.inventoryStartCount)

        fakeNow = 3_100L
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertEquals(1_100L, vm.lastTriggerHoldMs.value)
        assertEquals(0, reader.inventoryStopCount)
        assertFalse(vm.triggerHeld.value)
        vm.clear()
    }

    @Test
    fun triggerTestOnly_hold_updatesHeldState_withoutScanning() = runTest {
        var fakeNow = 8_000L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        vm.setTriggerTestOnly(true)
        reader.emitTrigger(true)
        advanceTimeBy(1_100)
        runCurrent()

        assertTrue(vm.triggerPressed.value)
        assertTrue(vm.triggerHeld.value)
        assertEquals(0, reader.inventoryStartCount)

        fakeNow = 9_100L
        reader.emitTrigger(false)
        advanceUntilIdle()

        assertFalse(vm.triggerHeld.value)
        assertEquals(1_100L, vm.lastTriggerHoldMs.value)
        vm.clear()
    }

    @Test
    fun triggerHistory_classifiesShortPressAndHoldBy1000msRule() = runTest {
        var fakeNow = 10_000L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        vm.setTriggerTestOnly(true)

        reader.emitTrigger(true)
        advanceTimeBy(300)
        fakeNow = 10_300L
        reader.emitTrigger(false)
        advanceUntilIdle()

        reader.emitTrigger(true)
        advanceTimeBy(1_200)
        fakeNow = 11_500L
        reader.emitTrigger(false)
        advanceUntilIdle()

        val history = vm.triggerHistory.value
        assertEquals(2, history.size)
        assertEquals(1_200L, history[0].durationMs)
        assertEquals("Giữ", history[0].kindLabel)
        assertEquals(300L, history[1].durationMs)
        assertEquals("Bấm", history[1].kindLabel)
        vm.clear()
    }

    @Test
    fun rawSdkKeyEvents_areCapturedInOrder() = runTest {
        var fakeNow = 20_000L
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this, now = { fakeNow })

        reader.emitRawKeyEvent("SDK DOWN keyCode=1")
        advanceUntilIdle()
        fakeNow = 20_300L
        reader.emitRawKeyEvent("SDK UP keyCode=4")
        advanceUntilIdle()

        val raw = vm.rawSdkLogs.value
        assertEquals(2, raw.size)
        assertTrue(raw[0].contains("SDK UP keyCode=4"))
        assertTrue(raw[1].contains("SDK DOWN keyCode=1"))
        vm.clear()
    }
}
