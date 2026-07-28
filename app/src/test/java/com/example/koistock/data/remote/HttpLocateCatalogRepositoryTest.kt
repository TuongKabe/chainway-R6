package com.example.koistock.data.remote

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class HttpLocateCatalogRepositoryTest {
    private class MemoryCache(
        var snapshot: LocateCatalogSnapshot? = null,
    ) : LocateCatalogCache {
        override suspend fun read() = snapshot
        override suspend fun write(snapshot: LocateCatalogSnapshot) {
            this.snapshot = snapshot
        }
    }

    @Test
    fun refresh_unchangedSignature_reusesCachedProductWithoutGetItem() = runTest {
        val tag = EpcTagDto("EPC-A", "SKU-A", status = "active", warehouse = "MAIN", syncRev = "7")
        val mapping = TagMapping("EPC-A", "SKU-A", status = "active", locationCode = "MAIN", syncRev = 7)
        val cachedItem = LocatableProduct(
            Product("SKU-A", "Koi cached", "con", TrackingMode.SERIALIZED, 1, "MAIN"),
            listOf(mapping),
        )
        val cache = MemoryCache(
            LocateCatalogSnapshot(
                items = listOf(cachedItem),
                tagSignatureBySku = mapOf("SKU-A" to tagSignature(listOf(mapping))),
                metadataVerifiedAt = 100L,
            ),
        )
        val calls = mutableListOf<String>()
        val api = fakeApi(listOf(tag), emptyList(), calls)

        val result = HttpLocateCatalogRepository(api, cache, now = { 200L }).refresh()

        assertEquals(listOf("getActiveTags"), calls)
        assertEquals("Koi cached", result.single().product.name)
    }

    @Test
    fun load_fetchesEachTaggedSkuOnceWithoutInventoryEnrichmentCalls() = runTest {
        val calls = mutableListOf<String>()
        val items = listOf(
            ItemDto("SKU-A", "Koi A", "con", "SERIALIZED", isActive = true),
            ItemDto("SKU-C", "Koi C", "con", "SERIALIZED", isActive = true),
            ItemDto("DA-BO-001", "Da bò", "pcs", "BULK", isActive = false),
        )
        val tags = listOf(
            EpcTagDto("EPC-A1", "SKU-A", status = "active", warehouse = "MAIN"),
            EpcTagDto("EPC-A2", "SKU-A", status = "ACTIVE", warehouse = "MAIN"),
            EpcTagDto("EPC-C1", "SKU-C", status = "active", warehouse = "MAIN"),
            EpcTagDto("EPC-INACTIVE", "DA-BO-001", status = "active", warehouse = "MAIN"),
            EpcTagDto("EPC-VOID", "SKU-A", status = "void", warehouse = "MAIN"),
        )
        val api = Proxy.newProxyInstance(
            KoiApiService::class.java.classLoader,
            arrayOf(KoiApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getActiveTags" -> ApiEnvelope(tags).also { calls += method.name }
                "getItem" -> {
                    val sku = args?.firstOrNull() as String
                    calls += "getItem:$sku"
                    ApiEnvelope(items.single { it.itemCode == sku })
                }
                "toString" -> "FakeKoiApiService"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KoiApiService

        val cache = MemoryCache()
        val repo = HttpLocateCatalogRepository(api, cache, now = { 100L })
        val result = repo.refresh()

        assertEquals(
            listOf("getActiveTags", "getItem:DA-BO-001", "getItem:SKU-A", "getItem:SKU-C"),
            calls.sorted(),
        )
        assertEquals(listOf("SKU-A", "SKU-C"), result.map { it.product.sku })
        val skuA = result.first()
        assertEquals(2L, skuA.product.quantity)
        assertEquals("MAIN", skuA.product.locationCode)
        assertEquals(listOf("EPC-A1", "EPC-A2"), skuA.activeTags.map { it.epc })
        assertEquals(result, repo.loadCached())
    }

    @Test
    fun findBySku_searchesPartialQueryAndLoadsTheMatchedProductsActiveEpcs() = runTest {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(
            KoiApiService::class.java.classLoader,
            arrayOf(KoiApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "searchItems" -> ApiEnvelope(
                    listOf(ItemDto("OR31", "Remote koi", "con", "SERIALIZED", isActive = true)),
                ).also { calls += "searchItems:${args?.firstOrNull()}" }
                "getTagsByItem" -> ApiEnvelope(
                    listOf(
                        EpcTagDto("EPC-OR31", "OR31", status = "active", warehouse = "MAIN"),
                        EpcTagDto("EPC-VOID", "OR31", status = "void", warehouse = "MAIN"),
                    ),
                ).also { calls += "getTagsByItem:${args?.firstOrNull()}" }
                "toString" -> "FakeKoiApiService"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KoiApiService

        val result = HttpLocateCatalogRepository(api, MemoryCache()).findBySku(" r31 ")

        assertEquals("OR31", result?.product?.sku)
        assertEquals(listOf("EPC-OR31"), result?.activeTags?.map { it.epc })
        assertEquals(
            listOf("searchItems:r31", "getTagsByItem:OR31"),
            calls,
        )
    }
    private fun fakeApi(
        tags: List<EpcTagDto>,
        items: List<ItemDto>,
        calls: MutableList<String>,
    ): KoiApiService = Proxy.newProxyInstance(
        KoiApiService::class.java.classLoader,
        arrayOf(KoiApiService::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getActiveTags" -> ApiEnvelope(tags).also { calls += method.name }
            "getItem" -> {
                val sku = args?.firstOrNull() as String
                calls += "getItem:$sku"
                ApiEnvelope(items.single { it.itemCode == sku })
            }
            "toString" -> "FakeKoiApiService"
            "hashCode" -> 0
            "equals" -> false
            else -> error("Unexpected API call: ${method.name}")
        }
    } as KoiApiService
}
