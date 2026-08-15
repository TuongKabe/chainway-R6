package com.example.koistock.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class CatalogItemDto(
    val itemCode: String,
    val itemName: String,
    val stockUom: String,
    val trackingMode: String,
    val isActive: Boolean,
    val syncRev: Long,
    val defaultWarehouse: String? = null,
    val imageUrl: String? = null,
)

data class CatalogTagDto(
    val epc: String,
    val itemCode: String,
    val status: String,
    val serialNo: String?,
    val warehouse: String?,
    val locationCode: String?,
    val syncRev: Long,
    val bin: String? = null,
)

data class CatalogPayload(
    val revision: Long = 0,
    val items: List<CatalogItemDto> = emptyList(),
    val tags: List<CatalogTagDto> = emptyList(),
)

data class CatalogEnvelope(val data: CatalogPayload)

/** Chuẩn hoá itemCode để so khớp item ↔ tag: bỏ khoảng trắng đầu/cuối, không phân biệt hoa/thường. */
internal fun String.normalizedItemCode(): String = trim().uppercase()

object CatalogDeltaMerger {
    fun apply(current: CatalogPayload, delta: CatalogPayload): CatalogPayload {
        val items = current.items.associateBy { it.itemCode }.toMutableMap()
        // Chỉ đánh dấu "đã xoá" khi delta xác nhận rõ item không còn active; không suy diễn
        // từ việc itemCode chưa có trong `items`, vì tag của item mới có thể đến trước item đó
        // trong cùng đợt đồng bộ — nếu suy diễn sai sẽ làm rớt tag đó vĩnh viễn (revision chỉ tiến).
        val removedItemCodes = mutableSetOf<String>()
        delta.items.forEach {
            if (it.isActive) items[it.itemCode] = it else {
                items.remove(it.itemCode)
                removedItemCodes += it.itemCode.normalizedItemCode()
            }
        }
        val tags = current.tags.associateBy { it.epc }.toMutableMap()
        delta.tags.forEach { if (it.status.equals("active", true)) tags[it.epc] = it else tags.remove(it.epc) }
        tags.values.removeIf { it.itemCode.normalizedItemCode() in removedItemCodes }
        return CatalogPayload(
            revision = maxOf(current.revision, delta.revision),
            items = items.values.sortedBy { it.itemCode },
            tags = tags.values.sortedBy { it.epc },
        )
    }
}

interface SupabaseCatalogApi {
    @GET("snapshot") suspend fun snapshot(): CatalogEnvelope
    @GET("changes") suspend fun changes(@Query("after_rev") afterRev: Long): CatalogEnvelope
}

interface CatalogPayloadCache {
    suspend fun readPayload(): CatalogPayload?
    suspend fun writePayload(payload: CatalogPayload)
}

class DataStoreCatalogPayloadCache(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) : CatalogPayloadCache {
    override suspend fun readPayload(): CatalogPayload? = dataStore.data.first()[KEY]?.let {
        runCatching { gson.fromJson(it, CatalogPayload::class.java) }.getOrNull()
    }

    override suspend fun writePayload(payload: CatalogPayload) {
        dataStore.edit { it[KEY] = gson.toJson(payload) }
    }

    private companion object { val KEY = stringPreferencesKey("supabase_catalog_snapshot_v1") }
}

class SupabaseLocateCatalogRepository(
    private val api: SupabaseCatalogApi,
    private val cache: CatalogPayloadCache,
    /** Backend gốc (rfid.bangtuong.online). Dùng làm fallback tìm SKU khi Supabase catalog
     * chưa có SKU đó (ví dụ item/tag chưa được đồng bộ sang Supabase) — người dùng vẫn tìm
     * được ngay từ nút "Tìm trên máy chủ" thay vì phải đợi đồng bộ backend xử lý xong. */
    private val fallbackApi: KoiApiService? = null,
) : LocateCatalogRepo {
    override suspend fun loadCached(): List<LocatableProduct>? = cache.readPayload()?.toLocatable()

    override suspend fun refresh(): List<LocatableProduct> {
        val current = cache.readPayload()
        val updated = if (current == null) api.snapshot().data else {
            CatalogDeltaMerger.apply(current, api.changes(current.revision).data)
        }
        cache.writePayload(updated)
        return updated.toLocatable()
    }

    override suspend fun findBySku(sku: String): LocatableProduct? {
        val query = sku.trim()
        if (query.isEmpty()) return null
        // Nút này ghi rõ "tìm trên máy chủ": luôn lấy snapshot đầy đủ từ server thay vì chỉ
        // đọc cache cục bộ, để không bao giờ báo "không tìm thấy" chỉ vì cache máy thiếu SKU.
        // Đồng thời tự chữa cache nếu nó từng bị thiếu dữ liệu do lỗi merge trước đó.
        val payload = api.snapshot().data
        cache.writePayload(payload)
        val locatable = payload.toLocatable()
        val found = locatable.firstOrNull { it.product.sku.equals(query, true) }
            ?: locatable.singleOrNull { it.product.sku.contains(query, true) }
        if (found != null) return found
        // SKU chưa có trong Supabase catalog (ví dụ chưa được đồng bộ) — thử tra thẳng backend gốc
        // để người dùng vẫn tìm được ngay, thay vì phải đợi đồng bộ xử lý xong.
        return fallbackApi?.let { runCatching { findBySkuViaKoiApi(it, query) }.getOrNull() }
    }

    private fun CatalogPayload.toLocatable(): List<LocatableProduct> {
        val activeTags = tags.filter { it.status.equals("active", true) }
            .groupBy { it.itemCode.normalizedItemCode() }
        return items.asSequence().filter { it.isActive }.mapNotNull { item ->
            val itemTags = activeTags[item.itemCode.normalizedItemCode()].orEmpty()
            if (itemTags.isEmpty()) return@mapNotNull null
            LocatableProduct(
                product = Product(
                    sku = item.itemCode,
                    name = item.itemName,
                    unit = item.stockUom,
                    trackingMode = TrackingMode.valueOf(item.trackingMode),
                    quantity = itemTags.size.toLong(),
                    locationCode = itemTags.firstNotNullOfOrNull { it.locationCode ?: it.bin ?: it.warehouse }
                        ?: item.defaultWarehouse.orEmpty(),
                    imageUrl = item.imageUrl,
                    syncRev = item.syncRev,
                    origin = "supabase",
                ),
                activeTags = itemTags.map { tag ->
                    TagMapping(
                        epc = tag.epc,
                        sku = tag.itemCode,
                        unitSerial = tag.serialNo,
                        status = tag.status,
                        locationCode = tag.locationCode,
                        origin = "supabase",
                        syncRev = tag.syncRev,
                        warehouse = tag.warehouse,
                        bin = tag.bin,
                    )
                }.sortedBy { it.epc },
            )
        }.sortedBy { it.product.sku }.toList()
    }
}

object SupabaseCatalogFactory {
    private const val BASE_URL = "https://ggxsrbezuuwkxnyxgcxq.supabase.co/functions/v1/catalog/"
    private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdneHNyYmV6dXV3a3hueXhnY3hxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUyMTQ3OTQsImV4cCI6MjEwMDc5MDc5NH0.iXvetCLUo-c738KYYG22v6K37BgeHArX7CB8WvUbvZU"

    fun create(): SupabaseCatalogApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("apikey", API_KEY).header("Authorization", "Bearer $API_KEY").build())
        }.build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(SupabaseCatalogApi::class.java)
    }
}
