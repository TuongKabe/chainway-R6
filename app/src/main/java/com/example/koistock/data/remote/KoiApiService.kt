package com.example.koistock.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ApiEnvelope<T>(
    val data: T,
)

data class ItemDto(
    val itemCode: String,
    val itemName: String,
    val stockUom: String,
    val trackingMode: String,
    val defaultWarehouse: String? = null,
    val itemGroup: String? = null,
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val syncRev: String? = null,
)

data class EpcTagDto(
    val epc: String,
    val itemCode: String,
    val serialNo: String? = null,
    val status: String = "active",
    val warehouse: String? = null,
    val syncRev: String? = null,
)

data class WarehouseDto(
    val name: String,
    val warehouseName: String,
    val parentWarehouse: String? = null,
    val warehouseType: String,
    val isActive: Boolean = true,
    val syncRev: String? = null,
)

data class BinDto(
    val itemCode: String,
    val warehouse: String,
    val actualQty: String,
    val syncRev: String? = null,
)

data class CommitMovementDto(
    val itemCode: String,
    val epc: String? = null,
    val qtyChange: Double,
    val warehouse: String? = null,
)

data class CommitRequestDto(
    val commandId: String,
    val type: String,
    val deviceId: String,
    val movements: List<CommitMovementDto>,
)

data class CommitResponseDto(
    val commandId: String,
    val status: String,
    val movementsApplied: Int? = null,
    val note: String? = null,
)

data class SyncResultDto(
    val runId: String? = null,
    val status: String? = null,
    val message: String? = null,
    // Dạng snake_case dự phòng nếu backend trả khác quy ước.
    val run_id: String? = null,
) {
    val resolvedRunId: String? get() = runId ?: run_id
}

interface KoiApiService {
    @GET("api/items")
    suspend fun getItems(): ApiEnvelope<List<ItemDto>>

    @GET("api/items/{code}")
    suspend fun getItem(@Path("code") code: String): ApiEnvelope<ItemDto>

    @POST("api/items")
    suspend fun upsertItem(@Body body: ItemDto): ApiEnvelope<ItemDto>

    @GET("api/epc-tags")
    suspend fun getTag(@Query("epc") epc: String): ApiEnvelope<EpcTagDto>

    @GET("api/epc-tags")
    suspend fun getTagsByItem(@Query("itemCode") itemCode: String): ApiEnvelope<List<EpcTagDto>>

    @POST("api/epc-tags")
    suspend fun upsertTag(@Body body: EpcTagDto): ApiEnvelope<EpcTagDto>

    @GET("api/warehouses")
    suspend fun getWarehouses(): ApiEnvelope<List<WarehouseDto>>

    @POST("api/warehouses")
    suspend fun upsertWarehouse(@Body body: WarehouseDto): ApiEnvelope<WarehouseDto>

    @GET("api/bin")
    suspend fun getBins(): ApiEnvelope<List<BinDto>>

    @POST("api/stock-commands/commit")
    suspend fun commitStock(@Body body: CommitRequestDto): ApiEnvelope<CommitResponseDto>

    /** Trigger đồng bộ 2 chiều PostgreSQL ↔ Google Sheet (chạy 2 DAG qua proxy/Airflow). */
    @POST("api/sync/reconcile")
    suspend fun syncReconcile(): ApiEnvelope<SyncResultDto>
}

object KoiApiFactory {
    fun create(baseUrl: String = KoiApiConfig.BASE_URL): KoiApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .readTimeout(java.time.Duration.ofSeconds(120))
            .writeTimeout(java.time.Duration.ofSeconds(30))
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KoiApiService::class.java)
    }
}
