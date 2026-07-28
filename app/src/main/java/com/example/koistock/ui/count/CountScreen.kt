package com.example.koistock.ui.count

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.ui.common.ScanTriggerDialog
import com.example.koistock.util.shareCsv
import kotlinx.coroutines.launch

internal fun toggleExpandedSku(currentSku: String?, tappedSku: String): String? =
    if (currentSku == tappedSku) null else tappedSku

internal const val COUNT_LOCATION_HELP = "Để trống để đối chiếu Toàn khu"

@Composable
fun CountScreen(
    vm: CountViewModel,
    locations: List<LocationNode>,
) {
    val zone by vm.zone.collectAsState()
    val counted by vm.countedBySku.collectAsState()
    val scannedSkuRows by vm.scannedSkuRows.collectAsState()
    val rows by vm.rows.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val isReconciling by vm.isReconciling.collectAsState()
    val reconcileMessage by vm.reconcileMessage.collectAsState()
    val scopeLabel by vm.scopeLabel.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var zoneInput by remember(zone) { mutableStateOf(zone.orEmpty()) }
    var showScanDialog by remember { mutableStateOf(false) }
    var expandedSku by remember { mutableStateOf<String?>(null) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    LaunchedEffect(scannedSkuRows) {
        if (scannedSkuRows.none { it.product.sku == expandedSku }) expandedSku = null
    }

    if (showScanDialog) {
        ScanTriggerDialog(
            isScanning = isScanning,
            statusLine = "Đã quét: ${counted.size} SKU / ${counted.values.sum()} tag",
            onStop = vm::stopScan,
            onClose = { showScanDialog = false },
            title = "Quét theo khu",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Chọn khu, quét EPC, đối chiếu chênh lệch và xuất CSV.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = zoneInput,
            onValueChange = {
                zoneInput = it
                vm.setZone(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mã khu/kệ") },
            placeholder = { Text("Để trống: Toàn khu") },
            supportingText = { Text(COUNT_LOCATION_HELP) },
        )
        Button(
            onClick = { showScanDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bắt đầu quét")
        }

        Text(
            "Đã quét: ${scannedSkuRows.sumOf { it.scannedTagCount }} tag · ${scannedSkuRows.size} SKU",
            style = MaterialTheme.typography.titleMedium,
        )
        if (scannedSkuRows.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Chưa có EPC nào được gom.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            scannedSkuRows.forEach { row ->
                CountedSkuAccordionCard(
                    row = row,
                    locations = locations,
                    expanded = expandedSku == row.product.sku,
                    onClick = {
                        expandedSku = toggleExpandedSku(expandedSku, row.product.sku)
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    vm.reconcile(locations)
                }
            }, enabled = !isReconciling) {
                if (isReconciling) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Đối chiếu")
                }
            }
            Button(onClick = { scope.launch { vm.saveCount() } }, enabled = counted.isNotEmpty()) {
                Text("Lưu count")
            }
            Button(
                onClick = { shareCsv(context, vm.csv(), "kiemke-${zoneInput.ifBlank { "draft" }}.csv") },
                enabled = rows.isNotEmpty(),
            ) {
                Text("Xuất CSV")
            }
        }

        Card {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Kết quả đối chiếu", style = MaterialTheme.typography.titleMedium)
                Text("Phạm vi: $scopeLabel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                reconcileMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (rows.isEmpty()) {
                    Text("Chưa chạy đối chiếu.")
                } else {
                    rows.forEach { row ->
                        Text("${row.sku} · ${row.name}")
                        Text(
                            "Đã quét ${row.scannedTagCount} tag · Tồn DB: ${row.dbStockQty} ${row.unit} · ${row.status}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountedSkuAccordionCard(
    row: CountedSkuRow,
    locations: List<LocationNode>,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val product = row.product
    val shelf = locations.firstOrNull {
        it.code == product.locationCode && it.type == LocationType.SHELF
    }
    val zone = locations.firstOrNull {
        it.code == shelf?.parent && it.type == LocationType.ZONE
    }
    val locationLabel = shelf?.let {
        "${zone?.code ?: "?"} → ${it.code} · ${it.name}"
    } ?: "Chưa có vị trí mặc định"

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${product.sku} · Đã quét ${row.scannedTagCount} tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Thu gọn" else "Mở chi tiết",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    HorizontalDivider()
                    DetailLine("SKU", product.sku)
                    DetailLine("Tên sản phẩm", product.name)
                    DetailLine("Tồn kho", "${product.quantity} ${product.unit}")
                    DetailLine(
                        "Loại quản lý",
                        if (product.trackingMode == TrackingMode.SERIALIZED) "Theo serial" else "Hàng số lượng",
                    )
                    DetailLine("Vị trí", locationLabel)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
