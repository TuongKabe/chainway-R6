package com.example.koistock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.koistock.ui.theme.Tangerine
import com.example.koistock.ui.theme.VividGreen

/**
 * Hiển thị mức pin đầu đọc R6 dạng icon + phần trăm, đổi màu theo mức.
 * `percent` = null nghĩa là chưa có dữ liệu (chưa kết nối) -> hiện dấu "—".
 */
@Composable
fun BatteryIndicator(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when {
        percent == null -> Icons.AutoMirrored.Filled.BatteryUnknown
        percent >= 60 -> Icons.Filled.BatteryFull
        percent >= 25 -> Icons.Filled.BatteryStd
        else -> Icons.Filled.BatteryAlert
    }
    val tint: Color = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percent >= 50 -> VividGreen
        percent >= 20 -> Tangerine
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Mức pin đầu đọc",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
