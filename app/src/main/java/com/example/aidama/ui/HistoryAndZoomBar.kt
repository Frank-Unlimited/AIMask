package com.example.aidama.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HistoryAndZoomBar() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = { /*TODO: Undo */ }) {
            Text("撤销", color = Color.Gray)
        }

        Row(
            modifier = Modifier
                .background(Color(0x80424242), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /*TODO: Zoom out */ }) {
                Text("-", color = Color.White, fontSize = 20.sp)
            }
            Divider(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray))
            IconButton(onClick = { /*TODO: Zoom in */ }) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
        }

        TextButton(onClick = { /*TODO: Redo */ }) {
            Text("重做", color = Color.Gray)
        }
    }
}