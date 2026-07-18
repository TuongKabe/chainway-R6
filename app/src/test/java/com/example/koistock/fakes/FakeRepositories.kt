package com.example.koistock.fakes

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.Transaction
import com.example.koistock.data.remote.LocationRepo
import com.example.koistock.data.remote.ActiveCellWriteQueueResult
import com.example.koistock.data.remote.ActiveCellWriteRequest
import com.example.koistock.data.remote.AssignSessionActionResult
import com.example.koistock.data.remote.AssignSessionRepo
import com.example.koistock.data.remote.AssignSessionSnapshot
import com.example.koistock.data.remote.GsheetWriteRepo
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.data.remote.TransactionRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProductRepo(
    val items: MutableMap<String, Product> = mutableMapOf(),
) : ProductRepo {
    override suspend fun getBySku(sku: String): Product? = items[sku]

    override fun observeAll(): Flow<List<Product>> = MutableStateFlow(items.values.toList())

    override suspend fun upsert(product: Product) {
        items[product.sku] = product
    }
}

class FakeTagRepo(
    val items: MutableMap<String, TagMapping> = mutableMapOf(),
) : TagRepo {
    override suspend fun getByEpc(epc: String): TagMapping? = items[epc]

    override suspend fun upsert(tag: TagMapping) {
        items[tag.epc] = tag
    }

    override suspend fun listBySku(sku: String): List<TagMapping> {
        return items.values.filter { it.sku == sku }
    }

    override suspend fun voidTag(epc: String) {
        items[epc]?.let { items[epc] = it.copy(status = "void", locationCode = null) }
    }
}

class FakeGsheetWriteRepo : GsheetWriteRepo {
    override suspend fun queueActiveCellWrite(request: ActiveCellWriteRequest): ActiveCellWriteQueueResult {
        return ActiveCellWriteQueueResult.Success(request.requestId, "queued", null, null)
    }
}

class FakeAssignSessionRepo : AssignSessionRepo {
    override suspend fun getLatestWaiting(): AssignSessionSnapshot? = null

    override suspend fun submitScan(
        sessionId: String,
        epc: String,
        serialNo: String?,
    ): AssignSessionActionResult = AssignSessionActionResult.Error("No fake session")

    override suspend fun confirm(sessionId: String): AssignSessionActionResult {
        return AssignSessionActionResult.Error("No fake session")
    }
}

class FakeLocationRepo(
    val items: MutableMap<String, LocationNode> = mutableMapOf(),
) : LocationRepo {
    override fun observeAll(): Flow<List<LocationNode>> = MutableStateFlow(items.values.toList())

    override suspend fun upsert(location: LocationNode) {
        items[location.code] = location
    }
}

class FakeTransactionRepo(
    val appended: MutableList<Transaction> = mutableListOf(),
) : TransactionRepo {
    override suspend fun append(transaction: Transaction) {
        appended.add(transaction)
    }
}
