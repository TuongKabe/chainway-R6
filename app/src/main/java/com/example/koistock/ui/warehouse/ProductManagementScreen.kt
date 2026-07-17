package com.example.koistock.ui.warehouse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.ui.common.ShelfSelector
import kotlinx.coroutines.launch

@Composable
fun ProductManagementScreen(vm: ProductManagementViewModel) {
    val products by vm.filteredProducts.collectAsState()
    val filters by vm.filters.collectAsState()
    val units by vm.availableUnits.collectAsState()
    val zones by vm.zones.collectAsState()
    val shelves by vm.shelvesForSelectedZone.collectAsState()
    val locations by vm.locations.collectAsState()
    val editor by vm.editor.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    editor?.let { current ->
        ProductEditorDialog(
            state = current,
            locations = locations,
            onChange = vm::updateEditor,
            onUndo = vm::resetEditor,
            onDismiss = vm::closeEditor,
            onSave = {
                scope.launch {
                    when (val result = vm.save()) {
                        ProductSaveResult.Success -> {
                            message = "Đã lưu và đồng bộ ${current.sku}"
                            vm.closeEditor()
                        }
                        is ProductSaveResult.SavedButSyncFailed -> {
                            message = "Đã lưu nhưng chưa đồng bộ: ${result.message}"
                        }
                        is ProductSaveResult.Error -> {
                            message = result.message
                        }
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Tìm SKU hoặc tên sản phẩm") },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.trackingMode == TrackingMode.SERIALIZED,
                onClick = { vm.setTrackingMode(if (filters.trackingMode == TrackingMode.SERIALIZED) null else TrackingMode.SERIALIZED) },
                label = { Text("Theo serial") },
            )
            FilterChip(
                selected = filters.trackingMode == TrackingMode.BULK,
                onClick = { vm.setTrackingMode(if (filters.trackingMode == TrackingMode.BULK) null else TrackingMode.BULK) },
                label = { Text("Hàng số lượng") },
            )
            FilterChip(
                selected = filters.stock == StockFilter.IN_STOCK,
                onClick = { vm.setStockFilter(if (filters.stock == StockFilter.IN_STOCK) StockFilter.ALL else StockFilter.IN_STOCK) },
                label = { Text("Còn hàng") },
            )
            FilterChip(
                selected = filters.stock == StockFilter.OUT_OF_STOCK,
                onClick = { vm.setStockFilter(if (filters.stock == StockFilter.OUT_OF_STOCK) StockFilter.ALL else StockFilter.OUT_OF_STOCK) },
                label = { Text("Hết hàng") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceButton("Đơn vị", filters.unit, units, vm::setUnit)
            ChoiceButton("Khu", filters.zoneCode, zones.map { it.code }, vm::setZone)
            ChoiceButton("Kệ", filters.shelfCode, shelves.map { it.code }, vm::setShelf)
            TextButton(onClick = vm::clearFilters) { Text("Xóa bộ lọc") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("Danh sách SKU (${products.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(products, key = { it.sku }) { product ->
                val shelf = locations.firstOrNull { it.code == product.locationCode && it.type == LocationType.SHELF }
                val zone = locations.firstOrNull { it.code == shelf?.parent }
                Card(onClick = { vm.selectProduct(product.sku) }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(product.name, fontWeight = FontWeight.SemiBold)
                        Text("${product.sku} · ${product.quantity} ${product.unit}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            shelf?.let { "${zone?.code ?: "?"} → ${it.code} · ${it.name}" } ?: "Chưa chọn kệ mặc định",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    state: ProductEditorState,
    locations: List<com.example.koistock.data.model.LocationNode>,
    onChange: (String, String, TrackingMode, String, String?) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa sản phẩm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.sku, {}, readOnly = true, label = { Text("SKU (không đổi)") })
                OutlinedTextField(state.name, { onChange(it, state.unit, state.trackingMode, state.shelfCode, state.imageUrl) }, label = { Text("Tên sản phẩm") })
                OutlinedTextField(state.unit, { onChange(state.name, it, state.trackingMode, state.shelfCode, state.imageUrl) }, label = { Text("Đơn vị") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.trackingMode == TrackingMode.SERIALIZED,
                        onClick = { onChange(state.name, state.unit, TrackingMode.SERIALIZED, state.shelfCode, state.imageUrl) },
                        label = { Text("Serial") },
                    )
                    FilterChip(
                        selected = state.trackingMode == TrackingMode.BULK,
                        onClick = { onChange(state.name, state.unit, TrackingMode.BULK, state.shelfCode, state.imageUrl) },
                        label = { Text("Số lượng") },
                    )
                }
                ShelfSelector(locations, state.shelfCode) {
                    onChange(state.name, state.unit, state.trackingMode, it, state.imageUrl)
                }
                OutlinedTextField(
                    state.imageUrl.orEmpty(),
                    { onChange(state.name, state.unit, state.trackingMode, state.shelfCode, it) },
                    label = { Text("URL ảnh") },
                )
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Lưu") } },
        dismissButton = {
            Row {
                TextButton(onClick = onUndo) { Text("Hoàn tác") }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        },
    )
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: String?,
    values: List<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = !expanded }) { Text(selected ?: label) }
        androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Tất cả") },
                onClick = { onSelect(null); expanded = false },
            )
            values.forEach { value ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}
