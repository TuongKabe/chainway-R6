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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        advanceUntilIdle()

        reader.emitTag("KOI-S1-1")
        reader.emitTag("KOI-S1-2")
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()

        vm.stopScan()
        assertEquals(2, vm.countedBySku.value["S1"])
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
        advanceUntilIdle()
        reader.emitTag("KOI-S1-1")
        advanceUntilIdle()
        vm.stopScan()

        vm.reconcile(listOf(ExpectedItem("S1", "Áo", 1, "A-03")))
        assertEquals(CountStatus.MATCH, vm.rows.value.first().status)
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
    }
}
