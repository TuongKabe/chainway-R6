package com.example.koistock.data.remote

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class LocatableProduct(
    val product: Product,
    val activeTags: List<TagMapping>,
)

interface LocateCatalogRepo {
    suspend fun loadCached(): List<LocatableProduct>?
    suspend fun refresh(): List<LocatableProduct>
    suspend fun findBySku(sku: String): LocatableProduct? = null
}

class HttpLocateCatalogRepository(
    private val api: KoiApiService,
    private val cache: LocateCatalogCache,
    private val now: () -> Long = System::currentTimeMillis,
    private val metadataTtlMs: Long = METADATA_TTL_MS,
) : LocateCatalogRepo {
    override suspend fun loadCached(): List<LocatableProduct>? = cache.read()?.items
    override suspend fun findBySku(sku: String): LocatableProduct? {
        val query = sku.trim()
        if (query.isEmpty()) return null
        val candidates = api.searchItems(query).data.filter(ItemDto::isActive)
        val item = candidates.firstOrNull { it.itemCode.equals(query, ignoreCase = true) }
            ?: candidates.singleOrNull()
            ?: return null
        val activeTags = api.getTagsByItem(item.itemCode).data
            .filter { it.status.equals("active", ignoreCase = true) }
            .filter { it.itemCode.equals(item.itemCode, ignoreCase = true) }
            .map { it.toTagMapping() }
        return if (activeTags.isEmpty()) null else item.toLocatableProduct(activeTags)
    }


    override suspend fun refresh(): List<LocatableProduct> = coroutineScope {
        val previous = cache.read()
        val activeTagsBySku = api.getActiveTags().data
            .filter { it.status.equals("active", ignoreCase = true) }
            .filter { it.itemCode.isNotBlank() }
            .map { it.toTagMapping() }
            .groupBy { it.sku.trim() }
        val signatures = activeTagsBySku.mapValues { tagSignature(it.value) }
        val previousItems = previous?.items.orEmpty().associateBy { it.product.sku }
        val currentTime = now()
        val refreshAll = previous == null || currentTime - previous.metadataVerifiedAt >= metadataTtlMs
        val skusToRefresh = activeTagsBySku.keys.filter { sku ->
            refreshAll || previousItems[sku] == null || previous.tagSignatureBySku[sku] != signatures[sku]
        }.toSet()

        val refreshedItems = skusToRefresh.map { sku ->
            async {
                val item = api.getItem(sku).data
                sku to if (item.isActive) item.toLocatableProduct(activeTagsBySku.getValue(sku)) else null
            }
        }.awaitAll().toMap()

        val result = activeTagsBySku.keys.mapNotNull { sku ->
            if (sku in skusToRefresh) refreshedItems[sku] else previousItems[sku]
        }.sortedBy { it.product.sku }
        val snapshot = LocateCatalogSnapshot(
            items = result,
            tagSignatureBySku = signatures.filterKeys { sku -> result.any { it.product.sku == sku } },
            metadataVerifiedAt = if (refreshAll) currentTime else previous?.metadataVerifiedAt ?: currentTime,
        )
        runCatching { cache.write(snapshot) }
        result
    }

    private fun ItemDto.toLocatableProduct(tags: List<TagMapping>) = LocatableProduct(
        product = Product(
            sku = itemCode,
            name = itemName,
            unit = stockUom,
            trackingMode = TrackingMode.valueOf(trackingMode),
            quantity = tags.size.toLong(),
            locationCode = tags.firstNotNullOfOrNull { it.locationCode } ?: defaultWarehouse.orEmpty(),
            imageUrl = imageUrl,
            syncRev = syncRev?.toLongOrNull() ?: 0L,
            origin = "api",
        ),
        activeTags = tags.sortedBy(TagMapping::epc),
    )

    private fun EpcTagDto.toTagMapping() = TagMapping(
        epc = epc,
        sku = itemCode,
        unitSerial = serialNo,
        status = status,
        locationCode = locationCode ?: warehouse,
        syncRev = syncRev?.toLongOrNull() ?: 0L,
        origin = "api",
    )

    private companion object {
        const val METADATA_TTL_MS = 6 * 60 * 60 * 1000L
    }
}

internal fun tagSignature(tags: List<TagMapping>): String = tags
    .sortedBy(TagMapping::epc)
    .joinToString("||") { "${it.epc}|${it.status.lowercase()}|${it.locationCode.orEmpty()}|${it.syncRev}" }
