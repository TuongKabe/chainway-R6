package com.example.koistock.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ScanFunction
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.TriggerMode

@Composable
fun ScanConfigScreen(
    function: ScanFunction,
    profile: ScanProfile,
    onSave: (ScanProfile) -> Unit,
    onResetDefault: () -> Unit,
) {
    // Bản nháp đang chỉnh; khởi tạo lại khi profile nguồn đổi (ví dụ sau khi Đặt mặc định).
    var draft by remember(profile) { mutableStateOf(profile) }
    val dirty = draft != profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Cấu hình quét: ${function.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        ConfigCard("Chế độ cò") {
            ChipRow(
                options = listOf(
                    TriggerMode.SINGLE to "Bóp 1 lần",
                    TriggerMode.CONTINUOUS to "Quét liên tục",
                ),
                selected = draft.triggerMode,
                onSelect = { draft = draft.copy(triggerMode = it) },
            )
            Text(
                if (draft.triggerMode == TriggerMode.SINGLE) {
                    "Mỗi lần bóp cò quét một lần (chức năng gom hàng: một đợt quét ngắn ~1.5s rồi tự dừng)."
                } else {
                    "Bóp cò lần 1 để bắt đầu quét, bóp lần 2 để kết thúc (cò R6 không có giữ/thả tin cậy)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConfigCard("Công suất: ${draft.power} / 30 dBm") {
            Slider(
                value = draft.power.toFloat(),
                onValueChange = { draft = draft.copy(power = it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
            )
            Text(
                "Cao (26–30): xa/nhiều thẻ. Thấp (12–20): quét đơn chính xác, tránh đọc nhầm thẻ lân cận.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConfigCard("Session") {
            ChipRow(
                options = (0..3).map { it to "S$it" },
                selected = draft.session,
                onSelect = { draft = draft.copy(session = it) },
            )
            Text(
                "S0: thẻ trả lời mọi vòng (quét đơn/định vị). S1–S2: mỗi thẻ 1 lần (đếm nhiều thẻ).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConfigCard("Q value: ${draft.q}") {
            Slider(
                value = draft.q.toFloat(),
                onValueChange = { draft = draft.copy(q = it.toInt()) },
                valueRange = 0f..15f,
                steps = 14,
            )
            Text(
                "Ít thẻ trong tầm → Q thấp (0–2). Nhiều thẻ → Q cao (6–8).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConfigCard("Miller (độ nhạy)") {
            ChipRow(
                options = listOf(0 to "FM0", 1 to "M2", 2 to "M4", 3 to "M8"),
                selected = draft.millerM,
                onSelect = { draft = draft.copy(millerM = it) },
            )
            Text(
                "M cao (M4/M8): nhạy hơn, chống nhiễu tốt hơn nhưng chậm hơn. FM0: nhanh nhất.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConfigCard("Nâng cao") {
            SwitchRow(
                label = "TagFocus",
                hint = "Bật: thẻ im sau 1 lần đọc (đếm nhanh). Tắt cho định vị.",
                checked = draft.tagFocus,
                onChange = { draft = draft.copy(tagFocus = it) },
            )
            SwitchRow(
                label = "FastID",
                hint = "Đọc kèm TID (chậm hơn). Tắt khi chỉ cần EPC.",
                checked = draft.fastId,
                onChange = { draft = draft.copy(fastId = it) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSave(draft) },
                enabled = dirty,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (dirty) "Lưu" else "Đã lưu")
            }
            OutlinedButton(
                onClick = onResetDefault,
                modifier = Modifier.weight(1f),
            ) {
                Text("Đặt mặc định")
            }
        }
    }
}

@Composable
private fun ConfigCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
