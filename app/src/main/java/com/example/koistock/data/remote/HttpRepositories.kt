package com.example.koistock.data.remote

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.model.Transaction
import com.example.koistock.data.model.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException

class HttpProductRepository(
    private val api: KoiApiService,
) : ProductRepo {
    private val products = MutableStateFlow<List<Product>>(emptyList())

    override suspend fun getBySku(sku: String): Product? = runCatching {
        val item = api.getItem(sku).data
        item.toProduct(aggregateQuantityFor(item.itemCode), defaultLocationFor(item.itemCode))
    }.getOrNull()

    override fun observeAll(): Flow<List<Product>> = products.asStateFlow()

    suspend fun refresh() {
        val items = runCatching { api.getItems().data.filter { it.isActive } }.getOrDefault(emptyList())
        val bins = runCatching { api.getBins().data }.getOrDefault(emptyList())
        val tagsByItem = items.associate { item ->
            item.itemCode to runCatching { api.getTagsByItem(item.itemCode).data }.getOrDefault(emptyList())
        }
        products.value = items.map { item ->
            val qty = when (item.trackingMode) {
                "SERIALIZED" -> tagsByItem[item.itemCode].orEmpty().count { it.status == "active" }.toLong()
                else -> bins.filter { it.itemCode == item.itemCode }.sumOf { it.actualQty.toDoubleOrNull()?.toLong() ?: 0L }
            }
            val location = bins.firstOrNull { it.itemCode == item.itemCode }?.warehouse
                ?: tagsByItem[item.itemCode].orEmpty().firstOrNull { !it.warehouse.isNullOrBlank() }?.warehouse
            item.toProduct(qty, location ?: item.defaultWarehouse)
        }
    }

    override suspend fun upsert(product: Product) {
        api.upsertItem(
            ItemDto(
                itemCode = product.sku,
                itemName = product.name,
                stockUom = product.unit,
                trackingMode = product.trackingMode.name,
                defaultWarehouse = product.locationCode.ifBlank { null },
                imageUrl = product.imageUrl,
                isActive = true,
                syncRev = product.syncRev.toString(),
            ),
        )
        refresh()
    }

    private suspend fun aggregateQuantityFor(itemCode: String): Long {
        val item = runCatching { api.getItem(itemCode).data }.getOrNull() ?: return 0
        return if (item.trackingMode == "SERIALIZED") {
            runCatching { api.getTagsByItem(itemCode).data }.getOrDefault(emptyList()).count { it.status == "active" }.toLong()
        } else {
            runCatching { api.getBins().data }.getOrDefault(emptyList())
                .filter { it.itemCode == itemCode }
                .sumOf { it.actualQty.toDoubleOrNull()?.toLong() ?: 0L }
        }
    }

    private suspend fun defaultLocationFor(itemCode: String): String? {
        val bins = runCatching { api.getBins().data }.getOrDefault(emptyList())
        return bins.firstOrNull { it.itemCode == itemCode }?.warehouse
    }

    private fun ItemDto.toProduct(quantity: Long, locationCode: String?): Product = Product(
        sku = itemCode,
        name = itemName,
        unit = stockUom,
        trackingMode = TrackingMode.valueOf(trackingMode),
        quantity = quantity,
        locationCode = locationCode.orEmpty(),
        imageUrl = imageUrl,
        syncRev = syncRev?.toLongOrNull() ?: 0L,
        origin = "api",
    )
}

class HttpTagRepository(
    private val api: KoiApiService,
) : TagRepo {
    override suspend fun getByEpc(epc: String): TagMapping? = try {
        api.getTag(epc).data.toTagMapping()
    } catch (_: HttpException) {
        null
    } catch (_: IOException) {
        null
    }

    override suspend fun upsert(tag: TagMapping) {
        api.upsertTag(
            EpcTagDto(
                epc = tag.epc,
                itemCode = tag.sku,
                serialNo = tag.unitSerial,
                status = tag.status,
                warehouse = tag.locationCode,
                syncRev = tag.syncRev.toString(),
            ),
        )
    }

    override suspend fun listBySku(sku: String): List<TagMapping> =
        runCatching { api.getTagsByItem(sku).data.map { it.toTagMapping() } }.getOrDefault(emptyList())

    private fun EpcTagDto.toTagMapping(): TagMapping = TagMapping(
        epc = epc,
        sku = itemCode,
        unitSerial = serialNo,
        status = status,
        locationCode = warehouse,
        syncRev = syncRev?.toLongOrNull() ?: 0L,
        origin = "api",
    )
}

class HttpTransactionRepository(
    private val api: KoiApiService,
) : TransactionRepo {
    override suspend fun append(transaction: Transaction) {
        api.commitStock(
            CommitRequestDto(
                commandId = transaction.id,
                type = transaction.type.name,
                deviceId = transaction.deviceId,
                movements = listOf(
                    CommitMovementDto(
                        itemCode = transaction.sku,
                        epc = transaction.epc,
                        qtyChange = transaction.delta.toDouble(),
                        warehouse = transaction.locationCode,
                    ),
                ),
            ),
        )
    }
}

class HttpLocationRepository(
    private val api: KoiApiService,
) : LocationRepo {
    private val locations = MutableStateFlow<List<LocationNode>>(emptyList())

    override fun observeAll(): Flow<List<LocationNode>> = locations.asStateFlow()

    suspend fun refresh() {
        locations.value = api.getWarehouses().data
            .filter { it.isActive }
            .map {
                LocationNode(
                    code = it.name,
                    name = it.warehouseName,
                    type = LocationType.valueOf(it.warehouseType),
                    parent = it.parentWarehouse,
                    syncRev = it.syncRev?.toLongOrNull() ?: 0L,
                    origin = "api",
                )
            }
    }

    override suspend fun upsert(location: LocationNode) {
        api.upsertWarehouse(
            WarehouseDto(
                name = location.code,
                warehouseName = location.name,
                parentWarehouse = location.parent,
                warehouseType = location.type.name,
                isActive = true,
                syncRev = location.syncRev.toString(),
            ),
        )
        refresh()
    }
}

sealed interface SyncOutcome {
    data class Success(val runId: String?, val note: String?) : SyncOutcome
    data class Failure(val message: String) : SyncOutcome
}

/** Trigger đồng bộ 2 chiều PostgreSQL ↔ Google Sheet qua backend Koi. */
class HttpSyncRepository(
    private val api: KoiApiService,
) {
    suspend fun reconcile(): SyncOutcome = try {
        // Không bọc trong runCatching để HttpException/IOException nổi lên đúng nhánh catch.
        val dto: SyncResultDto? = api.syncReconcile().data
        val status = dto?.status?.lowercase()
        if (status != null && status in setOf("failed", "error", "upstream_failed")) {
            SyncOutcome.Failure(dto.message ?: "Đồng bộ thất bại ($status)")
        } else {
            SyncOutcome.Success(dto?.resolvedRunId, dto?.message ?: dto?.status)
        }
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string().orEmpty()
        SyncOutcome.Failure("Lỗi máy chủ (${e.code()})" + body.take(120).let { if (it.isNotBlank()) ": $it" else "" })
    } catch (e: IOException) {
        SyncOutcome.Failure("Không kết nối được backend. Kiểm tra mạng và máy chủ đồng bộ.")
    } catch (e: Exception) {
        SyncOutcome.Failure("Lỗi không xác định khi đồng bộ: ${e.message ?: e.javaClass.simpleName}")
    }
}

class HttpStockCommandRepository(
    private val api: KoiApiService,
) : StockCommandRepo {
    override suspend fun commit(
        commandId: String,
        type: TxType,
        deviceId: String,
        movements: List<StockMovement>,
    ): CommitStockResult {
        return try {
            val response = api.commitStock(
                CommitRequestDto(
                    commandId = commandId,
                    type = type.name,
                    deviceId = deviceId,
                    movements = movements.map {
                        CommitMovementDto(
                            itemCode = it.sku,
                            epc = it.epc,
                            qtyChange = it.delta.toDouble(),
                            warehouse = it.locationCode,
                        )
                    },
                ),
            ).data
            CommitStockResult.Success(response.commandId)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            when {
                body.contains("INVALID_EPC") -> CommitStockResult.InvalidTag("", body)
                body.contains("INVALID_ITEM") -> CommitStockResult.InvalidTag("", body)
                else -> throw e
            }
        }
    }
}
