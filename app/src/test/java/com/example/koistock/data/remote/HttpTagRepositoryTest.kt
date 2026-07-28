package com.example.koistock.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class HttpTagRepositoryTest {
    @Test
    fun listActive_mapsActiveTagFields() = runTest {
        val dto = EpcTagDto(
            epc = "EPC-1",
            itemCode = "SKU-1",
            serialNo = "0001",
            status = "active",
            warehouse = "A-01",
            syncRev = "7",
        )
        val api = Proxy.newProxyInstance(
            KoiApiService::class.java.classLoader,
            arrayOf(KoiApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getActiveTags" -> {
                    assertEquals("active", args?.firstOrNull())
                    ApiEnvelope(listOf(dto))
                }
                "toString" -> "FakeKoiApiService"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KoiApiService

        val result = HttpTagRepository(api).listActive()

        assertEquals(1, result.size)
        assertEquals("EPC-1", result.single().epc)
        assertEquals("SKU-1", result.single().sku)
        assertEquals("0001", result.single().unitSerial)
        assertEquals("active", result.single().status)
        assertEquals("A-01", result.single().locationCode)
        assertEquals(7L, result.single().syncRev)
    }
}
