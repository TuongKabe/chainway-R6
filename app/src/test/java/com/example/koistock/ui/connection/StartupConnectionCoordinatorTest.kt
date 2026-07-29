package com.example.koistock.ui.connection

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupConnectionCoordinatorTest {
    @Test
    fun unresolvedPermission_waitsAndCanRunLater() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; true }

        assertEquals(StartupConnectionResult.WaitingForPermission, subject.run(null))
        assertEquals(0, calls)
        assertEquals(StartupConnectionResult.Connected, subject.run(true))
        assertEquals(1, calls)
    }

    @Test
    fun deniedPermission_opensPairingWithoutConnecting() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; true }

        assertEquals(StartupConnectionResult.OpenPairing, subject.run(false))
        assertEquals(0, calls)
    }

    @Test
    fun failedReconnect_opensPairing() = runTest {
        val subject = StartupConnectionCoordinator { false }

        assertEquals(StartupConnectionResult.OpenPairing, subject.run(true))
    }

    @Test
    fun successfulReconnect_staysOnDashboard() = runTest {
        val subject = StartupConnectionCoordinator { true }

        assertEquals(StartupConnectionResult.Connected, subject.run(true))
    }

    @Test
    fun repeatedRun_isIgnored() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; false }

        assertEquals(StartupConnectionResult.OpenPairing, subject.run(true))
        assertEquals(StartupConnectionResult.AlreadyHandled, subject.run(true))
        assertEquals(1, calls)
    }
}
