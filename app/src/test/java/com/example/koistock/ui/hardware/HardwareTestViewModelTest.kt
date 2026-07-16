package com.example.koistock.ui.hardware

import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun scanSingle_readsSingleTag() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val vm = HardwareTestViewModel(reader, this)

        vm.scanSingle()

        assertEquals("E2000ABC", vm.singleScan.value)
        assertEquals("E2000ABC", vm.lastSeenEpc.value)
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
    }

    @Test
    fun writeEpc_updatesResult() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        vm.writeEpc("OLD", "NEW")

        assertEquals("OLD" to "NEW", reader.lastWrittenEpc)
        assertEquals("Ghi EPC thành công", vm.writeResult.value)
    }

    @Test
    fun trigger_updatesDebugState() = runTest {
        val reader = FakeRfidReader()
        val vm = HardwareTestViewModel(reader, this)

        reader.emitTrigger(true)
        advanceUntilIdle()

        assertTrue(vm.triggerPressed.value)
        assertTrue(vm.logs.value.first().contains("Trigger"))
    }
}
