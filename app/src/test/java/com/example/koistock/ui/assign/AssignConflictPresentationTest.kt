package com.example.koistock.ui.assign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignConflictPresentationTest {
    @Test
    fun localSuccessSnackbar_includesEpcAndSku() {
        val result = AssignResult.Success("EPC-1", "SKU1", note = "Đã gán tag thành công.")

        val message = result.snackbarMessage()

        assertTrue(message.contains("EPC-1"))
        assertTrue(message.contains("SKU1"))
        assertTrue(message.contains("Đã gán"))
    }

    @Test
    fun webSuccessSnackbar_usesWebWording() {
        val result = AssignResult.Success(
            epc = "EPC-2",
            sku = "SKU2",
            note = "Đã gửi tag lên web và tự hoàn tất gán.",
        )

        val message = result.snackbarMessage()

        assertTrue(message.contains("EPC-2"))
        assertTrue(message.contains("SKU2"))
        assertTrue(message.contains("web", ignoreCase = true))
    }

    @Test
    fun onlyNonSuccessResults_requireDialog() {
        assertFalse(AssignResult.Success("E", "S").requiresDialog())
        assertTrue(AssignResult.PartialSuccess("E", "S", "partial").requiresDialog())
        assertTrue(AssignResult.Error("error").requiresDialog())
    }

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
