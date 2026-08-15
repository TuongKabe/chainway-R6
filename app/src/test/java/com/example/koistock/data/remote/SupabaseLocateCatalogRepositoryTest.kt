package com.example.koistock.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class SupabaseLocateCatalogRepositoryTest {
    @Test
    fun refresh_preservesWarehouseLocationBinAndRevisionForEachEpc() = runTest {
        val payload = CatalogPayload(
            revision = 21,
            items = listOf(CatalogItemDto("G65", "Pueblo", "pcs", "SERIALIZED", true, 20)),
            tags = listOf(
                CatalogTagDto(
                    epc = "EPC-G65",
                    itemCode = "G65",
                    status = "active",
                    serialNo = "SERIAL-1",
                    warehouse = "KHO-A",
                    locationCode = "A-01",
                    bin = "BIN-7",
                    syncRev = 21,
                ),
            ),
        )
        val api = object : SupabaseCatalogApi {
            override suspend fun snapshot() = CatalogEnvelope(payload)
            override suspend fun changes(afterRev: Long) = CatalogEnvelope(CatalogPayload(afterRev))
        }
        val cache = MemoryPayloadCache()

        val result = SupabaseLocateCatalogRepository(api, cache).refresh()

        val tag = result.single().activeTags.single()
        assertEquals("KHO-A", tag.warehouse)
        assertEquals("A-01", tag.locationCode)
        assertEquals("BIN-7", tag.bin)
        assertEquals(21L, tag.syncRev)
    }

    @Test
    fun refresh_matchesItemAndTagIgnoringCaseAndWhitespaceInItemCode() = runTest {
        val payload = CatalogPayload(
            revision = 1,
            items = listOf(CatalogItemDto(" g65 ", "Pueblo", "pcs", "SERIALIZED", true, 1)),
            tags = listOf(CatalogTagDto("EPC-G65", "G65", "active", null, "MAIN", null, 1)),
        )
        val api = object : SupabaseCatalogApi {
            override suspend fun snapshot() = CatalogEnvelope(payload)
            override suspend fun changes(afterRev: Long) = CatalogEnvelope(CatalogPayload(afterRev))
        }

        val result = SupabaseLocateCatalogRepository(api, MemoryPayloadCache()).refresh()

        assertEquals(1, result.size)
        assertEquals("EPC-G65", result.single().activeTags.single().epc)
    }

    @Test
    fun findBySku_queriesServerSnapshotInsteadOfOnlyLocalCache() = runTest {
        val payload = CatalogPayload(
            revision = 1,
            items = listOf(CatalogItemDto("G65", "Pueblo", "pcs", "SERIALIZED", true, 1)),
            tags = listOf(CatalogTagDto("EPC-G65", "G65", "active", null, "MAIN", null, 1)),
        )
        val api = object : SupabaseCatalogApi {
            override suspend fun snapshot() = CatalogEnvelope(payload)
            override suspend fun changes(afterRev: Long) = CatalogEnvelope(CatalogPayload(afterRev))
        }
        // Cache máy rỗng: nếu findBySku chỉ đọc cache sẽ trả về null dù server có SKU này.
        val cache = MemoryPayloadCache()

        val found = SupabaseLocateCatalogRepository(api, cache).findBySku("G65")

        assertEquals("G65", found?.product?.sku)
        assertEquals(payload, cache.readPayload())
    }

    @Test
    fun findBySku_fallsBackToKoiApiWhenSkuMissingFromSupabaseSnapshot() = runTest {
        // Supabase catalog rỗng (SKU chưa được đồng bộ sang) — nhưng backend gốc đã có SKU này.
        val emptyPayload = CatalogPayload(revision = 1)
        val supabaseApi = object : SupabaseCatalogApi {
            override suspend fun snapshot() = CatalogEnvelope(emptyPayload)
            override suspend fun changes(afterRev: Long) = CatalogEnvelope(CatalogPayload(afterRev))
        }
        val fallbackApi = Proxy.newProxyInstance(
            KoiApiService::class.java.classLoader,
            arrayOf(KoiApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "searchItems" -> ApiEnvelope(
                    listOf(ItemDto("OR33", "Minerva Brown", "pia", "BULK", isActive = true)),
                )
                "getTagsByItem" -> ApiEnvelope(
                    listOf(EpcTagDto("EPC-OR33", "OR33", status = "active", warehouse = "MAIN")),
                )
                "toString" -> "FakeKoiApiService"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KoiApiService

        val repo = SupabaseLocateCatalogRepository(supabaseApi, MemoryPayloadCache(), fallbackApi)
        val found = repo.findBySku("OR33")

        assertEquals("OR33", found?.product?.sku)
        assertEquals(listOf("EPC-OR33"), found?.activeTags?.map { it.epc })
    }

    @Test
    fun findBySku_withoutFallbackApi_returnsNullWhenMissingFromSupabaseSnapshot() = runTest {
        val emptyPayload = CatalogPayload(revision = 1)
        val supabaseApi = object : SupabaseCatalogApi {
            override suspend fun snapshot() = CatalogEnvelope(emptyPayload)
            override suspend fun changes(afterRev: Long) = CatalogEnvelope(CatalogPayload(afterRev))
        }

        val found = SupabaseLocateCatalogRepository(supabaseApi, MemoryPayloadCache()).findBySku("OR33")

        assertNull(found)
    }

    private class MemoryPayloadCache : CatalogPayloadCache {
        private var value: CatalogPayload? = null
        override suspend fun readPayload(): CatalogPayload? = value
        override suspend fun writePayload(payload: CatalogPayload) {
            value = payload
        }
    }
}
