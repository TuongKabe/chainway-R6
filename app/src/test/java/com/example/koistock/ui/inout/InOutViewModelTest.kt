package com.example.koistock.ui.inout

import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TxType
import com.example.koistock.data.remote.CommitStockResult
import com.example.koistock.data.remote.StockCommandRepo
import com.example.koistock.data.remote.StockMovement
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.fakes.FakeTagRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InOutViewModelTest {
    @Test
    fun out_confirm_appendsNegativeDeltas() = runTest {
        val reader = FakeRfidReader()
        val repo = FakeStockCommandRepo()
        val vm = InOutViewModel(
            reader = reader,
            tagRepo = FakeTagRepo(
                mutableMapOf(
                    "KOI-S1-1" to TagMapping("KOI-S1-1", "S1"),
                    "KOI-S1-2" to TagMapping("KOI-S1-2", "S1"),
                ),
            ),
            stockCommandRepo = repo,
            deviceId = "dev-1",
            newCommandId = { "7" },
            scope = this,
        )
        vm.setMode(InOutMode.OUT)
        vm.startScan()
        advanceUntilIdle()
        reader.emitTag("KOI-S1-1")
        reader.emitTag("KOI-S1-2")
        advanceUntilIdle()
        vm.stopScan()
        vm.confirm()
        advanceUntilIdle()

        assertEquals(2, repo.lastMovements.size)
        assertTrue(repo.lastMovements.all { it.delta == -1L })
        assertEquals(TxType.OUT, repo.lastType)
    }

    @Test
    fun in_confirm_appendsPositiveDeltas() = runTest {
        val reader = FakeRfidReader()
        val repo = FakeStockCommandRepo()
        val vm = InOutViewModel(
            reader = reader,
            tagRepo = FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1", "S1"))),
            stockCommandRepo = repo,
            deviceId = "dev-1",
            newCommandId = { "7" },
            scope = this,
        )
        vm.setMode(InOutMode.IN)
        vm.startScan()
        advanceUntilIdle()
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()
        vm.stopScan()
        vm.confirm()
        advanceUntilIdle()

        assertEquals(1L, repo.lastMovements.first().delta)
        assertEquals(TxType.IN, repo.lastType)
    }

    @Test
    fun pending_groupsBySku_dedupeEpc() = runTest {
        val reader = FakeRfidReader()
        val vm = InOutViewModel(
            reader = reader,
            tagRepo = FakeTagRepo(
                mutableMapOf(
                    "KOI-S1-1" to TagMapping("KOI-S1-1", "S1"),
                    "KOI-S1-2" to TagMapping("KOI-S1-2", "S1"),
                ),
            ),
            stockCommandRepo = FakeStockCommandRepo(),
            deviceId = "dev-1",
            newCommandId = { "7" },
            scope = this,
        )
        vm.startScan()
        advanceUntilIdle()
        reader.emitTag("KOI-S1-1")
        reader.emitTag("KOI-S1-1")
        reader.emitTag("KOI-S1-2")
        advanceUntilIdle()

        assertEquals(2, vm.pending.value["S1"])
        vm.stopScan()
    }

    @Test
    fun trigger_toggle_startsAndStopsInventory() = runTest {
        val reader = FakeRfidReader()
        val vm = InOutViewModel(
            reader = reader,
            tagRepo = FakeTagRepo(),
            stockCommandRepo = FakeStockCommandRepo(),
            deviceId = "dev-1",
            newCommandId = { "7" },
            scope = this,
        )
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

private class FakeStockCommandRepo : StockCommandRepo {
    var lastType: TxType? = null
    var lastMovements: List<StockMovement> = emptyList()

    override suspend fun commit(
        commandId: String,
        type: TxType,
        deviceId: String,
        movements: List<StockMovement>,
    ): CommitStockResult {
        lastType = type
        lastMovements = movements
        return CommitStockResult.Success(commandId)
    }
}
