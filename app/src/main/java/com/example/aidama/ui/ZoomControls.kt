package com.example.aidama.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ZoomControls(
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0x80424242), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = { onZoomChange(zoomLevel + 0.1f) }) {
            Text("+", color = Color.White, fontSize = 20.sp)
        }
        Divider(modifier = Modifier.width(24.dp).padding(vertical = 4.dp).background(Color.Gray))
        IconButton(onClick = { onZoomChange(zoomLevel - 0.1f) }) {
            Text("-", color = Color.White, fontSize = 20.sp)
        }
    }
}