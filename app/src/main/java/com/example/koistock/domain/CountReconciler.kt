package com.example.koistock.domain

enum class CountStatus { MATCH, EXTRA, MISSING, MISPLACED }

data class ExpectedItem(
    val sku: String,
    val name: String,
    val expectedQty: Int,
    val homeLocation: String,
)

data class CountRow(
    val sku: String,
    val name: String,
    val counted: Int,
    val expected: Int,
    val locationCode: String,
    val status: CountStatus,
)

object CountReconciler {
    fun reconcile(
        zone: String,
        countedBySku: Map<String, Int>,
        expected: List<ExpectedItem>,
    ): List<CountRow> {
        val expectedBySku = expected.associateBy { it.sku }
        val rows = mutableListOf<CountRow>()

        expected.filter { it.homeLocation == zone }.forEach { item ->
            val counted = countedBySku[item.sku] ?: 0
            val status = when {
                counted == item.expectedQty -> CountStatus.MATCH
                counted == 0 || counted < item.expectedQty -> CountStatus.MISSING
                else -> CountStatus.EXTRA
            }
            rows += CountRow(item.sku, item.name, counted, item.expectedQty, zone, status)
        }

        countedBySku.forEach { (sku, counted) ->
            val expectedItem = expectedBySku[sku]
            if (expectedItem?.homeLocation == zone) return@forEach
            val status = if (expectedItem == null) CountStatus.EXTRA else CountStatus.MISPLACED
            rows += CountRow(
                sku = sku,
                name = expectedItem?.name ?: sku,
                counted = counted,
                expected = expectedItem?.expectedQty ?: 0,
                locationCode = zone,
                status = status,
            )
        }

        return rows.sortedBy { it.sku }
    }
}
