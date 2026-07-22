package com.example.koistock.domain

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.TrackingMode
import java.math.BigDecimal

sealed interface CountScope {
    data object EntireWarehouse : CountScope

    data class Location(
        val code: String,
        val includedCodes: Set<String>,
    ) : CountScope
}

data class CountInventoryItem(
    val sku: String,
    val name: String,
    val unit: String,
    val trackingMode: TrackingMode,
)

data class CountBinStock(
    val sku: String,
    val locationCode: String,
    val actualQty: String,
)

data class CountTagStock(
    val sku: String,
    val locationCode: String?,
    val active: Boolean,
)

data class CountInventorySnapshot(
    val scope: CountScope,
    val expected: List<ExpectedItem>,
    val skusWithStockAnywhere: Set<String>,
)

interface CountInventoryRepository {
    suspend fun load(
        locationCode: String,
        locations: List<LocationNode>,
    ): CountInventorySnapshot
}

object CountInventoryCalculator {
    fun calculate(
        locationCode: String,
        locations: List<LocationNode>,
        items: List<CountInventoryItem>,
        bins: List<CountBinStock>,
        tags: List<CountTagStock>,
    ): CountInventorySnapshot {
        val normalizedCode = locationCode.trim()
        val scope = resolveScope(normalizedCode, locations)
        val globalQtyBySku = quantitiesBySku(CountScope.EntireWarehouse, items, bins, tags)
        val scopedQtyBySku = quantitiesBySku(scope, items, bins, tags)
        val itemBySku = items.associateBy(CountInventoryItem::sku)

        val expected = scopedQtyBySku
            .filterValues { it > 0 }
            .mapNotNull { (sku, quantity) ->
                itemBySku[sku]?.let { item ->
                    ExpectedItem(
                        sku = sku,
                        name = item.name,
                        expectedQty = quantity,
                        homeLocation = normalizedCode,
                        unit = item.unit,
                    )
                }
            }
            .sortedBy(ExpectedItem::sku)

        return CountInventorySnapshot(
            scope = scope,
            expected = expected,
            skusWithStockAnywhere = globalQtyBySku.filterValues { it > 0 }.keys,
        )
    }

    private fun resolveScope(code: String, locations: List<LocationNode>): CountScope {
        if (code.isBlank()) return CountScope.EntireWarehouse
        require(locations.any { it.code == code }) { "Không tìm thấy khu/kệ: $code" }

        val included = mutableSetOf(code)
        var changed: Boolean
        do {
            changed = false
            locations.forEach { location ->
                if (location.parent in included && included.add(location.code)) changed = true
            }
        } while (changed)
        return CountScope.Location(code, included)
    }

    private fun quantitiesBySku(
        scope: CountScope,
        items: List<CountInventoryItem>,
        bins: List<CountBinStock>,
        tags: List<CountTagStock>,
    ): Map<String, Int> {
        val includedCodes = (scope as? CountScope.Location)?.includedCodes
        return items.associate { item ->
            val quantity = when (item.trackingMode) {
                TrackingMode.BULK -> bins.asSequence()
                    .filter { it.sku == item.sku }
                    .filter { includedCodes == null || it.locationCode in includedCodes }
                    .fold(BigDecimal.ZERO) { total, bin -> total + parseWholeQuantity(bin) }
                    .intValueExact()

                TrackingMode.SERIALIZED -> tags.count { tag ->
                    tag.sku == item.sku && tag.active &&
                        (includedCodes == null || tag.locationCode in includedCodes)
                }
            }
            item.sku to quantity
        }
    }

    private fun parseWholeQuantity(bin: CountBinStock): BigDecimal {
        val value = bin.actualQty.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Tồn DB không hợp lệ cho ${bin.sku}: ${bin.actualQty}")
        require(value.stripTrailingZeros().scale() <= 0) {
            "Tồn DB phải là số nguyên cho ${bin.sku}: ${bin.actualQty}"
        }
        return value
    }
}
