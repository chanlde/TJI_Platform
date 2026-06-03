package com.tji.device.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.TjiControlDisabled
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiTextMuted
import com.tji.device.ui.theme.TjiWarning

@Composable
fun TjiActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .defaultMinSize(minWidth = 96.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = TjiControlDisabled,
            disabledContentColor = TjiTextMuted
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true, name = "TJI Action Buttons")
@Composable
private fun TjiActionButtonPreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(64.dp)
            .defaultMinSize(minWidth = 220.dp)
    ) {
        TjiActionButton(
            text = "开启",
            enabled = true,
            color = TjiPrimary,
            onClick = {}
        )
        TjiActionButton(
            text = "关闭",
            enabled = true,
            color = TjiWarning,
            onClick = {}
        )
    }
}
