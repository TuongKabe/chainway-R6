package com.example.koistock.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteLocatePayloadParserTest {
    @Test
    fun `parses epoch millisecond expiry`() {
        val command = RemoteLocatePayloadParser.parse(
            mapOf("commandId" to "cmd-1", "sku" to "G530", "expiresAt" to "1785312000000"),
        )

        assertEquals(RemoteLocateCommand("cmd-1", "G530", 1785312000000L), command)
    }

    @Test
    fun `parses ISO 8601 expiry`() {
        val command = RemoteLocatePayloadParser.parse(
            mapOf("commandId" to "cmd-2", "sku" to "G431", "expiresAt" to "2026-07-29T08:00:00Z"),
        )

        assertEquals(1785312000000L, command?.expiresAtEpochMs)
    }

    @Test
    fun `rejects incomplete payload`() {
        assertNull(RemoteLocatePayloadParser.parse(mapOf("sku" to "G530")))
    }
}
