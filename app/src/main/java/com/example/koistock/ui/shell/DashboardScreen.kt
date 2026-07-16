package com.example.koistock.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState
import com.example.koistock.ui.theme.Ash
import com.example.koistock.ui.theme.ElectricBlue
import com.example.koistock.ui.theme.PaperMist
import com.example.koistock.ui.theme.SoftMint
import com.example.koistock.ui.theme.Tangerine

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    onOpen: (String) -> Unit,
) {
    val isConnected = connectionState is ConnectionState.Connected

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Ash),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardPill("RFID OPS", SoftMint, ElectricBlue)
                        DashboardPill(if (isConnected) "R6 ONLINE" else "R6 OFFLINE", PaperMist, Tangerine)
                    }
                    Text(
                        text = "KOIStock điều khiển kho RFID theo backend mới",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "App đã chuyển sang mô hình HTTP → Koi backend → PostgreSQL/Airflow. Giờ ưu tiên test flow thật trên R6 và tối ưu UI thao tác ngoài kho.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onOpen(AppDestinations.Pairing.route) }) {
                            Text(if (isConnected) "Quản lý kết nối" else "Kết nối R6")
                        }
                        OutlinedButton(onClick = { onOpen(AppDestinations.Guide.route) }) {
                            Text("Xem hướng dẫn")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Ash),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Trạng thái hệ thống", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    StatusRow(
                        "R6 BLE",
                        when (connectionState) {
                            is ConnectionState.Connected -> "Đã kết nối ${connectionState.mac}"
                            is ConnectionState.Connecting -> "Đang kết nối ${connectionState.mac}"
                            ConnectionState.Scanning -> "Đang tìm thiết bị"
                            ConnectionState.Disconnected -> "Chưa kết nối"
                        },
                    )
                    StatusRow("Backend API", "https://rfid.bangtuong.online/")
                    StatusRow("Kiến trúc", "App → Koi backend → PostgreSQL → Airflow → Google Sheet")
                }
            }
        }

        item {
            Text("Các nghiệp vụ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Ash),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { onOpen(item.route) },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    }
}

@Composable
private fun DashboardPill(label: String, background: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperMist, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
