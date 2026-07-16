package com.example.koistock.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState
import com.example.koistock.ui.theme.SoftAmber

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    onOpen: (String) -> Unit,
) {
    val isConnected = connectionState is ConnectionState.Connected

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "KOIStock sẵn sàng cho quy trình kho RFID",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "App đã có shell nghiệp vụ, kết nối R6 và hướng dẫn thao tác ngay trên từng màn hình để đội kho review trực tiếp trên điện thoại.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onOpen(AppDestinations.Pairing.route) }) {
                            Text(if (isConnected) "Quản lý kết nối" else "Kết nối R6")
                        }
                        Button(onClick = { onOpen(AppDestinations.Guide.route) }) {
                            Text("Xem hướng dẫn")
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Trạng thái hệ thống", style = MaterialTheme.typography.titleMedium)
                    StatusRow("R6 BLE", when (connectionState) {
                        is ConnectionState.Connected -> "Đã kết nối ${connectionState.mac}"
                        is ConnectionState.Connecting -> "Đang kết nối ${connectionState.mac}"
                        ConnectionState.Scanning -> "Đang tìm thiết bị"
                        ConnectionState.Disconnected -> "Chưa kết nối"
                    })
                    StatusRow("Firebase", "Chờ bổ sung google-services.json")
                    StatusRow("Chainway reader", "Đã chuyển sang reader thật để kiểm tra trên điện thoại")
                    AssistChip(onClick = {}, label = { Text("Ưu tiên khóa kết nối R6 thật trước") })
                }
            }
        }

        item {
            Text("Các nghiệp vụ đã sẵn sàng", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(580.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(AppDestinations.menuItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        shape = RoundedCornerShape(20.dp),
                        onClick = { onOpen(item.route) },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SoftAmber),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Hướng triển khai tiếp theo", style = MaterialTheme.typography.titleMedium)
                    Text("1. Thêm google-services.json và bật Anonymous Auth / Firestore")
                    Text("2. Xác nhận kết nối, inventory, locate và write EPC với R6 thật")
                    Text("3. Mở rộng dữ liệu thật cho từng màn nghiệp vụ")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
