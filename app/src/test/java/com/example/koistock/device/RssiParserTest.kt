package com.example.koistock.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RssiParserTest {
    @Test
    fun parsesSignedDecimal() {
        assertEquals(-62, RssiParser.parse("-62"))
    }

    @Test
    fun parsesPositiveMagnitude() {
        assertEquals(-58, RssiParser.parse("58"))
    }

    @Test
    fun parsesDecoratedDecimal() {
        assertEquals(-63, RssiParser.parse("RSSI: -62.6 dBm"))
    }

    @Test
    fun parsesHexByte() {
        assertEquals(-59, RssiParser.parse("C5"))
    }

    @Test
    fun invalidInputUsesFallback() {
        assertEquals(-70, RssiParser.parse("unknown"))
    }

    @Test
    fun changingDecoratedInputDoesNotCollapseToFallback() {
        assertNotEquals(
            RssiParser.parse("RSSI: -71 dBm"),
            RssiParser.parse("RSSI: -45 dBm"),
        )
    }
}
