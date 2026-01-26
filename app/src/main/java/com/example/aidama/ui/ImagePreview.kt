package com.example.aidama.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.min

@Composable
fun ImagePreview(
    modifier: Modifier = Modifier,
    imageUri: Uri?,
    ocrRects: List<OcrRect>,
    originalImageSize: IntSize?,
    zoomLevel: Float,
    panOffset: Offset,
    onPanChange: (Offset) -> Unit,
    selectedOcrIndices: Set<Int>,
    onOcrRectClick: (Int) -> Unit,
    isAiProcessed: Boolean,
    isEffectView: Boolean
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(ocrRects, originalImageSize, zoomLevel, panOffset) {
                        detectTapGestures { tapOffset ->
                            if (zoomLevel == 1f) return@detectTapGestures
                            originalImageSize?.let { imgSize ->
                                val canvasWidth = size.width.toFloat()
                                val canvasHeight = size.height.toFloat()
                                val scale = min(canvasWidth / imgSize.width.toFloat(), canvasHeight / imgSize.height.toFloat())
                                val transformedTap = Offset(
                                    (tapOffset.x - panOffset.x - (canvasWidth - imgSize.width * scale * zoomLevel) / 2) / (scale * zoomLevel),
                                    (tapOffset.y - panOffset.y - (canvasHeight - imgSize.height * scale * zoomLevel) / 2) / (scale * zoomLevel)
                                )
                                val clickedIndex = ocrRects.indices.filter { idx ->
                                    val r = ocrRects[idx]
                                    Rect(r.x.toFloat(), r.y.toFloat(), (r.x + r.width).toFloat(), (r.y + r.height).toFloat()).contains(transformedTap)
                                }.minByOrNull { ocrRects[it].width * ocrRects[it].height }
                                clickedIndex?.let { onOcrRectClick(it) }
                            }
                        }
                    }
                    .pointerInput(zoomLevel, panOffset) {
                        detectDragGestures { _, dragAmount ->
                            if (zoomLevel > 1f) onPanChange(panOffset + dragAmount)
                        }
                    }
                    .graphicsLayer { scaleX = zoomLevel; scaleY = zoomLevel; translationX = panOffset.x; translationY = panOffset.y },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

                if (originalImageSize != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = min(size.width / originalImageSize.width, size.height / originalImageSize.height)
                        val offsetX = (size.width - originalImageSize.width * scale) / 2
                        val offsetY = (size.height - originalImageSize.height * scale) / 2

                        ocrRects.forEachIndexed { index, ocrRect ->
                            val isSelected = selectedOcrIndices.contains(index)
                            val left = ocrRect.x * scale + offsetX
                            val top = ocrRect.y * scale + offsetY
                            val rectWidth = ocrRect.width * scale
                            val rectHeight = ocrRect.height * scale

                            // 1. 颜色逻辑：只有被选中的在效果图模式下才变黑
                            val isBlackedOut = isEffectView && isSelected

                            if (isBlackedOut) {
                                // 打码状态：填充黑色
                                drawRect(color = Color.Black, topLeft = Offset(left, top), size = Size(rectWidth, rectHeight))
                            } else {
                                // 未打码状态：显示细边框
                                val boxColor = when {
                                    !isAiProcessed -> Color.Yellow
                                    ocrRect.sensitivity == "high" -> Color.Red
                                    ocrRect.sensitivity == "medium" -> Color.Yellow
                                    ocrRect.sensitivity == "low" -> Color.Green
                                    else -> Color.Yellow
                                }
                                drawRect(
                                    color = boxColor.copy(alpha = 0.8f),
                                    topLeft = Offset(left, top),
                                    size = Size(rectWidth, rectHeight),
                                    style = Stroke(width = 0.5.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}