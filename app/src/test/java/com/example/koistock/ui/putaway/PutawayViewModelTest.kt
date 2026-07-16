package com.example.koistock.ui.putaway

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.model.TxType
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.fakes.FakeProductRepo
import com.example.koistock.fakes.FakeTagRepo
import com.example.koistock.fakes.FakeTransactionRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PutawayViewModelTest {
    private fun product(sku: String, mode: TrackingMode) = Product(sku, "n", "c", mode, 0, "A-01")

    @Test
    fun serialized_applyAssign_updatesTagLocation() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1", "S1", locationCode = "A-01")))
        val products = FakeProductRepo(mutableMapOf("S1" to product("S1", TrackingMode.SERIALIZED)))
        val tx = FakeTransactionRepo()
        val vm = PutawayViewModel(reader, tags, products, tx, "dev-1", { 5 }, this)
        vm.setLocationByTag("A-03")
        vm.startCollect()
        advanceUntilIdle()
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()
        vm.stopCollect()
        val count = vm.applyAssign()
        assertEquals(1, count)
        assertEquals("A-03", tags.items["KOI-S1-1"]!!.locationCode)
        assertTrue(tx.appended.isEmpty())
    }

    @Test
    fun bulk_applyAssign_appendsMoveTransaction() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-B1-1" to TagMapping("KOI-B1-1", "B1", locationCode = "A-01")))
        val products = FakeProductRepo(mutableMapOf("B1" to product("B1", TrackingMode.BULK)))
        val tx = FakeTransactionRepo()
        val vm = PutawayViewModel(reader, tags, products, tx, "dev-1", { 5 }, this)
        vm.setLocationByTag("A-03")
        vm.startCollect()
        advanceUntilIdle()
        reader.emitTag("KOI-B1-1")
        advanceUntilIdle()
        vm.stopCollect()
        vm.applyAssign()
        assertEquals(1, tx.appended.size)
        assertEquals(TxType.MOVE, tx.appended.first().type)
        assertEquals("A-03", tx.appended.first().locationCode)
    }

    @Test
    fun collect_dedupesByEpc() = runTest {
        val reader = FakeRfidReader()
        val vm = PutawayViewModel(reader, FakeTagRepo(), FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, this)
        vm.startCollect()
        advanceUntilIdle()
        reader.emitTag("E1")
        reader.emitTag("E1")
        reader.emitTag("E2")
        advanceUntilIdle()
        assertEquals(setOf("E1", "E2"), vm.scanned.value)
        vm.stopCollect()
    }

    @Test
    fun trigger_toggle_startsAndStopsInventory() = runTest {
        val reader = FakeRfidReader()
        val vm = PutawayViewModel(reader, FakeTagRepo(), FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, this)
        runCurrent()

        reader.emitTrigger(true)
        runCurrent()
        advanceUntilIdle()
        assertTrue(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStartCount)

        reader.emitTrigger(true)
        runCurrent()
        advanceUntilIdle()
        assertFalse(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStopCount)
        vm.clear()
    }
}
