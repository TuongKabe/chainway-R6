package com.example.koistock.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegistrationRequestTest {
    @Test
    fun `builds fixed device PATCH request without exposing token in URL`() {
        val request = DeviceRegistrationRequest.create("token-123")

        assertEquals("PATCH", request.method)
        assertEquals(
            "https://fwetygumscetrwckoxpb.supabase.co/rest/v1/devices?id=eq.koistock-handheld-01",
            request.url,
        )
        assertEquals("token-123", request.fcmToken)
        assertEquals(true, request.enabled)
    }
}
