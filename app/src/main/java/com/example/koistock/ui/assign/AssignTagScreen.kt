package com.example.koistock.ui.assign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.koistock.ui.common.FeatureGuideButton

@Composable
fun AssignTagScreen(
    vm: AssignTagViewModel,
    products: List<Product>,
    prefillEpc: String? = null,
) {
    val epc by vm.scannedEpc.collectAsState()
    val done by vm.done.collectAsState()
    var structured by remember { mutableStateOf(false) }
    var selectedSku by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Gán tag cho hàng mới", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn gán tag",
                quickSteps = listOf(
                    "Quét một tag trắng hoặc tag chưa dùng.",
                    "Chọn đúng SKU cần gán.",
                    "Bật chế độ EPC có cấu trúc nếu muốn chuẩn hóa mã KOI.",
                    "Nhấn Gán để lưu mapping.",
                ),
                notes = listOf(
                    "Không gán nhầm EPC đang dùng cho SKU khác.",
                    "Nếu write EPC thất bại, hãy kiểm tra lại khoảng cách tới tag.",
                ),
            )
        }
        Button(onClick = vm::scanBlank, modifier = Modifier.fillMaxWidth()) {
            Text("Quét tag trống")
        }
        Text("EPC: ${epc ?: prefillEpc ?: "—"}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = structured, onCheckedChange = { structured = it })
            Spacer(Modifier.width(8.dp))
            Text("Ghi mã KOI có cấu trúc")
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products) { product ->
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
        Button(
            onClick = { selectedSku?.let { vm.assign(it, structured) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedSku != null && (epc ?: prefillEpc) != null,
        ) {
            Text(if (done) "Đã gán ✓" else "Gán")
        }
    }
}
