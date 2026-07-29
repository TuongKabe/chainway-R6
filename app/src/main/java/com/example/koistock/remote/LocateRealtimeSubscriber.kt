package com.example.koistock.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Polls for new locate commands for the configured device.
 * This avoids the need for a full Supabase Realtime WebSocket client
 * and works with the existing HTTP-based architecture.
 */
class LocateRealtimeSubscriber(
    private val baseUrl: String,
    private val deviceId: String,
    private val apiKey: String,
    private val coordinator: RemoteLocateCoordinator,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 2000L,
) {
    private var job: Job? = null
    private var lastCheckedId: String? = null

    fun start() {
        stop()
        job = scope.launch {
            while (true) {
                try {
                    checkForCommands()
                } catch (e: Exception) {
                    Log.e("LocateRealtime", "Poll error", e)
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun checkForCommands() {
        val url = URL("$baseUrl/rest/v1/locate_commands?device_id=eq.$deviceId&status=eq.pending&order=created_at.desc&limit=5")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connect()

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONArray(body)
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    val commandId = obj.getString("id")
                    val sku = obj.getString("sku")
                    val expiresAt = obj.getString("expires_at")

                    if (lastCheckedId != null && commandId <= lastCheckedId!!) continue

                    val expiresAtMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(expiresAt.take(19))?.time ?: continue

                    val cmd = RemoteLocateCommand(commandId, sku, expiresAtMs)
                    coordinator.submit(cmd)
                }
                if (json.length() > 0) {
                    lastCheckedId = json.getJSONObject(0).getString("id")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
