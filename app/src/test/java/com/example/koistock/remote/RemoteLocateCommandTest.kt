package com.example.koistock.remote

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RemoteLocateCommandTest {
    @Test
    fun `isExpired returns true when now is past expiry`() {
        val cmd = RemoteLocateCommand("id1", "SKU-001", 1000L)
        assertTrue(cmd.isExpired(2000L))
    }

    @Test
    fun `isExpired returns false when now is before expiry`() {
        val cmd = RemoteLocateCommand("id1", "SKU-001", 2000L)
        assertFalse(cmd.isExpired(1000L))
    }

    @Test
    fun `isExpired returns false when now equals expiry`() {
        val cmd = RemoteLocateCommand("id1", "SKU-001", 1000L)
        assertFalse(cmd.isExpired(1000L))
    }
}
