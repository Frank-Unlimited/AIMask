package com.example.aidama.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.example.aidama.data.model.OcrRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

object MosaicUtils {
    private val ColorPalette: List<Color> = listOf(
        Color(0xFF4A90E2), Color(0xFF50E3C2), Color(0xFFBB86FC), Color(0xFFF5A623),
        Color(0xFFEF5350), Color(0xFF8BC34A), Color(0xFF00BCD4), Color(0xFFFF7043)
    )

    fun getColorForIndex(index: Int): Color {
        val size = ColorPalette.size
        if (size == 0) return Color.Black
        return ColorPalette[((index % size) + size) % size]
    }

    fun getFileNameKey(context: Context, uri: Uri): String {
        var displayName = ""
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) displayName = cursor.getString(0) ?: ""
                }
            } else {
                displayName = uri.lastPathSegment ?: ""
            }
        } catch (e: Exception) { e.printStackTrace() }

        return when {
            displayName.contains("primary4", ignoreCase = true) || displayName.contains("36") -> "primary4"
            displayName.contains("primary3", ignoreCase = true) || displayName.contains("35") -> "primary3"
            displayName.contains("primary2", ignoreCase = true) || displayName.contains("34") -> "primary2"
            displayName.contains("primary1", ignoreCase = true) || displayName.contains("29") || displayName.contains("msf:29") -> "primary1"
            else -> "primary1"
        }
    }

    fun loadJsonFromAssets(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ex: IOException) { null }
    }

    fun parseOcrJson(jsonString: String): Pair<List<OcrRect>, IntSize> {
        val rects = mutableListOf<OcrRect>()
        try {
            val root = JSONObject(jsonString)
            val w: Int; val h: Int
            if (root.has("image_metadata")) {
                val metadata = root.getJSONObject("image_metadata")
                val dimensions = metadata.getJSONObject("dimensions")
                w = dimensions.getInt("width"); h = dimensions.getInt("height")
                val textRegions = root.getJSONArray("text_regions")
                for (i in 0 until textRegions.length()) {
                    val region = textRegions.getJSONObject(i)
                    val vertices = region.getJSONObject("bounding_box").getJSONArray("vertices")
                    val x1 = vertices.getJSONObject(0).getInt("x")
                    val y1 = vertices.getJSONObject(0).getInt("y")
                    val x2 = vertices.getJSONObject(2).getInt("x")
                    val y2 = vertices.getJSONObject(2).getInt("y")
                    val type = region.optString("type", "未分类")
                    val assoc = region.optString("association_id", null)?.takeIf { it.isNotBlank() }
                    rects.add(OcrRect(x1, y1, x2 - x1, y2 - y1, region.getString("text"), region.optString("level", "none"), type, assoc))
                }
            } else {
                w = root.optInt("width", 0); h = root.optInt("height", 0)
                val items = root.optJSONArray("items") ?: return Pair(rects, IntSize(w, h))
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val assocRaw = obj.optString("association_id", "")
                    val assoc = if (assocRaw.isBlank()) null else assocRaw
                    rects.add(OcrRect(obj.optInt("x", 0), obj.optInt("y", 0), obj.optInt("width", 0), obj.optInt("height", 0), obj.optString("text", ""), obj.optString("sensitivity", "none"), obj.optString("type", "未分类"), assoc))
                }
            }
            return Pair(rects, IntSize(w, h))
        } catch (e: Exception) { return Pair(rects, IntSize(0, 0)) }
    }

    fun coercePanOffset(offset: Offset, zoom: Float, size: IntSize): Offset {
        if (zoom <= 1f) return Offset.Zero
        val maxX = (size.width * (zoom - 1)) / 2f
        val maxY = (size.height * (zoom - 1)) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    suspend fun saveImageToGallery(context: Context, imageUri: Uri, ocrRects: List<OcrRect>, selectedIndices: Set<Int>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext false
                inputStream?.close()
                val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(resultBitmap)
                val paint = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
                selectedIndices.forEach { index ->
                    val r = ocrRects[index]
                    canvas.drawRect(r.x.toFloat(), r.y.toFloat(), (r.x + r.width).toFloat(), (r.y + r.height).toFloat(), paint)
                }
                val filename = "AIDama_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AIDama")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}