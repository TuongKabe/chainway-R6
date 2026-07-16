package com.example.koistock.device

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerPressTrackerTest {
    @Test
    fun defaultSingleKeyDown_autoReleasesQuicklyForSingleClickDevice() = runTest {
        val events = mutableListOf<Boolean>()
        val tracker = TriggerPressTracker(
            scope = this,
            onStateChanged = { events += it },
        )

        tracker.onKeyDown(1)
        runCurrent()
        advanceTimeBy(119)
        runCurrent()

        assertEquals(listOf(true), events)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    @Test
    fun singleKeyDown_autoReleasesAfterIdleTimeout() = runTest {
        val events = mutableListOf<Boolean>()
        val tracker = buildTracker(this, events, idleTimeoutMs = 120L)

        tracker.onKeyDown(1)
        runCurrent()
        advanceTimeBy(119)
        runCurrent()

        assertEquals(listOf(true), events)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    @Test
    fun repeatedKeyDown_extendsPressedUntilInputStops() = runTest {
        val events = mutableListOf<Boolean>()
        val tracker = buildTracker(this, events, idleTimeoutMs = 120L)

        tracker.onKeyDown(1)
        advanceTimeBy(100)
        tracker.onKeyDown(1)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf(true), events)

        advanceTimeBy(20)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    @Test
    fun keyUp_releasesImmediately() = runTest {
        val events = mutableListOf<Boolean>()
        val tracker = buildTracker(this, events, idleTimeoutMs = 120L)

        tracker.onKeyDown(1)
        runCurrent()
        tracker.onKeyUp(1)
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    @Test
    fun singleKeyDown_staysPressedLongEnoughForHoldThreshold() = runTest {
        val events = mutableListOf<Boolean>()
        val tracker = buildTracker(this, events, idleTimeoutMs = 900L)

        tracker.onKeyDown(1)
        runCurrent()
        advanceTimeBy(350)
        runCurrent()

        assertEquals(listOf(true), events)

        advanceTimeBy(550)
        runCurrent()
        assertEquals(listOf(true, false), events)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true, false), events)
    }

    private fun buildTracker(
        scope: TestScope,
        events: MutableList<Boolean>,
        idleTimeoutMs: Long,
    ): TriggerPressTracker = TriggerPressTracker(
        scope = scope,
        idleTimeoutMs = idleTimeoutMs,
        onStateChanged = { events += it },
    )
}
