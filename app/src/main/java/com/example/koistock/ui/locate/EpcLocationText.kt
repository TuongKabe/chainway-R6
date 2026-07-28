package com.example.koistock.ui.locate

import com.example.koistock.data.model.TagMapping

internal fun warehouseText(tag: TagMapping): String =
    tag.warehouse?.trim()?.takeIf(String::isNotEmpty) ?: "Chưa gán"

internal fun positionText(tag: TagMapping): String =
    tag.locationCode?.trim()?.takeIf(String::isNotEmpty)
        ?: tag.bin?.trim()?.takeIf(String::isNotEmpty)
        ?: "Chưa gán"
