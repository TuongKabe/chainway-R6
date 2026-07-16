package com.example.koistock.device

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val mac: String) : ConnectionState
    data class Connected(val mac: String) : ConnectionState
}
