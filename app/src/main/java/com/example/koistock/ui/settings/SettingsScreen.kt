package com.example.koistock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.ScanFunction
import com.example.koistock.device.R6Region
import com.example.koistock.device.Readback
import com.example.koistock.ui.shell.AppDestinations

@Composable
fun SettingsScreen(
    connectionState: ConnectionState,
    baseUrl: String,
    r6State: R6SettingsState,
    onSelectRegion: (R6Region) -> Unit,
    onRefreshR6: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val connectionSummary = when (connectionState) {
        is ConnectionState.Connected -> "Đã kết nối · ${connectionState.mac}"
        is ConnectionState.Connecting -> "Đang kết nối…"
        ConnectionState.Scanning -> "Đang tìm thiết bị…"
        ConnectionState.Disconnected -> "Chưa kết nối"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Thiết bị")
        SettingsRow(
            icon = Icons.Filled.Bluetooth,
            title = "Kết nối R6",
            subtitle = connectionSummary,
            onClick = { onOpen(AppDestinations.Pairing.route) },
        )
        R6DeviceConfigCard(r6State, onSelectRegion, onRefreshR6)
        SettingsRow(
            icon = Icons.Filled.Memory,
            title = "Test phần cứng R6",
            subtitle = "Kiểm tra scan, định vị, ghi EPC, cò bấm",
            onClick = { onOpen(AppDestinations.Hardware.route) },
        )
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "Hướng dẫn kết nối",
            subtitle = "Checklist ghép nối và cấp quyền",
            onClick = { onOpen(AppDestinations.Guide.route) },
        )

        SectionTitle("Cấu hình quét theo chức năng")
        Text(
            "Chỉnh chế độ cò (bóp 1 lần / nhấn giữ) và thông số Chainway riêng cho từng chức năng.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanFunction.entries.forEach { fn ->
            SettingsRow(
                icon = Icons.Filled.Tune,
                title = fn.label,
                subtitle = "Chế độ cò + công suất, session, Q, Miller…",
                onClick = { onOpen(AppDestinations.scanConfigRoute(fn)) },
            )
        }

        SectionTitle("Hệ thống")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoLine("Backend API", baseUrl)
                InfoLine("Kiến trúc", "App → Koi backend → PostgreSQL → Airflow → Google Sheet")
            }
        }
    }
}

@Composable
private fun R6DeviceConfigCard(
    state: R6SettingsState,
    onSelectRegion: (R6Region) -> Unit,
    onRefresh: () -> Unit,
) {
    val actual = when (val value = state.snapshot?.region) {
        is Readback.Value -> "${value.value.label} · ${value.value.bandLabel}"
        is Readback.Failed -> "Không đọc được: ${value.message}"
        Readback.Unsupported -> "Firmware không hỗ trợ đọc lại"
        null -> "Chưa đọc từ máy"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Vùng tần số chung R6", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                R6Region.entries.forEach { region ->
                    FilterChip(
                        selected = state.desiredConfig.region == region,
                        onClick = { onSelectRegion(region) },
                        label = { Text(region.label) },
                    )
                }
            }
            InfoLine("App yêu cầu", "${state.desiredConfig.region.label} · ${state.desiredConfig.region.bandLabel}")
            InfoLine("R6 thực tế", actual)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onRefresh, enabled = state.connected && !state.loading) {
                Text(if (state.loading) "Đang đọc…" else "Đọc lại từ máy")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
