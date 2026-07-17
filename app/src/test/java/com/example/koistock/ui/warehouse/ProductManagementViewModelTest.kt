package com.example.koistock.ui.warehouse

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.remote.LocationRepo
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.WarehouseSyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductManagementViewModelTest {
    private class ProductSource(initial: List<Product>) : ProductRepo {
        val flow = MutableStateFlow(initial)
        var saved: Product? = null
        var failSave = false
        override suspend fun getBySku(sku: String) = flow.value.firstOrNull { it.sku == sku }
        override fun observeAll(): Flow<List<Product>> = flow
        override suspend fun upsert(product: Product) {
            if (failSave) error("save failed")
            saved = product
            flow.value = flow.value.map { if (it.sku == product.sku) product else it }
        }
    }

    private class LocationSource(initial: List<LocationNode>) : LocationRepo {
        val flow = MutableStateFlow(initial)
        override fun observeAll(): Flow<List<LocationNode>> = flow
        override suspend fun upsert(location: LocationNode) {
            flow.value = flow.value + location
        }
    }

    private val locations = listOf(
        LocationNode("A", "Khu A", LocationType.ZONE),
        LocationNode("B", "Khu B", LocationType.ZONE),
        LocationNode("A-01", "Kệ 1", LocationType.SHELF, parent = "A"),
        LocationNode("B-01", "Kệ B", LocationType.SHELF, parent = "B"),
    )

    private val products = listOf(
        Product("SKU-1", "Cá Koi", "Con", TrackingMode.SERIALIZED, 2, "A-01"),
        Product("SKU-2", "Thức ăn", "Bao", TrackingMode.BULK, 0, "B-01"),
        Product("ABC", "Máy bơm", "Cái", TrackingMode.BULK, 3, "B-01"),
    )

    @Test
    fun searchAndFilters_areCombined() = runTest {
        val vm = ProductManagementViewModel(ProductSource(products), LocationSource(locations), { WarehouseSyncResult.Success }, backgroundScope)
        runCurrent()

        vm.setQuery("sku")
        vm.setTrackingMode(TrackingMode.SERIALIZED)
        vm.setStockFilter(StockFilter.IN_STOCK)
        vm.setZone("A")
        runCurrent()

        assertEquals(listOf("SKU-1"), vm.filteredProducts.value.map { it.sku })
    }

    @Test
    fun selectedZoneNarrowsShelves_andClearRestoresAll() = runTest {
        val vm = ProductManagementViewModel(ProductSource(products), LocationSource(locations), { WarehouseSyncResult.Success }, backgroundScope)
        runCurrent()

        vm.setZone("A")
        runCurrent()
        assertEquals(listOf("A-01"), vm.shelvesForSelectedZone.value.map { it.code })

        vm.clearFilters()
        runCurrent()
        assertEquals(3, vm.filteredProducts.value.size)
        assertNull(vm.filters.value.zoneCode)
    }

    @Test
    fun selectAndUndo_prefillsOriginalProductWithImmutableSku() = runTest {
        val vm = ProductManagementViewModel(ProductSource(products), LocationSource(locations), { WarehouseSyncResult.Success }, backgroundScope)
        runCurrent()

        vm.selectProduct("SKU-1")
        vm.updateEditor(name = "Đã đổi", unit = "Chiếc", trackingMode = TrackingMode.BULK, shelfCode = "B-01", imageUrl = "x")
        vm.resetEditor()

        assertEquals("SKU-1", vm.editor.value?.sku)
        assertEquals("Cá Koi", vm.editor.value?.name)
        assertEquals("A-01", vm.editor.value?.shelfCode)
    }

    @Test
    fun save_rejectsStaleShelfAndKeepsEditor() = runTest {
        val vm = ProductManagementViewModel(ProductSource(products), LocationSource(locations), { WarehouseSyncResult.Success }, backgroundScope)
        runCurrent()
        vm.selectProduct("SKU-1")
        vm.updateEditor("Cá Koi", "Con", TrackingMode.SERIALIZED, "MISSING", null)

        val result = vm.save()

        assertTrue(result is ProductSaveResult.Error)
        assertEquals("MISSING", vm.editor.value?.shelfCode)
    }

    @Test
    fun save_upsertsThenSynchronizes() = runTest {
        val repo = ProductSource(products)
        var syncCalls = 0
        val vm = ProductManagementViewModel(repo, LocationSource(locations), { syncCalls++; WarehouseSyncResult.Success }, backgroundScope)
        runCurrent()
        vm.selectProduct("SKU-1")
        vm.updateEditor("Koi Nhật", "Con", TrackingMode.SERIALIZED, "A-01", null)

        val result = vm.save()

        assertEquals(ProductSaveResult.Success, result)
        assertEquals("SKU-1", repo.saved?.sku)
        assertEquals("Koi Nhật", repo.saved?.name)
        assertEquals(1, syncCalls)
    }
}
