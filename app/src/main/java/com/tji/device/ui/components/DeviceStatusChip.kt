package com.tji.device.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(isOnline: Boolean) {

    val textColor = if (isOnline) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFD32F2F)
    }

    val dotColor = if (isOnline) {
        Color(0xFF4CAF50)
    } else {
        Color(0xFFF44336)
    }

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isOnline) "在线" else "离线",
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }

}


@Preview(showBackground = true)
@Composable
fun onlineExample() {
    StatusChip(isOnline = false)
}

@Preview(showBackground = true)
@Composable
fun offlineExample() {
    StatusChip(isOnline = true)
}