package com.example.koistock.ui.zones

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.remote.LocationRepo
import com.example.koistock.data.remote.WarehouseSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

sealed interface ZoneAddResult {
    data object Ok : ZoneAddResult
    data class SavedButSyncFailed(val msg: String) : ZoneAddResult
    data class Error(val msg: String) : ZoneAddResult
}

class ZoneViewModel(
    private val locationRepo: LocationRepo,
    private val now: () -> Long,
    scope: CoroutineScope,
    private val syncAfterSave: suspend () -> WarehouseSyncResult = { WarehouseSyncResult.Success },
) {
    val locations: StateFlow<List<LocationNode>> =
        locationRepo.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun addZone(code: String, name: String): ZoneAddResult {
        val existing = locationRepo.observeAll().first()
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
        if (name.isBlank()) return ZoneAddResult.Error("Tên khu không được để trống")
        if (existing.any { it.code == code }) return ZoneAddResult.Error("Mã đã tồn tại")
        locationRepo.upsert(LocationNode(code, name, LocationType.ZONE, updatedAt = now(), origin = "app"))
        return syncResult()
    }

    suspend fun addShelf(zoneCode: String, code: String, name: String): ZoneAddResult {
        val existing = locationRepo.observeAll().first()
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
        if (name.isBlank()) return ZoneAddResult.Error("Tên kệ không được để trống")
        if (existing.none { it.code == zoneCode && it.type == LocationType.ZONE }) {
            return ZoneAddResult.Error("Chưa có Khu cha")
        }
        if (existing.any { it.code == code }) return ZoneAddResult.Error("Mã đã tồn tại")
        locationRepo.upsert(
            LocationNode(
                code = code,
                name = name,
                type = LocationType.SHELF,
                parent = zoneCode,
                updatedAt = now(),
                origin = "app",
            ),
        )
        return syncResult()
    }

    suspend fun updateLocation(code: String, name: String, parent: String?): ZoneAddResult {
        val existing = locationRepo.observeAll().first()
        val current = existing.firstOrNull { it.code == code }
            ?: return ZoneAddResult.Error("Khu/kệ không còn tồn tại")
        if (name.isBlank()) return ZoneAddResult.Error("Tên không được để trống")
        if (current.type == LocationType.SHELF && existing.none {
                it.code == parent && it.type == LocationType.ZONE
            }
        ) {
            return ZoneAddResult.Error("Khu cha không hợp lệ")
        }
        locationRepo.upsert(
            current.copy(
                name = name.trim(),
                parent = if (current.type == LocationType.SHELF) parent else null,
                updatedAt = now(),
                origin = "app",
            ),
        )
        return syncResult()
    }

    private suspend fun syncResult(): ZoneAddResult = when (val sync = syncAfterSave()) {
        WarehouseSyncResult.Success -> ZoneAddResult.Ok
        is WarehouseSyncResult.SavedButSyncFailed -> ZoneAddResult.SavedButSyncFailed(sync.message)
        is WarehouseSyncResult.LoadFailed -> ZoneAddResult.SavedButSyncFailed(sync.message)
    }
}
