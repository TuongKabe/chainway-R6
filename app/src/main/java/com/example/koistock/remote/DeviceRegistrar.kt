package com.example.koistock.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DeviceRegistrationRequest(
    val method: String,
    val url: String,
    val fcmToken: String,
    val enabled: Boolean,
) {
    companion object {
        fun create(fcmToken: String) = DeviceRegistrationRequest(
            method = "PATCH",
            url = "https://fwetygumscetrwckoxpb.supabase.co/rest/v1/devices?id=eq.koistock-handheld-01",
            fcmToken = fcmToken,
            enabled = true,
        )
    }
}

class DeviceRegistrar(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun register(fcmToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val registration = DeviceRegistrationRequest.create(fcmToken)
            val body = JSONObject()
                .put("fcm_token", registration.fcmToken)
                .put("enabled", registration.enabled)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(registration.url)
                .header("apikey", PUBLISHABLE_KEY)
                .header("Authorization", "Bearer $PUBLISHABLE_KEY")
                .header("Prefer", "return=minimal")
                .patch(body)
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Device registration failed with HTTP ${response.code}" }
            }
        }
    }

    private companion object {
        const val PUBLISHABLE_KEY = "sb_publishable_c5ehYaWf6FMufDuDPaM7wg_s-xH3Uup"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
