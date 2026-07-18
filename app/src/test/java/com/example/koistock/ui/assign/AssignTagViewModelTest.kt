package com.example.koistock.ui.assign

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.FakeProductRepo
import com.example.koistock.fakes.FakeAssignSessionRepo
import com.example.koistock.fakes.FakeGsheetWriteRepo
import com.example.koistock.fakes.FakeTagRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignTagViewModelTest {
    @Test
    fun scanBlank_capturesEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val vm = viewModel(reader, FakeTagRepo(), this.backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        assertEquals("E2000ABC", vm.scannedEpc.value)
    }

    @Test
    fun assign_raw_writesTagMappingWithScannedEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = viewModel(reader, tags, this.backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = false, pushBarcodeToSheet = false)
        advanceUntilIdle()
        val saved = tags.items["E2000ABC"]!!
        assertEquals("SKU1", saved.sku)
        assertEquals(100, saved.updatedAt)
        assertTrue(vm.done.value)
    }

    @Test
    fun assign_structured_writesEncodedEpcToTagAndMapping() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = viewModel(reader, tags, this.backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = true, pushBarcodeToSheet = false)
        advanceUntilIdle()
        assertEquals("E2000ABC", reader.lastWrittenEpc?.first)
        assertTrue(reader.lastWrittenEpc?.second?.startsWith("KOI-SKU1-") == true)
        assertTrue(tags.items.keys.any { it.startsWith("KOI-SKU1-") })
    }

    @Test
    fun trigger_click_scansBlankTag() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000TRIGGER", -30) }
        val vm = viewModel(reader, FakeTagRepo(), this.backgroundScope)
        runCurrent()

        reader.emitTrigger(true)
        runCurrent()
        advanceUntilIdle()

        assertEquals("E2000TRIGGER", vm.scannedEpc.value)
        assertEquals(1, reader.singleScanCount)
    }

    private fun sampleProducts() = FakeProductRepo(
        mutableMapOf(
            "SKU1" to Product("SKU1", "Ca KOI", "con", TrackingMode.SERIALIZED, 1, "A-03"),
        ),
    )

    private fun viewModel(
        reader: FakeRfidReader,
        tags: FakeTagRepo,
        scope: CoroutineScope,
    ) = AssignTagViewModel(
        reader = reader,
        tagRepo = tags,
        productRepo = sampleProducts(),
        gsheetWriteRepo = FakeGsheetWriteRepo(),
        assignSessionRepo = FakeAssignSessionRepo(),
        deviceId = "dev-1",
        now = { 100 },
        scope = scope,
    )
}
