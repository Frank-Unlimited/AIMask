package com.example.aidama.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.min
import com.example.aidama.data.model.OcrRect

@Composable
fun ImagePreview(modifier: Modifier = Modifier, imageUri: Uri?, ocrRects: List<OcrRect>, originalImageSize: IntSize?, zoomLevel: Float, panOffset: Offset, onPanChange: (Offset) -> Unit, selectedOcrIndices: Set<Int>, onOcrRectClick: (Int) -> Unit, isAiProcessed: Boolean, isEffectView: Boolean, isExporting: Boolean = false, typeColorMap: Map<String, Color>) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        if (imageUri != null) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(ocrRects, originalImageSize, zoomLevel, panOffset) {
                        detectTapGestures { tapOffset ->
                            if (zoomLevel == 1f) return@detectTapGestures
                            originalImageSize?.let { imgSize ->
                                val scale = min(size.width / imgSize.width.toFloat(), size.height / imgSize.height.toFloat())
                                val transformedTap = Offset((tapOffset.x - panOffset.x - (size.width - imgSize.width * scale * zoomLevel) / 2) / (scale * zoomLevel), (tapOffset.y - panOffset.y - (size.height - imgSize.height * scale * zoomLevel) / 2) / (scale * zoomLevel))
                                ocrRects.indices.filter { Rect(ocrRects[it].x.toFloat(), ocrRects[it].y.toFloat(), (ocrRects[it].x + ocrRects[it].width).toFloat(), (ocrRects[it].y + ocrRects[it].height).toFloat()).contains(transformedTap) }.minByOrNull { ocrRects[it].width * ocrRects[it].height }?.let { onOcrRectClick(it) }
                            }
                        }
                    }
                    .pointerInput(zoomLevel, panOffset) { detectDragGestures { _, drag -> if (zoomLevel > 1f) onPanChange(panOffset + drag * 5f) } }
                    .graphicsLayer { scaleX = zoomLevel; scaleY = zoomLevel; translationX = panOffset.x; translationY = panOffset.y },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (originalImageSize != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val scale = min(size.width / originalImageSize.width, size.height / originalImageSize.height)
                        val offX = (size.width - originalImageSize.width * scale) / 2
                        val offY = (size.height - originalImageSize.height * scale) / 2
                        ocrRects.forEachIndexed { index, r ->
                            val selected = selectedOcrIndices.contains(index)
                            val left = r.x * scale + offX; val top = r.y * scale + offY
                            if (selected && isEffectView) {
                                val color = typeColorMap[r.type] ?: Color.Gray
                                val maskColor = if (isExporting) Color.Black else color.copy(alpha = 0.45f)
                                drawRect(color = maskColor, topLeft = Offset(left, top), size = Size(r.width * scale, r.height * scale))
                                if (!isExporting) drawRect(color, Offset(left, top), Size(r.width * scale, r.height * scale), style = Stroke(1.dp.toPx()))
                            } else {
                                val c = if (!isAiProcessed) Color.Yellow else if (r.sensitivity == "high") Color.Red else Color.Yellow
                                drawRect(c.copy(alpha = 0.5f), Offset(left, top), Size(r.width * scale, r.height * scale), style = Stroke(0.5.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}