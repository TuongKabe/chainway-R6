package com.example.koistock.ui.lookup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.ui.theme.Ash
import com.example.koistock.ui.theme.ElectricBlue
import com.example.koistock.ui.theme.PaperMist
import com.example.koistock.ui.theme.Tangerine

@Composable
fun LookupScreen(
    vm: LookupViewModel,
    onAssign: (String) -> Unit,
) {
    val result by vm.result.collectAsState()

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Ash),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Quét một thẻ RFID để xem thông tin sản phẩm, tồn kho và vị trí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = vm::scanOnce,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Quét thẻ")
                    }
                    OutlinedButton(
                        onClick = vm::clear,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Ash),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (val state = result) {
                    LookupResult.Idle -> LookupInfoBlock(
                        badge = "READY",
                        title = "Sẵn sàng quét",
                        message = "Chưa có kết quả nào. Bấm Quét thẻ để bắt đầu.",
                    )

                    LookupResult.NotFound -> LookupInfoBlock(
                        badge = "NO READ",
                        title = "Không đọc được tag",
                        message = "Kiểm tra kết nối R6 hoặc đưa tag lại gần đầu đọc hơn.",
                        badgeColor = Tangerine,
                    )

                    is LookupResult.UnknownTag -> {
                        LookupInfoBlock(
                            badge = "UNMAPPED",
                            title = "Tag chưa được map",
                            message = "EPC ${state.epc} chưa có trong hệ thống.",
                            badgeColor = Tangerine,
                        )
                        OutlinedButton(onClick = { onAssign(state.epc) }) {
                            Text("Gán tag này")
                        }
                    }

                    is LookupResult.Error -> LookupInfoBlock(
                        badge = "ERROR",
                        title = "Tra cứu thất bại",
                        message = state.message,
                        badgeColor = Tangerine,
                    )

                    is LookupResult.Found -> {
                        LookupInfoBlock(
                            badge = "FOUND",
                            title = state.product.name,
                            message = "Đã tìm thấy dữ liệu tag và sản phẩm.",
                        )
                        LookupField("SKU", state.product.sku)
                        LookupField("Đơn vị", state.product.unit)
                        LookupField("Tracking", state.product.trackingMode.name)
                        LookupField("Tồn hiện tại", state.product.quantity.toString())
                        LookupField("Vị trí", state.product.locationCode.ifBlank { "Chưa gán" })
                        LookupField("EPC", state.tag.epc)
                        LookupField("Serial", state.tag.unitSerial ?: "—")
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupInfoBlock(
    badge: String,
    title: String,
    message: String,
    badgeColor: androidx.compose.ui.graphics.Color = ElectricBlue,
) {
    Box(
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(badge, style = MaterialTheme.typography.labelMedium, color = badgeColor)
    }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(message, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LookupField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperMist, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
