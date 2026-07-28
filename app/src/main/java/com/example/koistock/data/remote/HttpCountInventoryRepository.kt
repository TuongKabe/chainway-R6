package com.example.koistock.data.remote

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.domain.CountBinStock
import com.example.koistock.domain.CountInventoryCalculator
import com.example.koistock.domain.CountInventoryItem
import com.example.koistock.domain.CountInventoryRepository
import com.example.koistock.domain.CountInventorySnapshot
import com.example.koistock.domain.CountTagStock

class HttpCountInventoryRepository(
    private val api: KoiApiService,
) : CountInventoryRepository {
    override suspend fun load(
        locationCode: String,
        locations: List<LocationNode>,
    ): CountInventorySnapshot {
        val itemDtos = api.getItems().data.filter(ItemDto::isActive)
        val items = itemDtos.map { item ->
            CountInventoryItem(
                sku = item.itemCode,
                name = item.itemName,
                unit = item.stockUom,
                trackingMode = TrackingMode.valueOf(item.trackingMode.uppercase()),
            )
        }
        val bins = api.getBins().data.map { bin ->
            CountBinStock(
                sku = bin.itemCode,
                locationCode = bin.warehouse,
                actualQty = bin.actualQty,
            )
        }
        val tags = itemDtos
            .filter { it.trackingMode.equals(TrackingMode.SERIALIZED.name, ignoreCase = true) }
            .flatMap { item -> api.getTagsByItem(item.itemCode).data }
            .map { tag ->
                CountTagStock(
                    sku = tag.itemCode,
                    locationCode = tag.locationCode ?: tag.warehouse,
                    active = tag.status.equals("active", ignoreCase = true),
                )
            }

        return CountInventoryCalculator.calculate(locationCode, locations, items, bins, tags)
    }
}
