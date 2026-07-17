package com.example.koistock.ui.warehouse

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.remote.LocationRepo
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.WarehouseSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class StockFilter { ALL, IN_STOCK, OUT_OF_STOCK }

data class ProductFilters(
    val query: String = "",
    val trackingMode: TrackingMode? = null,
    val stock: StockFilter = StockFilter.ALL,
    val unit: String? = null,
    val zoneCode: String? = null,
    val shelfCode: String? = null,
)

data class ProductEditorState(
    val sku: String,
    val name: String,
    val unit: String,
    val trackingMode: TrackingMode,
    val shelfCode: String,
    val imageUrl: String?,
)

sealed interface ProductSaveResult {
    data object Success : ProductSaveResult
    data class SavedButSyncFailed(val message: String) : ProductSaveResult
    data class Error(val message: String) : ProductSaveResult
}

class ProductManagementViewModel(
    private val productRepo: ProductRepo,
    locationRepo: LocationRepo,
    private val syncAfterSave: suspend () -> WarehouseSyncResult,
    scope: CoroutineScope,
) {
    private val products = productRepo.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val locations = locationRepo.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val mutableFilters = MutableStateFlow(ProductFilters())
    val filters: StateFlow<ProductFilters> = mutableFilters.asStateFlow()

    private val mutableEditor = MutableStateFlow<ProductEditorState?>(null)
    val editor: StateFlow<ProductEditorState?> = mutableEditor.asStateFlow()
    private var originalProduct: Product? = null

    val availableUnits: StateFlow<List<String>> = products
        .combine(mutableFilters) { items, _ -> items.map { it.unit }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val zones: StateFlow<List<LocationNode>> = locations
        .combine(mutableFilters) { nodes, _ -> nodes.filter { it.type == LocationType.ZONE }.sortedBy { it.code } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val shelvesForSelectedZone: StateFlow<List<LocationNode>> = locations
        .combine(mutableFilters) { nodes, current ->
            nodes.filter {
                it.type == LocationType.SHELF &&
                    (current.zoneCode == null || it.parent == current.zoneCode)
            }.sortedBy { it.code }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val filteredProducts: StateFlow<List<Product>> = combine(products, locations, mutableFilters) { items, nodes, current ->
        val shelfParent = nodes.filter { it.type == LocationType.SHELF }.associate { it.code to it.parent }
        items.filter { product ->
            val queryMatches = current.query.isBlank() ||
                product.sku.contains(current.query, ignoreCase = true) ||
                product.name.contains(current.query, ignoreCase = true)
            val stockMatches = when (current.stock) {
                StockFilter.ALL -> true
                StockFilter.IN_STOCK -> product.quantity > 0
                StockFilter.OUT_OF_STOCK -> product.quantity <= 0
            }
            queryMatches &&
                (current.trackingMode == null || product.trackingMode == current.trackingMode) &&
                stockMatches &&
                (current.unit == null || product.unit == current.unit) &&
                (current.zoneCode == null || shelfParent[product.locationCode] == current.zoneCode) &&
                (current.shelfCode == null || product.locationCode == current.shelfCode)
        }.sortedBy { it.sku }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) = updateFilters { copy(query = value) }
    fun setTrackingMode(value: TrackingMode?) = updateFilters { copy(trackingMode = value) }
    fun setStockFilter(value: StockFilter) = updateFilters { copy(stock = value) }
    fun setUnit(value: String?) = updateFilters { copy(unit = value) }
    fun setZone(value: String?) = updateFilters { copy(zoneCode = value, shelfCode = null) }
    fun setShelf(value: String?) = updateFilters { copy(shelfCode = value) }
    fun clearFilters() { mutableFilters.value = ProductFilters() }

    fun selectProduct(sku: String) {
        originalProduct = products.value.firstOrNull { it.sku == sku }
        resetEditor()
    }

    fun closeEditor() {
        originalProduct = null
        mutableEditor.value = null
    }

    fun resetEditor() {
        mutableEditor.value = originalProduct?.toEditor()
    }

    fun updateEditor(
        name: String,
        unit: String,
        trackingMode: TrackingMode,
        shelfCode: String,
        imageUrl: String?,
    ) {
        mutableEditor.value = mutableEditor.value?.copy(
            name = name,
            unit = unit,
            trackingMode = trackingMode,
            shelfCode = shelfCode,
            imageUrl = imageUrl,
        )
    }

    suspend fun save(): ProductSaveResult {
        val edit = mutableEditor.value ?: return ProductSaveResult.Error("Chưa chọn sản phẩm")
        val original = originalProduct ?: return ProductSaveResult.Error("Không còn dữ liệu sản phẩm gốc")
        if (edit.name.isBlank()) return ProductSaveResult.Error("Tên sản phẩm không được để trống")
        if (edit.unit.isBlank()) return ProductSaveResult.Error("Đơn vị không được để trống")
        if (locations.value.none { it.code == edit.shelfCode && it.type == LocationType.SHELF }) {
            return ProductSaveResult.Error("Kệ đã chọn không còn tồn tại")
        }

        val updated = original.copy(
            name = edit.name.trim(),
            unit = edit.unit.trim(),
            trackingMode = edit.trackingMode,
            locationCode = edit.shelfCode,
            imageUrl = edit.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
            origin = "app",
        )
        runCatching { productRepo.upsert(updated) }
            .onFailure { return ProductSaveResult.Error(it.message ?: "Không lưu được sản phẩm") }

        return when (val sync = syncAfterSave()) {
            WarehouseSyncResult.Success -> {
                originalProduct = updated
                mutableEditor.value = updated.toEditor()
                ProductSaveResult.Success
            }
            is WarehouseSyncResult.SavedButSyncFailed -> ProductSaveResult.SavedButSyncFailed(sync.message)
            is WarehouseSyncResult.LoadFailed -> ProductSaveResult.SavedButSyncFailed(sync.message)
        }
    }

    private fun updateFilters(transform: ProductFilters.() -> ProductFilters) {
        mutableFilters.value = mutableFilters.value.transform()
    }

    private fun Product.toEditor() = ProductEditorState(
        sku = sku,
        name = name,
        unit = unit,
        trackingMode = trackingMode,
        shelfCode = locationCode,
        imageUrl = imageUrl,
    )
}
