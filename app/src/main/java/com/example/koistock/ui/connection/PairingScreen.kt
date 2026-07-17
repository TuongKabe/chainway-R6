package com.example.koistock.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState

@Composable
fun PairingScreen(
    vm: ConnectionViewModel,
    onConnected: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()

    LaunchedEffect(state) {
        if (state is ConnectionState.Connected) {
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Trạng thái: " + when (state) {
                is ConnectionState.Connected -> "Đã kết nối"
                is ConnectionState.Connecting -> "Đang kết nối..."
                ConnectionState.Scanning -> "Đang tìm thiết bị..."
                ConnectionState.Disconnected -> "Chưa kết nối"
            },
        )

        Button(
            onClick = vm::scan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Tìm thiết bị")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text("${device.mac} · RSSI ${device.rssi}") },
                    trailingContent = {
                        Button(onClick = { vm.connect(device.mac) }) {
                            Text("Kết nối")
                        }
                    },
                )
            }
        }
    }
}
