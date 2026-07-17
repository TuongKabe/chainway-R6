package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    batteryPercent: Int?,
    isSyncing: Boolean,
    onOpen: (String) -> Unit,
    onSync: () -> Unit,
    onOpenPairing: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionStatusCard(
            connectionState = connectionState,
            batteryPercent = batteryPercent,
            isSyncing = isSyncing,
            onSync = onSync,
            onOpenPairing = onOpenPairing,
            modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            "Nghiệp vụ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            items(AppDestinations.menuItems) { item ->
                FeatureTile(item = item, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    batteryPercent: Int?,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionState is ConnectionState.Connected
    val statusText = when (connectionState) {
        is ConnectionState.Connected -> "Đã kết nối · ${connectionState.mac}"
        is ConnectionState.Connecting -> "Đang kết nối · ${connectionState.mac}"
        ConnectionState.Scanning -> "Đang tìm thiết bị…"
        ConnectionState.Disconnected -> "Chưa kết nối đầu đọc R6"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = if (isConnected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Đầu đọc Chainway R6",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isConnected && batteryPercent != null) {
                        Text(
                            "Pin: $batteryPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onOpenPairing) {
                    Text(if (isConnected) "Quản lý kết nối" else "Kết nối R6")
                }
                FilledTonalButton(onClick = onSync, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Đồng bộ Google Sheet")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(
    item: AppDestination,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { onOpen(item.route) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    item.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
