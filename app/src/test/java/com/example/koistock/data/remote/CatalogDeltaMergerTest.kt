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

    @Test
    fun applyDelta_keepsTagWhoseItemArrivesInALaterDelta() {
        // Tag của SKU "C" đến trước khi item "C" được đồng bộ (thứ tự ghi khác nhau giữa 2 bảng).
        val current = CatalogPayload(revision = 5)
        val deltaWithTagOnly = CatalogPayload(
            revision = 6,
            tags = listOf(CatalogTagDto("EPC-C", "C", "active", null, "MAIN", null, 6)),
        )

        val afterFirstDelta = CatalogDeltaMerger.apply(current, deltaWithTagOnly)

        // Tag vẫn được giữ lại dù chưa có item khớp — không bị rớt vĩnh viễn.
        assertEquals(listOf("EPC-C"), afterFirstDelta.tags.map { it.epc })
        assertEquals(emptyList<String>(), afterFirstDelta.items.map { it.itemCode })

        val deltaWithItem = CatalogPayload(
            revision = 7,
            items = listOf(CatalogItemDto("C", "Item C", "pcs", "SERIALIZED", true, 7)),
        )
        val afterSecondDelta = CatalogDeltaMerger.apply(afterFirstDelta, deltaWithItem)

        assertEquals(listOf("C"), afterSecondDelta.items.map { it.itemCode })
        assertEquals(listOf("EPC-C"), afterSecondDelta.tags.map { it.epc })
    }

    @Test
    fun applyDelta_removesTagsWhenItemExplicitlyMarkedInactive() {
        val current = CatalogPayload(
            revision = 5,
            items = listOf(CatalogItemDto("D", "Item D", "pcs", "SERIALIZED", true, 5)),
            tags = listOf(CatalogTagDto("EPC-D", "D", "active", null, "MAIN", null, 5)),
        )
        val delta = CatalogPayload(
            revision = 6,
            items = listOf(CatalogItemDto("D", "Item D", "pcs", "SERIALIZED", false, 6)),
        )

        val merged = CatalogDeltaMerger.apply(current, delta)

        assertEquals(emptyList<String>(), merged.items.map { it.itemCode })
        assertEquals(emptyList<String>(), merged.tags.map { it.epc })
    }
}
