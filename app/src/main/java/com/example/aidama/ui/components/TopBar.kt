package com.example.aidama.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidama.R

@Composable
fun TopBar(onExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = R.drawable.ic_launcher_playstore), contentDescription = "Logo", modifier = Modifier.size(64.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(8.dp)); Text(text = "慧码", color = Color.White, fontSize = 20.sp)
        }
        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))) { Text(text = "导出结果", color = Color.White) }
    }
}