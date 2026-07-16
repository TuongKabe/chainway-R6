package com.example.koistock.ui.zones

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.remote.LocationRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

sealed interface ZoneAddResult {
    data object Ok : ZoneAddResult
    data class Error(val msg: String) : ZoneAddResult
}

class ZoneViewModel(
    private val locationRepo: LocationRepo,
    private val now: () -> Long,
    scope: CoroutineScope,
) {
    val locations: StateFlow<List<LocationNode>> =
        locationRepo.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun addZone(code: String, name: String): ZoneAddResult {
        val existing = locationRepo.observeAll().first()
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
        if (existing.any { it.code == code }) return ZoneAddResult.Error("Mã đã tồn tại")
        locationRepo.upsert(LocationNode(code, name, LocationType.ZONE, updatedAt = now(), origin = "app"))
        return ZoneAddResult.Ok
    }

    suspend fun addShelf(zoneCode: String, code: String, name: String): ZoneAddResult {
        val existing = locationRepo.observeAll().first()
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
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
        return ZoneAddResult.Ok
    }
}
