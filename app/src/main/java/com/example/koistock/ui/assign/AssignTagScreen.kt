package com.example.koistock.ui.assign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.Product

@Composable
fun AssignTagScreen(
    vm: AssignTagViewModel,
    products: List<Product>,
    prefillEpc: String? = null,
) {
    val epc by vm.scannedEpc.collectAsState()
    val barcode by vm.barcode.collectAsState()
    val working by vm.working.collectAsState()
    val result by vm.result.collectAsState()
    val assignSession by vm.assignSession.collectAsState()
    val sessionLoading by vm.sessionLoading.collectAsState()
    val autoRefresh by vm.autoRefresh.collectAsState()
    var structured by remember { mutableStateOf(false) }
    var selectedSku by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, products) {
        if (query.isBlank()) products
        else products.filter { it.name.contains(query, true) || it.sku.contains(query, true) }
    }

    LaunchedEffect(assignSession?.itemCode) {
        assignSession?.itemCode?.let { selectedSku = it }
    }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    result?.let { r ->
        AssignResultDialog(result = r, onDismiss = vm::acknowledgeResult)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Bridge webapp ↔ Android: app sẽ tự tìm session chờ và có thể auto confirm luôn sau khi quét.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = autoRefresh, onCheckedChange = vm::setAutoRefresh)
            Spacer(Modifier.width(8.dp))
            Text("Tự làm mới session web mỗi vài giây")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.refreshLatestAssignSession() }, enabled = !sessionLoading && !working, modifier = Modifier.weight(1f)) {
                if (sessionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Nhận session web")
                }
            }
            OutlinedButton(onClick = vm::clearAssignSession, enabled = assignSession != null, modifier = Modifier.weight(1f)) {
                Text("Bỏ session")
            }
        }
        if (assignSession != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Session web: ${assignSession?.id}", fontWeight = FontWeight.SemiBold)
                Text("SKU: ${assignSession?.item?.itemName ?: assignSession?.itemCode ?: "—"}")
                Text("Kho: ${assignSession?.warehouse ?: "—"} · Vị trí: ${assignSession?.locationCode ?: "—"}")
                Text(
                    when (assignSession?.status) {
                        "confirmed" -> "Session này đã auto confirm xong. Webapp sẽ hiện confirmed."
                        "scanned" -> "Session đang ở scanned. App sẽ thử auto confirm luôn sau khi gửi EPC."
                        else -> "Đang chờ EPC từ thiết bị R6."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(onClick = vm::scanBlank, modifier = Modifier.fillMaxWidth()) {
            Text("Quét tag trống")
        }
        Text(
            "EPC: ${epc ?: prefillEpc ?: "—"}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (assignSession != null) {
            Button(
                onClick = vm::pushCurrentEpcToAssignSession,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working && (epc ?: prefillEpc) != null,
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Gửi EPC + auto confirm")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = structured, onCheckedChange = { structured = it })
            Spacer(Modifier.width(8.dp))
            Text("Ghi mã KOI có cấu trúc")
        }
        OutlinedTextField(
            value = barcode,
            onValueChange = vm::updateBarcode,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Mã vạch") },
            supportingText = {
                Text("Đưa focus vào ô này rồi quét barcode. Khi cần vẫn có thể paste / nhập tay.")
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::clearBarcode, enabled = barcode.isNotBlank()) {
                Text("Xóa barcode")
            }
            Text(
                "Sheet target: koistock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Tìm SKU cần gán") },
        )
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.sku }) { product ->
                ListItem(
                    headlineContent = { Text(product.name) },
                    supportingContent = { Text(product.sku) },
                    trailingContent = {
                        RadioButton(
                            selected = selectedSku == product.sku,
                            onClick = { selectedSku = product.sku },
                        )
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedSku?.let { vm.assign(it, structured, pushBarcodeToSheet = false) } },
                modifier = Modifier.weight(1f),
                enabled = !working && selectedSku != null && (epc ?: prefillEpc) != null,
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Gán tag")
                }
            }
            Button(
                onClick = { selectedSku?.let { vm.assign(it, structured, pushBarcodeToSheet = true) } },
                modifier = Modifier.weight(1f),
                enabled = !working && selectedSku != null && (epc ?: prefillEpc) != null && barcode.isNotBlank(),
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Gán + ghi Sheet")
                }
            }
        }
    }
}

@Composable
private fun AssignResultDialog(
    result: AssignResult,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (result) {
        is AssignResult.Success -> {
            val sentToWebSession = result.note?.contains("web session", ignoreCase = true) == true
            val detail = buildString {
                append(
                    if (sentToWebSession) {
                        "Đã gửi EPC ${result.epc} cho SKU ${result.sku} sang web session."
                    } else {
                        "Đã liên kết EPC ${result.epc} với SKU ${result.sku}."
                    }
                )
                result.barcode?.takeIf { it.isNotBlank() }?.let { append("\nBarcode: $it") }
                result.note?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
            }
            (if (sentToWebSession) "Đã gửi EPC sang web ✓" else "Gán tag thành công ✓") to detail
        }

        is AssignResult.PartialSuccess -> "Gán tag xong, bước sau chưa xong" to result.message
        is AssignResult.Error -> "Gán tag thất bại" to result.message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đã hiểu") } },
        title = { Text(title) },
        text = { Text(body) },
    )
}
