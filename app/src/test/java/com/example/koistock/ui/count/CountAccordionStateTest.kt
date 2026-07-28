package com.example.koistock.ui.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountAccordionStateTest {
    @Test
    fun tappingClosedSku_opensIt() {
        assertEquals("S1", toggleExpandedSku(null, "S1"))
    }

    @Test
    fun tappingOpenSku_closesIt() {
        assertNull(toggleExpandedSku("S1", "S1"))
    }

    @Test
    fun tappingAnotherSku_replacesOpenSku() {
        assertEquals("S2", toggleExpandedSku("S1", "S2"))
    }
}
