package com.example.koistock.ui.lookup

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.FakeProductRepo
import com.example.koistock.fakes.FakeTagRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LookupViewModelTest {
    @Test
    fun scan_knownTag_returnsFound() = runTest {
        val reader = FakeRfidReader().apply {
            scannedSingle = ScannedTag("KOI-SKU1-1", -40)
        }
        val tags = FakeTagRepo(
            mutableMapOf(
                "KOI-SKU1-1" to TagMapping("KOI-SKU1-1", "SKU1"),
            ),
        )
        val products = FakeProductRepo(
            mutableMapOf(
                "SKU1" to Product("SKU1", "Ca KOI", "con", TrackingMode.SERIALIZED, 3, "A-03"),
            ),
        )

        val vm = LookupViewModel(reader, tags, products, backgroundScope)
        vm.scanOnce()
        advanceUntilIdle()

        val result = vm.result.value
        assertTrue(result is LookupResult.Found)
        assertEquals("Ca KOI", (result as LookupResult.Found).product.name)
    }

    @Test
    fun scan_unmappedTag_returnsUnknown() = runTest {
        val reader = FakeRfidReader().apply {
            scannedSingle = ScannedTag("E2000999", -40)
        }
        val vm = LookupViewModel(reader, FakeTagRepo(), FakeProductRepo(), backgroundScope)

        vm.scanOnce()
        advanceUntilIdle()

        assertTrue(vm.result.value is LookupResult.UnknownTag)
    }

    @Test
    fun scan_noTag_returnsNotFound() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = null }
        val vm = LookupViewModel(reader, FakeTagRepo(), FakeProductRepo(), backgroundScope)

        vm.scanOnce()
        advanceUntilIdle()

        assertEquals(LookupResult.NotFound, vm.result.value)
    }
}
