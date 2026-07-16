package com.example.koistock.domain

import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.Transaction

object StockCalculator {
    fun bulkQuantity(txns: List<Transaction>, sku: String): Long =
        txns.asSequence()
            .filter { it.sku == sku }
            .sumOf { it.delta }

    fun serializedQuantity(
        tags: List<TagMapping>,
        sku: String,
        locationCode: String? = null,
    ): Int = tags.count {
        it.sku == sku &&
            it.status == "active" &&
            (locationCode == null || it.locationCode == locationCode)
    }
}
