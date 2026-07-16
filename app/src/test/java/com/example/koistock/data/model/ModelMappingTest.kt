package com.example.koistock.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ModelMappingTest {
    @Test
    fun product_roundTripsTimestamp() {
        val product = Product(
            sku = "SKU1",
            name = "Ca KOI",
            unit = "con",
            trackingMode = TrackingMode.BULK,
            quantity = 5,
            locationCode = "A-03",
            imageUrl = null,
            updatedAt = 1_234L,
            origin = "app",
            syncRev = 7L,
        )

        val mapped = product.toMap()
        assertTrue(mapped["updatedAt"] != null)

        val restored = Product.fromMap(
            id = "SKU1",
            map = mapped + ("updatedAt" to Timestamp(2, 0)),
        )

        assertEquals(2_000L, restored.updatedAt)
        assertEquals(product.copy(updatedAt = 2_000L), restored)
    }

    @Test
    fun tagMapping_readsLegacyEpochMillis() {
        val restored = TagMapping.fromMap(
            id = "E1",
            map = mapOf(
                "sku" to "SKU1",
                "status" to "active",
                "updatedAt" to 99L,
                "origin" to "sheet",
                "syncRev" to 3L,
            ),
        )

        assertEquals(99L, restored.updatedAt)
        assertNull(restored.unitSerial)
    }
}
