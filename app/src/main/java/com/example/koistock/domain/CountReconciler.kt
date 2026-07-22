package com.example.koistock.domain

enum class CountStatus { MATCH, EXTRA, MISSING, MISPLACED }

data class ExpectedItem(
    val sku: String,
    val name: String,
    val expectedQty: Int,
    val homeLocation: String,
    val unit: String = "",
)

data class CountRow(
    val sku: String,
    val name: String,
    val scannedTagCount: Int,
    val dbStockQty: Int,
    val unit: String,
    val locationCode: String,
    val status: CountStatus,
) {
    val counted: Int get() = scannedTagCount
    val expected: Int get() = dbStockQty
}

object CountReconciler {
    @Deprecated("Use the scope-aware overload")
    fun reconcile(
        zone: String,
        countedBySku: Map<String, Int>,
        expected: List<ExpectedItem>,
    ): List<CountRow> {
        val scope = if (zone.isBlank()) {
            CountScope.EntireWarehouse
        } else {
            CountScope.Location(zone, setOf(zone))
        }
        return reconcile(scope, countedBySku, expected, expected.mapTo(mutableSetOf(), ExpectedItem::sku))
    }

    fun reconcile(
        scope: CountScope,
        countedBySku: Map<String, Int>,
        expected: List<ExpectedItem>,
        skusWithStockAnywhere: Set<String>,
    ): List<CountRow> {
        val expectedBySku = expected.associateBy { it.sku }
        val locationCode = (scope as? CountScope.Location)?.code.orEmpty()
        val rows = mutableListOf<CountRow>()

        expected.forEach { item ->
            val scannedTagCount = countedBySku[item.sku] ?: 0
            val status = if (scannedTagCount > 0) CountStatus.MATCH else CountStatus.MISSING
            rows += CountRow(
                sku = item.sku,
                name = item.name,
                scannedTagCount = scannedTagCount,
                dbStockQty = item.expectedQty,
                unit = item.unit,
                locationCode = locationCode,
                status = status,
            )
        }

        countedBySku.forEach { (sku, scannedTagCount) ->
            if (sku in expectedBySku) return@forEach
            val status = if (scope is CountScope.Location && sku in skusWithStockAnywhere) {
                CountStatus.MISPLACED
            } else {
                CountStatus.EXTRA
            }
            rows += CountRow(
                sku = sku,
                name = sku,
                scannedTagCount = scannedTagCount,
                dbStockQty = 0,
                unit = "",
                locationCode = locationCode,
                status = status,
            )
        }

        return rows.sortedBy { it.sku }
    }
}
