package com.example.aidama.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ImageThumbnails(
    images: List<Uri>,
    selectedImageIndex: Int,
    onImageSelect: (Int) -> Unit,
    onImageRemove: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(images) { index, imageUri ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onImageSelect(index) }
                        .border(
                            width = 2.dp,
                            color = if (index == selectedImageIndex) Color(0xFF4A90E2) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Clearer remove button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp) // Margin from the corner
                            .size(22.dp)   // The overall size of the clickable background circle
                            .clip(CircleShape)
                            .background(Color.Red)
                            .clickable { onImageRemove(index) },
                        contentAlignment = Alignment.Center // Center the icon inside this Box
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp) // The size of the 'X' icon itself
                        )
                    }
                }
            }
        }
    }
}