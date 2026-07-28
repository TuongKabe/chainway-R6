package com.example.koistock.ui.locate

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.remote.LocatableProduct
import com.example.koistock.data.remote.LocateCatalogRepo
import com.example.koistock.device.FakeRfidReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocateViewModelTest {
    private class RecordingCatalogRepo(
        private val cached: List<LocatableProduct>? = null,
        private val refreshed: List<LocatableProduct> = emptyList(),
        private val failure: Throwable? = null,
        private val refreshGate: CompletableDeferred<Unit>? = null,
    ) : LocateCatalogRepo {
        var refreshCalls = 0

        override suspend fun loadCached() = cached

        override suspend fun refresh(): List<LocatableProduct> {
            refreshCalls += 1
            refreshGate?.await()
            failure?.let { throw it }
            return refreshed
        }
    }

    /** Beeper giả để đếm số tiếng phát ra qua loa điện thoại. */
    private class FakeBeeper : com.example.koistock.device.Beeper {
        var count = 0
        override fun beep() { count += 1 }
        override fun release() = Unit
    }

    @Test
    fun loadCatalog_loadsBatchCatalogOnce() = runTest {
        val catalog = RecordingCatalogRepo(
            refreshed = listOf(
                LocatableProduct(
                    product("SKU-A", "Koi A"),
                    listOf(TagMapping("EPC-A1", "SKU-A"), TagMapping("EPC-A2", "SKU-A")),
                ),
            ),
        )
        val vm = LocateViewModel(FakeRfidReader(), backgroundScope, catalogRepo = catalog)

        vm.loadCatalog()
        runCurrent()

        val ready = vm.catalogState.value as LocateCatalogState.Ready
        assertEquals(1, catalog.refreshCalls)
        assertEquals(listOf("SKU-A"), ready.items.map { it.product.sku })
        assertEquals(listOf("EPC-A1", "EPC-A2"), ready.items.single().activeTags.map { it.epc })
    }

    @Test
    fun loadCatalog_repositoryFailureWithoutCache_returnsError() = runTest {
        val catalog = RecordingCatalogRepo(failure = IllegalStateException("catalog offline"))
        val vm = LocateViewModel(
            FakeRfidReader(),
            backgroundScope,
            catalogRepo = catalog,
        )

        vm.loadCatalog()
        runCurrent()

        val error = vm.catalogState.value as LocateCatalogState.Error
        assertEquals(1, catalog.refreshCalls)
        assertTrue(error.message.contains("catalog offline"))
    }

    @Test
    fun loadCatalog_showsCacheWhileRefreshIsRunning() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cached = listOf(LocatableProduct(product("SKU-CACHED", "Cached"), listOf(TagMapping("E1", "SKU-CACHED"))))
        val catalog = RecordingCatalogRepo(cached = cached, refreshed = emptyList(), refreshGate = gate)
        val vm = LocateViewModel(FakeRfidReader(), backgroundScope, catalogRepo = catalog)

        vm.loadCatalog()
        runCurrent()

        val ready = vm.catalogState.value as LocateCatalogState.Ready
        assertEquals(listOf("SKU-CACHED"), ready.items.map { it.product.sku })
        assertTrue(ready.refreshing)
        gate.complete(Unit)
    }

    @Test
    fun filterLocatableProducts_trimsQueryAndIgnoresCase() {
        val items = listOf(
            LocatableProduct(product("SKU-ABC", "Koi Showa"), listOf(TagMapping("E1", "SKU-ABC"))),
            LocatableProduct(product("SKU-XYZ", "Koi Kohaku"), listOf(TagMapping("E2", "SKU-XYZ"))),
        )

        assertEquals(listOf("SKU-ABC"), filterLocatableProducts(items, "  sku-abc ").map { it.product.sku })
        assertEquals(listOf("SKU-XYZ"), filterLocatableProducts(items, " KOHAKU ").map { it.product.sku })
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
    fun repeatedMatchingReads_doNotRestartBeepBeforeCurrentCadenceElapses() = runTest {
        val reader = FakeRfidReader()
        val beeper = FakeBeeper()
        val vm = LocateViewModel(reader, this.backgroundScope, beeper = beeper)

        vm.start("E2000ABC")
        runCurrent()
        repeat(200) {
            reader.emitTag("E2000ABC", -29)
            runCurrent()
        }

        // Lần đọc đầu tiên bắt đầu vòng beep. Các read RFID tiếp theo chỉ cập nhật
        // RSSI, không được hủy/tạo lại vòng beep và phát dồn 200 âm cùng lúc.
        assertEquals(1, beeper.count)

        advanceTimeBy(BeepCadence.intervalMs(100))
        runCurrent()
        assertEquals(2, beeper.count)
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

    private fun product(sku: String, name: String) = Product(
        sku = sku,
        name = name,
        unit = "con",
        trackingMode = TrackingMode.SERIALIZED,
    )
}
