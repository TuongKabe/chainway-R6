package com.example.koistock.ui.connection

import kotlinx.coroutines.CancellationException

sealed interface StartupConnectionResult {
    data object WaitingForPermission : StartupConnectionResult
    data object Connected : StartupConnectionResult
    data object OpenPairing : StartupConnectionResult
    data object AlreadyHandled : StartupConnectionResult
}

internal fun canStartPairingScan(permissionGranted: Boolean?): Boolean =
    permissionGranted == true

class StartupConnectionCoordinator(
    private val reconnect: suspend () -> Boolean,
) {
    private var handled = false

    suspend fun run(permissionGranted: Boolean?): StartupConnectionResult {
        if (permissionGranted == null) return StartupConnectionResult.WaitingForPermission
        if (handled) return StartupConnectionResult.AlreadyHandled

        handled = true
        if (!permissionGranted) return StartupConnectionResult.OpenPairing

        val connected = try {
            reconnect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return if (connected) {
            StartupConnectionResult.Connected
        } else {
            StartupConnectionResult.OpenPairing
        }
    }
}
