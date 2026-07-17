package com.example.koistock.ui.locate

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.ui.theme.ElectricBlue
import com.example.koistock.ui.theme.Tangerine
import com.example.koistock.ui.theme.VividGreen

@Composable
fun LocateScreen(
    vm: LocateViewModel,
    products: List<Product>,
    tagRepo: TagRepo,
    isConnected: Boolean,
    onOpenPairing: () -> Unit,
) {
    var selectedSku by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember(selectedSku, products) { products.firstOrNull { it.sku == selectedSku } }

    if (selected == null) {
        ProductPicker(products = products, onSelect = { selectedSku = it.sku })
    } else {
        LocatePhase(
            vm = vm,
            product = selected,
            tagRepo = tagRepo,
            isConnected = isConnected,
            onChangeProduct = {
                vm.stop()
                selectedSku = null
            },
            onOpenPairing = onOpenPairing,
        )
    }
}

@Composable
private fun ProductPicker(
    products: List<Product>,
    onSelect: (Product) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, products) {
        if (query.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(query, ignoreCase = true) || it.sku.contains(query, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Chọn sản phẩm cần định vị", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Tìm theo tên hoặc SKU") },
        )
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (products.isEmpty()) "Chưa có dữ liệu sản phẩm. Hãy đồng bộ ở trang Tổng quan." else "Không tìm thấy sản phẩm phù hợp.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.sku }) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { onSelect(product) },
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(
                                "SKU ${product.sku}" + product.locationCode.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
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
private fun LocatePhase(
    vm: LocateViewModel,
    product: Product,
    tagRepo: TagRepo,
    isConnected: Boolean,
    onChangeProduct: () -> Unit,
    onOpenPairing: () -> Unit,
) {
    val signal by vm.signal.collectAsState()
    val interval by vm.intervalMs.collectAsState()
    val isLocating by vm.isLocating.collectAsState()
    val rawReads by vm.rawReads.collectAsState()
    val lastSeenEpc by vm.lastSeenEpc.collectAsState()
    val lastRawRssi by vm.lastRawRssi.collectAsState()

    var tags by remember(product.sku) { mutableStateOf<List<TagMapping>?>(null) }
    var selectedEpc by rememberSaveable(product.sku) { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    // Tải danh sách EPC thật đã gán cho SKU này.
    LaunchedEffect(product.sku) {
        val loaded = tagRepo.listBySku(product.sku).filter { it.status == "active" }
        tags = loaded
        selectedEpc = loaded.firstOrNull()?.epc
    }

    // Đồng bộ tag đang chọn để cò trên R6 có thể bắt đầu dò.
    LaunchedEffect(selectedEpc) {
        vm.setTarget(selectedEpc)
    }

    // Dừng dò khi rời màn.
    DisposableEffect(product.sku) {
        onDispose { vm.stop() }
    }

    if (showHelp) {
        LocateHelpDialog(onDismiss = { showHelp = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header sản phẩm + nút hướng dẫn.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "SKU ${product.sku}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Hướng dẫn dò")
                }
            }
        }

        when {
            !isConnected -> ConnectBanner(onOpenPairing = onOpenPairing)

            tags == null -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            tags.isNullOrEmpty() -> InfoBanner(
                "Sản phẩm này chưa có tag EPC nào được gán. Hãy gán tag trước khi định vị.",
            )

            else -> {
                TagSelector(
                    tags = tags.orEmpty(),
                    selectedEpc = selectedEpc,
                    enabled = !isLocating,
                    onSelect = { selectedEpc = it },
                )

                if (isLocating) {
                    OutlinedButton(onClick = { vm.stop() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Dừng dò")
                    }
                } else {
                    Button(
                        onClick = { selectedEpc?.let { vm.start(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedEpc != null,
                    ) {
                        Text("Bắt đầu dò")
                    }
                }

                SignalGauge(signal = signal, isLocating = isLocating)

                if (isLocating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Nhịp beep: $interval ms — càng gần tag, beep càng nhanh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Chẩn đoán: cho biết reader có đọc thẻ không, và thẻ vừa đọc.
                            Text(
                                "Đã đọc: $rawReads lượt" + (lastSeenEpc?.let { " · gần nhất: $it" } ?: "") +
                                    (lastRawRssi?.let { " · RSSI thô: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (rawReads == 0) {
                                Text(
                                    "Chưa đọc được thẻ nào. Kiểm tra đầu đọc/công suất và đưa lại gần thẻ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (signal == 0) {
                                Text(
                                    "Đang đọc thẻ nhưng chưa khớp EPC mục tiêu ($selectedEpc). Có thể thẻ chưa gán đúng EPC này.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(onClick = onChangeProduct, modifier = Modifier.fillMaxWidth()) {
                    Text("Đổi sản phẩm")
                }
            }
        }
    }
}

/**
 * Màn hình tín hiệu lớn, đổi màu theo cường độ để nhìn từ xa khi đang rê đầu đọc.
 * Xám = chưa thấy, Xanh dương = còn xa, Cam = đang gần, Xanh lá = rất gần.
 */
@Composable
private fun SignalGauge(signal: Int, isLocating: Boolean) {
    val target = when {
        !isLocating -> MaterialTheme.colorScheme.surfaceVariant
        signal <= 0 -> MaterialTheme.colorScheme.surfaceVariant
        signal >= 70 -> VividGreen
        signal >= 40 -> Tangerine
        else -> ElectricBlue
    }
    val background by animateColorAsState(targetValue = target, label = "signalColor")
    val onColor = if (!isLocating || signal <= 0) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }
    val label = when {
        !isLocating -> "CHƯA DÒ"
        signal <= 0 -> "CHƯA THẤY TAG"
        signal >= 70 -> "RẤT GẦN"
        signal >= 40 -> "ĐANG GẦN"
        else -> "CÒN XA"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$signal",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.titleMedium,
                color = onColor,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { signal / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = onColor,
                trackColor = onColor.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun TagSelector(
    tags: List<TagMapping>,
    selectedEpc: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (tags.size == 1) "Tag cần dò" else "Chọn tag cần dò (${tags.size} tag)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tags.forEach { tag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { if (enabled) onSelect(tag.epc) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedEpc == tag.epc,
                        onClick = { if (enabled) onSelect(tag.epc) },
                        enabled = enabled,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tag.epc, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        tag.unitSerial?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "Serial $it",
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
private fun ConnectBanner(onOpenPairing: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Cần kết nối đầu đọc R6 để định vị.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onOpenPairing) { Text("Kết nối R6") }
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocateHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đã hiểu") } },
        title = { Text("Cách định vị sản phẩm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Đảm bảo đầu đọc R6 đã kết nối và còn pin.")
                Text("2. Chọn tag EPC của sản phẩm cần tìm.")
                Text("3. Bấm \"Bắt đầu dò\" trên màn hình HOẶC bấm cò trên đầu đọc R6.")
                Text("4. Rê đầu đọc chậm quanh khu vực; theo dõi thanh tín hiệu và tiếng beep: càng gần tag, tín hiệu càng cao và beep càng nhanh.")
                Text("5. Bấm \"Dừng dò\" hoặc bấm cò lần nữa khi đã tìm thấy.")
            }
        },
    )
}
