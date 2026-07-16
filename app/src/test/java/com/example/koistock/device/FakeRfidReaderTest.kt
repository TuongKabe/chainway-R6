package com.example.koistock.device

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRfidReaderTest {
    @Test
    fun connect_success_movesToConnected() = runTest {
        val reader = FakeRfidReader()

        assertTrue(reader.connect("AA:BB:CC:DD:EE:FF"))
        assertEquals(
            ConnectionState.Connected("AA:BB:CC:DD:EE:FF"),
            reader.connectionState.value,
        )
    }

    @Test
    fun connect_failure_staysDisconnected() = runTest {
        val reader = FakeRfidReader().apply { setConnectResult(false) }

        assertFalse(reader.connect("AA:BB:CC:DD:EE:FF"))
        assertEquals(ConnectionState.Disconnected, reader.connectionState.value)
    }

    @Test
    fun emitTag_isReceivedOnInventoryFlow() = runTest {
        val reader = FakeRfidReader()

        reader.inventory.test {
            reader.emitTag("E200001", -40)
            assertEquals(ScannedTag("E200001", -40), awaitItem())
        }
    }
}
