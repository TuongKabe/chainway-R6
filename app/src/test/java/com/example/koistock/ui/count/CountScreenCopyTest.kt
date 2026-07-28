package com.example.koistock.ui.count

import org.junit.Assert.assertTrue
import org.junit.Test

class CountScreenCopyTest {
    @Test
    fun locationHelpExplainsBlankMeansWholeWarehouse() {
        assertTrue(COUNT_LOCATION_HELP.contains("Để trống"))
        assertTrue(COUNT_LOCATION_HELP.contains("Toàn khu"))
    }
}
