package com.example.koistock.ui.assign

import org.junit.Assert.assertTrue
import org.junit.Test

class AssignConflictPresentationTest {
    @Test
    fun messageIncludesOwnerSkuAndEpc() {
        val conflict = AssignConflict(
            AssignConflictReason.EPC_OWNED_BY_OTHER_SKU,
            ownerSku = "SKU2",
            ownerName = "Ca Showa",
            epc = "EPC-X",
        )

        val message = conflict.displayMessage()

        assertTrue(message.contains("SKU2"))
        assertTrue(message.contains("EPC-X"))
    }
}
