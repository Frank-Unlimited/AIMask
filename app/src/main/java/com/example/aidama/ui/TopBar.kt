package com.example.aidama.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource // 必须引入这个
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidama.R // 确保引入了 R 文件

@Composable
fun TopBar(onExport: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // --- 修改这里 ---
            Icon(
                painter = painterResource(id = R.drawable.ic_logo), // 使用你的图片资源
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp), // 可以手动调整 logo 大小
                tint = Color.Unspecified // 如果你的 Logo 是彩色的，请设为 Unspecified 保持原色
            )
            // ----------------
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "慧码", color = Color.White, fontSize = 20.sp)
        }
        Button(
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
        ) {
            Text(text = "导出结果", color = Color.White)
        }
    }
}