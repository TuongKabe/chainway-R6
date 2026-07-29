package com.example.koistock.remote

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteLocateCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = HandledCommandStore(context)

    @Test
    fun `valid command is accepted and emitted`() = runTest {
        val coordinator = RemoteLocateCoordinator(store, backgroundScope, { 500L })
        val cmd = RemoteLocateCommand("id1", "SKU-001", 1000L)

        val result = coordinator.submit(cmd)
        assertTrue(result is RemoteLocateCoordinator.SubmissionResult.Accepted)

        val emitted = coordinator.commands.first()
        assertEquals("SKU-001", emitted.sku)
    }

    @Test
    fun `duplicate command is rejected`() = runTest {
        val coordinator = RemoteLocateCoordinator(store, backgroundScope, { 500L })
        val cmd = RemoteLocateCommand("dup-id", "SKU-001", 1000L)

        coordinator.submit(cmd)
        val result = coordinator.submit(cmd)
        assertTrue(result is RemoteLocateCoordinator.SubmissionResult.Duplicate)
    }

    @Test
    fun `expired command is rejected`() = runTest {
        val coordinator = RemoteLocateCoordinator(store, backgroundScope, { 2000L })
        val cmd = RemoteLocateCommand("id2", "SKU-002", 1000L)

        val result = coordinator.submit(cmd)
        assertTrue(result is RemoteLocateCoordinator.SubmissionResult.Rejected)
    }
}
