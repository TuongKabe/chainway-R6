package com.example.koistock.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseSyncCoordinatorTest {
    @Test
    fun syncAndRefresh_runsInOrder() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = WarehouseSyncCoordinator(
            reconcile = {
                calls += "sync"
                SyncOutcome.Success("run", null)
            },
            refreshProducts = { calls += "products" },
            refreshLocations = { calls += "locations" },
        )

        val result = coordinator.syncAndRefresh()

        assertEquals(listOf("sync", "products", "locations"), calls)
        assertEquals(WarehouseSyncResult.Success, result)
    }

    @Test
    fun syncFailure_stillRefreshesAndReturnsPartialFailure() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = WarehouseSyncCoordinator(
            reconcile = {
                calls += "sync"
                SyncOutcome.Failure("sheet offline")
            },
            refreshProducts = { calls += "products" },
            refreshLocations = { calls += "locations" },
        )

        val result = coordinator.syncAndRefresh()

        assertEquals(listOf("sync", "products", "locations"), calls)
        assertEquals(WarehouseSyncResult.SavedButSyncFailed("sheet offline"), result)
    }

    @Test
    fun refreshFailure_isReported() = runTest {
        val coordinator = WarehouseSyncCoordinator(
            reconcile = { SyncOutcome.Success(null, null) },
            refreshProducts = { error("products unavailable") },
            refreshLocations = {},
        )

        val result = coordinator.syncAndRefresh()

        assertTrue(result is WarehouseSyncResult.LoadFailed)
        assertEquals("products unavailable", (result as WarehouseSyncResult.LoadFailed).message)
    }
}
