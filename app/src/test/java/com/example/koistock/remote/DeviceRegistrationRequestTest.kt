package com.example.koistock.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegistrationRequestTest {
    @Test
    fun `builds server POST request without exposing token in URL`() {
        val request = DeviceRegistrationRequest.create("token-123")

        assertEquals("POST", request.method)
        assertEquals(
            "https://kitleather.com/api/devices/register",
            request.url,
        )
        assertEquals("token-123", request.fcmToken)
        assertEquals("koistock-handheld-01", request.deviceId)
    }
}
