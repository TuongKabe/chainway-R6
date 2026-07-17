package com.example.koistock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Popup quét liên tục theo cò R6, dùng chung cho các chức năng quét hàng loạt.
 *
 * Luồng: mở popup ở trạng thái chờ → bấm cò lần 1 = bắt đầu (VM tự xử lý qua triggerEvents)
 * → bấm cò lần 2 = kết thúc → popup tự đóng. Có nút "Kết thúc"/"Đóng" dự phòng.
 *
 * @param isScanning trạng thái quét lấy từ ViewModel.
 * @param statusLine dòng mô tả tiến độ (ví dụ "Đã quét: 5 SKU").
 * @param onStop dừng quét (idempotent) — gọi khi bấm "Kết thúc".
 * @param onClose đóng popup.
 */
@Composable
fun ScanTriggerDialog(
    isScanning: Boolean,
    statusLine: String,
    onStop: () -> Unit,
    onClose: () -> Unit,
    title: String = "Quét bằng cò R6",
) {
    var startedOnce by remember { mutableStateOf(false) }

    // Ghi nhận đã từng bắt đầu; khi quét kết thúc (cò lần 2) thì tự đóng popup.
    LaunchedEffect(isScanning) {
        if (isScanning) {
            startedOnce = true
        } else if (startedOnce) {
            onClose()
        }
    }

    AlertDialog(
        onDismissRequest = {
            onStop()
            onClose()
        },
        confirmButton = {
            TextButton(onClick = {
                onStop()
                onClose()
            }) {
                Text(if (isScanning) "Kết thúc" else "Đóng")
            }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    if (isScanning) "Đang quét…" else "Chờ bấm cò",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isScanning) {
                        "Bấm cò lần nữa trên đầu đọc R6 để kết thúc."
                    } else {
                        "Bấm cò trên đầu đọc R6 để bắt đầu quét, bấm lần nữa để kết thúc."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isScanning && statusLine.isNotBlank()) {
                    Text(statusLine, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
        },
    )
}
