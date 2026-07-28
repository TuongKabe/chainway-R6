package com.example.koistock.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDeltaMergerTest {
    @Test
    fun applyDelta_upsertsChangedRowsRemovesInactiveRowsAndAdvancesRevision() {
        val current = CatalogPayload(
            revision = 10,
            items = listOf(
                CatalogItemDto("A", "Old A", "pcs", "SERIALIZED", true, 8),
                CatalogItemDto("B", "Old B", "pcs", "SERIALIZED", true, 9),
            ),
            tags = listOf(
                CatalogTagDto("EPC-A", "A", "active", null, "MAIN", null, 8),
                CatalogTagDto("EPC-B", "B", "active", null, "MAIN", null, 9),
            ),
        )
        val delta = CatalogPayload(
            revision = 13,
            items = listOf(
                CatalogItemDto("A", "New A", "pcs", "SERIALIZED", true, 11),
                CatalogItemDto("B", "Old B", "pcs", "SERIALIZED", false, 12),
            ),
            tags = listOf(CatalogTagDto("EPC-B", "B", "void", null, "MAIN", null, 13)),
        )

        val merged = CatalogDeltaMerger.apply(current, delta)

        assertEquals(13, merged.revision)
        assertEquals(listOf("A"), merged.items.map { it.itemCode })
        assertEquals("New A", merged.items.single().itemName)
        assertEquals(listOf("EPC-A"), merged.tags.map { it.epc })
    }
}
