package com.example.koistock.ui.count

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.domain.CountStatus
import com.example.koistock.domain.ExpectedItem
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
class CountViewModelTest {
    @Test
    fun scan_groupsBySku() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(
            mutableMapOf(
                "KOI-S1-1" to TagMapping("KOI-S1-1", "S1"),
                "KOI-S1-2" to TagMapping("KOI-S1-2", "S1"),
            ),
        )
        val products = FakeProductRepo(
            mutableMapOf("S1" to Product("S1", "Áo", "c", TrackingMode.SERIALIZED, 0, "A-03")),
        )
        val vm = CountViewModel(reader, tags, products, FakeTransactionRepo(), "d", { 0 }, this)
        vm.setZone("A-03")
        vm.startScan()
        runCurrent()

        reader.emitTag("KOI-S1-1")
        reader.emitTag("KOI-S1-2")
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()

        vm.stopScan()
        assertEquals(2, vm.countedBySku.value["S1"])
        vm.clear()
    }

    @Test
    fun reconcile_producesRows() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1", "S1")))
        val products = FakeProductRepo(
            mutableMapOf("S1" to Product("S1", "Áo", "c", TrackingMode.SERIALIZED, 0, "A-03")),
        )
        val vm = CountViewModel(reader, tags, products, FakeTransactionRepo(), "d", { 0 }, this)
        vm.setZone("A-03")
        vm.startScan()
        runCurrent()
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()
        vm.stopScan()

        vm.reconcile(listOf(ExpectedItem("S1", "Áo", 1, "A-03")))
        assertEquals(CountStatus.MATCH, vm.rows.value.first().status)
        vm.clear()
    }

    @Test
    fun csv_containsHeader() = runTest {
        val vm = CountViewModel(
            FakeRfidReader(),
            FakeTagRepo(),
            FakeProductRepo(),
            FakeTransactionRepo(),
            "d",
            { 0 },
            this,
        )
        assertTrue(vm.csv().contains("khu,kệ,sku"))
        vm.clear()
    }

    @Test
    fun trigger_single_doesOneBurstThenStops() = runTest {
        // Mặc định COUNT khi không truyền profile = SINGLE (bóp 1 lần = một đợt quét ngắn tự dừng).
        val reader = FakeRfidReader()
        val vm = CountViewModel(
            reader,
            FakeTagRepo(),
            FakeProductRepo(),
            FakeTransactionRepo(),
            "d",
            { 0 },
            this,
        )
        runCurrent()

        // Ngay sau khi bóp: đợt quét bắt đầu.
        reader.emitTrigger(true)
        runCurrent()
        assertTrue(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStartCount)

        // Hết đợt (~1.5s) tự dừng.
        advanceUntilIdle()
        assertFalse(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStopCount)
        vm.clear()
    }

    @Test
    fun trigger_continuous_pressStartsPressStops() = runTest {
        val reader = FakeRfidReader()
        val vm = CountViewModel(
            reader,
            FakeTagRepo(),
            FakeProductRepo(),
            FakeTransactionRepo(),
            "d",
            { 0 },
            this,
            profile = com.example.koistock.device.ScanProfile(
                triggerMode = com.example.koistock.device.TriggerMode.CONTINUOUS,
            ),
        )
        runCurrent()

        // Bóp lần 1 -> bắt đầu quét (bỏ qua sự kiện thả).
        reader.emitTrigger(true)
        runCurrent()
        reader.emitTrigger(false)
        runCurrent()
        advanceUntilIdle()
        assertTrue(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStartCount)

        // Bóp lần 2 -> kết thúc.
        reader.emitTrigger(true)
        runCurrent()
        advanceUntilIdle()
        assertFalse(reader.inventoryRunning)
        assertEquals(1, reader.inventoryStopCount)
        vm.clear()
    }
}
