package com.example.koistock.ui.assign

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.remote.AssignSessionActionResult
import com.example.koistock.data.remote.AssignSessionItem
import com.example.koistock.data.remote.AssignSessionRepo
import com.example.koistock.data.remote.AssignSessionSnapshot
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.FakeAssignSessionRepo
import com.example.koistock.fakes.FakeGsheetWriteRepo
import com.example.koistock.fakes.FakeProductRepo
import com.example.koistock.fakes.FakeTagRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignTagViewModelTest {
    @Test
    fun scanBlank_capturesEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val vm = viewModel(reader, FakeTagRepo(), backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        assertEquals("E2000ABC", vm.scannedEpc.value)
    }

    @Test
    fun assign_raw_writesTagMappingWithScannedEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = viewModel(reader, tags, backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = false, pushBarcodeToSheet = false)
        advanceUntilIdle()
        val saved = tags.items["E2000ABC"]!!
        assertEquals("SKU1", saved.sku)
        assertEquals(100, saved.updatedAt)
        assertTrue(vm.done.value)
    }

    @Test
    fun assign_structured_writesEncodedEpcToTagAndMapping() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = viewModel(reader, tags, backgroundScope)
        vm.scanBlank()
        advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = true, pushBarcodeToSheet = false)
        advanceUntilIdle()
        assertEquals("E2000ABC", reader.lastWrittenEpc?.first)
        assertTrue(reader.lastWrittenEpc?.second?.startsWith("KOI-SKU1-") == true)
        assertTrue(tags.items.keys.any { it.startsWith("KOI-SKU1-") })
    }

    @Test
    fun trigger_click_scansBlankTag() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000TRIGGER", -30) }
        val vm = viewModel(reader, FakeTagRepo(), backgroundScope)
        runCurrent()

        reader.emitTrigger(true)
        runCurrent()
        advanceUntilIdle()

        assertEquals("E2000TRIGGER", vm.scannedEpc.value)
        assertEquals(1, reader.burstScanCount)
        assertEquals(0, reader.singleScanCount)
    }

    @Test
    fun scanWithWaitingSession_autoSubmitsAndConfirms() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(1, sessions.submitCalls)
        assertEquals("confirmed", vm.assignSession.value?.status)
    }

    @Test
    fun epcOwnedByAnotherSku_blocksWebCallsAndPublishesConflict() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-X", -30) }
        val tags = FakeTagRepo(mutableMapOf("EPC-X" to TagMapping("EPC-X", "SKU2", status = "active")))
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, tags, backgroundScope, sessions, sampleProductsWithSku2())
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(0, sessions.submitCalls)
        assertEquals("SKU2", vm.conflict.value?.ownerSku)
        assertEquals("EPC-X", vm.conflict.value?.epc)
    }

    @Test
    fun skuWithAnotherActiveEpc_blocksWebCalls() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-NEW", -30) }
        val tags = FakeTagRepo(mutableMapOf("EPC-OLD" to TagMapping("EPC-OLD", "SKU1", status = "active")))
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, tags, backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(0, sessions.submitCalls)
        assertEquals("EPC-OLD", vm.conflict.value?.epc)
    }

    @Test
    fun epcAlreadyOwnedBySessionSku_isAllowed() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
        val tags = FakeTagRepo(mutableMapOf("EPC-1" to TagMapping("EPC-1", "SKU1", status = "active")))
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, tags, backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(1, sessions.submitCalls)
        assertEquals(1, sessions.confirmCalls)
    }

    @Test
    fun inactiveMapping_doesNotBlockWebCompletion() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-NEW", -30) }
        val tags = FakeTagRepo(mutableMapOf("EPC-OLD" to TagMapping("EPC-OLD", "SKU1", status = "void")))
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, tags, backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(1, sessions.submitCalls)
    }

    @Test
    fun verificationFailure_failsClosed() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
        val sessions = RecordingSessionRepo()
        val vm = viewModel(reader, ThrowingTagRepo(), backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(0, sessions.submitCalls)
        assertTrue(vm.result.value is AssignResult.Error)
    }

    @Test
    fun scanWithoutWaitingSession_doesNotCallWeb() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
        val sessions = RecordingSessionRepo().apply { latest = null }
        val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)

        vm.scanBlank()
        advanceUntilIdle()

        assertEquals(0, sessions.submitCalls)
    }

    @Test
    fun repeatedScanWhileSubmitting_callsWebOnce() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
        val sessions = RecordingSessionRepo().apply { submitGate = CompletableDeferred() }
        val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)
        vm.refreshLatestAssignSession()
        runCurrent()

        vm.scanBlank()
        runCurrent()
        vm.scanBlank()
        runCurrent()
        sessions.submitGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, sessions.submitCalls)
    }

    private class RecordingSessionRepo : AssignSessionRepo {
        val waiting = AssignSessionSnapshot(
            id = "session-1",
            itemCode = "SKU1",
            status = "waiting_scan",
            expiresAt = "2099-01-01T00:00:00Z",
            item = AssignSessionItem("SKU1", "Ca KOI"),
        )
        var submitCalls = 0
        var confirmCalls = 0
        var submitGate: CompletableDeferred<Unit>? = null
        var latest: AssignSessionSnapshot? = waiting

        override suspend fun getLatestWaiting() = latest

        override suspend fun submitScan(
            sessionId: String,
            epc: String,
            serialNo: String?,
        ): AssignSessionActionResult {
            submitCalls++
            submitGate?.await()
            return AssignSessionActionResult.Success(waiting.copy(status = "scanned", scannedEpc = epc))
        }

        override suspend fun confirm(sessionId: String): AssignSessionActionResult {
            confirmCalls++
            return AssignSessionActionResult.Success(waiting.copy(status = "confirmed"))
        }
    }

    private class ThrowingTagRepo : TagRepo {
        override suspend fun getByEpc(epc: String): TagMapping? = error("verification offline")
        override suspend fun listBySku(sku: String): List<TagMapping> = error("verification offline")
        override suspend fun listActive(): List<TagMapping> = error("verification offline")
        override suspend fun upsert(tag: TagMapping) = Unit
        override suspend fun voidTag(epc: String) = Unit
    }

    private fun sampleProducts() = FakeProductRepo(
        mutableMapOf(
            "SKU1" to Product("SKU1", "Ca KOI", "con", TrackingMode.SERIALIZED, 1, "A-03"),
        ),
    )

    private fun sampleProductsWithSku2() = FakeProductRepo(
        mutableMapOf(
            "SKU1" to Product("SKU1", "Ca KOI", "con", TrackingMode.SERIALIZED, 1, "A-03"),
            "SKU2" to Product("SKU2", "Ca Showa", "con", TrackingMode.SERIALIZED, 1, "A-03"),
        ),
    )

    private fun viewModel(
        reader: FakeRfidReader,
        tags: TagRepo,
        scope: CoroutineScope,
        sessions: AssignSessionRepo = FakeAssignSessionRepo(),
        products: ProductRepo = sampleProducts(),
    ) = AssignTagViewModel(
        reader = reader,
        tagRepo = tags,
        productRepo = products,
        gsheetWriteRepo = FakeGsheetWriteRepo(),
        assignSessionRepo = sessions,
        deviceId = "dev-1",
        now = { 100 },
        scope = scope,
    )
}
