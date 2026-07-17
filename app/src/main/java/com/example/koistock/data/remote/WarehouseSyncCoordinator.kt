package com.example.koistock.data.remote

sealed interface WarehouseSyncResult {
    data object Success : WarehouseSyncResult
    data class SavedButSyncFailed(val message: String) : WarehouseSyncResult
    data class LoadFailed(val message: String) : WarehouseSyncResult
}

class WarehouseSyncCoordinator(
    private val reconcile: suspend () -> SyncOutcome,
    private val refreshProducts: suspend () -> Unit,
    private val refreshLocations: suspend () -> Unit,
) {
    suspend fun syncAndRefresh(): WarehouseSyncResult {
        val sync = reconcile()
        val refreshFailure = runCatching {
            refreshProducts()
            refreshLocations()
        }.exceptionOrNull()

        if (refreshFailure != null) {
            return WarehouseSyncResult.LoadFailed(
                refreshFailure.message ?: "Không tải lại được dữ liệu kho",
            )
        }

        return when (sync) {
            is SyncOutcome.Success -> WarehouseSyncResult.Success
            is SyncOutcome.Failure -> WarehouseSyncResult.SavedButSyncFailed(sync.message)
        }
    }
}
