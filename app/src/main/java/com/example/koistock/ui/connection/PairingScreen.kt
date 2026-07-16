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
import com.example.koistock.ui.common.FeatureGuideButton

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Kết nối đầu đọc R6", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn kết nối R6",
                quickSteps = listOf(
                    "Bật Bluetooth trên điện thoại và đầu đọc R6.",
                    "Nhấn Tìm thiết bị để quét các đầu đọc BLE xung quanh.",
                    "Chọn đúng R6 theo tên hoặc địa chỉ MAC.",
                    "Khi trạng thái chuyển sang Đã kết nối, có thể sang các màn nghiệp vụ.",
                ),
                notes = listOf(
                    "Nếu không thấy R6, hãy kiểm tra máy đã cấp quyền Bluetooth.",
                    "Đưa điện thoại lại gần R6 khi ghép nối lần đầu.",
                    "App sẽ lưu MAC gần nhất để tự kết nối lại ở lần sau.",
                ),
            )
        }

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
