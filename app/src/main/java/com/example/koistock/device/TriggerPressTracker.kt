package com.example.koistock.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TriggerPressTracker(
    private val scope: CoroutineScope,
    private val idleTimeoutMs: Long = 120L,
    private val onStateChanged: (Boolean) -> Unit,
) {
    private var releaseJob: Job? = null
    private var pressed = false

    fun onKeyDown(keyCode: Int) {
        releaseJob?.cancel()
        if (!pressed) {
            pressed = true
            onStateChanged(true)
        }
        releaseJob = scope.launch {
            delay(idleTimeoutMs)
            emitReleaseIfNeeded()
        }
    }

    fun onKeyUp(keyCode: Int) {
        releaseJob?.cancel()
        emitReleaseIfNeeded()
    }

    fun release() {
        releaseJob?.cancel()
        emitReleaseIfNeeded()
    }

    private fun emitReleaseIfNeeded() {
        if (pressed) {
            pressed = false
            onStateChanged(false)
        }
    }
}
