package com.example.koistock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpcCodecTest {
    @Test
    fun encode_thenSkuOf_roundTrips() {
        val epc = EpcCodec.encode("SKU1", "0001")
        assertEquals("KOI-SKU1-0001", epc)
        assertEquals("SKU1", EpcCodec.skuOf(epc))
    }

    @Test
    fun mask_isPrefixForSku() {
        assertEquals("KOI-SKU1-", EpcCodec.maskForSku("SKU1"))
        assertTrue(EpcCodec.encode("SKU1", "9").startsWith(EpcCodec.maskForSku("SKU1")))
    }

    @Test
    fun rawEpc_isNotStructured() {
        assertFalse(EpcCodec.isStructured("E20000123456"))
        assertNull(EpcCodec.skuOf("E20000123456"))
    }
}
