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
    val deviceId: String,
) {
    companion object {
        private const val BASE_URL = "https://kitleather.com"
        private const val DEVICE_ID = "koistock-handheld-01"
        internal const val REGISTRATION_SECRET = "0ee742affd205e5328b847c9f00f4a4e"

        fun create(fcmToken: String) = DeviceRegistrationRequest(
            method = "POST",
            url = "$BASE_URL/api/devices/register",
            fcmToken = fcmToken,
            deviceId = DEVICE_ID,
        )
    }
}

class DeviceRegistrar(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun register(fcmToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val registration = DeviceRegistrationRequest.create(fcmToken)
            val payload = JSONObject()
                .put("deviceId", registration.deviceId)
                .put("fcmToken", registration.fcmToken)
                .toString()
            val body = payload.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(registration.url)
                .header("Authorization", "Bearer ${DeviceRegistrationRequest.Companion.REGISTRATION_SECRET}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Device registration failed with HTTP ${response.code}" }
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
