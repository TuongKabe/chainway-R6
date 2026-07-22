package com.example.koistock.domain

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CountInventoryCalculatorTest {
    private val locations = listOf(
        LocationNode("ZONE-A", "Khu A", LocationType.ZONE),
        LocationNode("SHELF-X", "Kệ X", LocationType.SHELF, parent = "ZONE-A"),
        LocationNode("ZONE-B", "Khu B", LocationType.ZONE),
        LocationNode("SHELF-Y", "Kệ Y", LocationType.SHELF, parent = "ZONE-B"),
    )
    private val items = listOf(
        CountInventoryItem("BULK-1", "Áo", "cái", TrackingMode.BULK),
        CountInventoryItem("SER-1", "Quần", "cái", TrackingMode.SERIALIZED),
    )
    private val bins = listOf(
        CountBinStock("BULK-1", "SHELF-X", "4"),
        CountBinStock("BULK-1", "SHELF-Y", "6"),
    )
    private val tags = listOf(
        CountTagStock("SER-1", "SHELF-X", active = true),
        CountTagStock("SER-1", "SHELF-X", active = false),
        CountTagStock("SER-1", "SHELF-Y", active = true),
    )

    @Test
    fun blankScopeIncludesAllLocations() {
        val result = CountInventoryCalculator.calculate("", locations, items, bins, tags)

        assertEquals(CountScope.EntireWarehouse, result.scope)
        assertEquals(10, result.expected.single { it.sku == "BULK-1" }.expectedQty)
        assertEquals("cái", result.expected.single { it.sku == "BULK-1" }.unit)
        assertEquals(2, result.expected.single { it.sku == "SER-1" }.expectedQty)
    }

    @Test
    fun zoneUsesParentRelationshipInsteadOfCodePrefix() {
        val result = CountInventoryCalculator.calculate("ZONE-A", locations, items, bins, tags)

        assertEquals(
            setOf("ZONE-A", "SHELF-X"),
            (result.scope as CountScope.Location).includedCodes,
        )
        assertEquals(4, result.expected.single { it.sku == "BULK-1" }.expectedQty)
    }

    @Test
    fun shelfIsExactAndSerializedCountsOnlyActiveTags() {
        val result = CountInventoryCalculator.calculate("SHELF-X", locations, items, bins, tags)

        assertEquals(setOf("SHELF-X"), (result.scope as CountScope.Location).includedCodes)
        assertEquals(1, result.expected.single { it.sku == "SER-1" }.expectedQty)
    }

    @Test
    fun shelfDoesNotIncludeLegacyChildLocation() {
        val legacyLocations = locations +
            LocationNode("LEGACY-CHILD", "Dữ liệu cũ", LocationType.SHELF, parent = "SHELF-X")
        val legacyBins = bins + CountBinStock("BULK-1", "LEGACY-CHILD", "99")

        val result = CountInventoryCalculator.calculate(
            "SHELF-X", legacyLocations, items, legacyBins, tags,
        )

        assertEquals(setOf("SHELF-X"), (result.scope as CountScope.Location).includedCodes)
        assertEquals(4, result.expected.single { it.sku == "BULK-1" }.expectedQty)
    }

    @Test
    fun snapshotTracksSkusWithPositiveStockAnywhere() {
        val result = CountInventoryCalculator.calculate("SHELF-X", locations, items, bins, tags)

        assertEquals(setOf("BULK-1", "SER-1"), result.skusWithStockAnywhere)
    }

    @Test
    fun unknownNonBlankLocationIsRejected() {
        val error = try {
            CountInventoryCalculator.calculate("UNKNOWN", locations, items, bins, tags)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            error
        }

        assertEquals("Không tìm thấy khu/kệ: UNKNOWN", error.message)
    }
}
