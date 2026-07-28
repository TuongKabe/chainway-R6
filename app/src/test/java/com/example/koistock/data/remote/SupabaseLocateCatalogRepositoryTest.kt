package com.example.koistock.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private class MemoryPayloadCache : CatalogPayloadCache {
        private var value: CatalogPayload? = null
        override suspend fun readPayload(): CatalogPayload? = value
        override suspend fun writePayload(payload: CatalogPayload) {
            value = payload
        }
    }
}
