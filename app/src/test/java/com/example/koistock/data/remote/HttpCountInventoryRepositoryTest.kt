package com.example.koistock.data.remote

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class HttpCountInventoryRepositoryTest {
    private val locations = listOf(
        LocationNode("A", "Khu A", LocationType.ZONE),
        LocationNode("A-01", "Kệ 1", LocationType.SHELF, parent = "A"),
    )

    @Test
    fun mapsBinsAndOnlyActiveTagsToDbStock() = runTest {
        val api = fakeApi(
            items = listOf(
                ItemDto("B1", "Áo", "cái", "BULK"),
                ItemDto("S1", "Quần", "cái", "SERIALIZED"),
            ),
            bins = listOf(BinDto("B1", "A-01", "10")),
            tagsByItem = mapOf(
                "B1" to emptyList(),
                "S1" to listOf(
                    EpcTagDto("E1", "S1", status = "active", locationCode = "A-01"),
                    EpcTagDto("E2", "S1", status = "void", locationCode = "A-01"),
                ),
            ),
        )

        val result = HttpCountInventoryRepository(api).load("", locations)

        assertEquals(10, result.expected.single { it.sku == "B1" }.expectedQty)
        assertEquals(1, result.expected.single { it.sku == "S1" }.expectedQty)
    }

    @Test
    fun binApiFailurePropagatesInsteadOfBecomingZeroStock() = runTest {
        val api = fakeApi(
            items = listOf(ItemDto("B1", "Áo", "cái", "BULK")),
            binFailure = IOException("offline"),
        )

        val error = try {
            HttpCountInventoryRepository(api).load("", locations)
            throw AssertionError("Expected IOException")
        } catch (error: Throwable) {
            error
        }

        val rootCause = generateSequence(error) { it.cause }.last()
        assertTrue(rootCause is IOException)
        assertTrue(rootCause.message.orEmpty().contains("offline"))
    }

    private fun fakeApi(
        items: List<ItemDto>,
        bins: List<BinDto> = emptyList(),
        tagsByItem: Map<String, List<EpcTagDto>> = emptyMap(),
        binFailure: Throwable? = null,
    ): KoiApiService = Proxy.newProxyInstance(
        KoiApiService::class.java.classLoader,
        arrayOf(KoiApiService::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getItems" -> ApiEnvelope(items)
            "getBins" -> binFailure?.let { throw it } ?: ApiEnvelope(bins)
            "getTagsByItem" -> ApiEnvelope(tagsByItem[args?.firstOrNull()] ?: emptyList())
            "toString" -> "FakeKoiApiService"
            "hashCode" -> 0
            "equals" -> false
            else -> error("Unexpected API call: ${method.name}")
        }
    } as KoiApiService
}
