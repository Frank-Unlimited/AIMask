package com.example.aidama.ui

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
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MosaicUtils {
    private val ColorPalette = listOf(
        Color(0xFF4A90E2), Color(0xFF50E3C2), Color(0xFFBB86FC), Color(0xFFF5A623),
        Color(0xFFEF5350), Color(0xFF8BC34A), Color(0xFF00BCD4), Color(0xFFFF7043)
    )

    fun getColorForIndex(index: Int): Color = ColorPalette[index % ColorPalette.size]

    /**
     * 【核心修复】根据图片真实名称精确匹配 JSON 前缀
     */
    fun getFileNameKey(context: Context, uri: Uri): String {
        var displayName = ""
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        displayName = cursor.getString(0) ?: ""
                    }
                }
            } else {
                displayName = uri.lastPathSegment ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 根据文件名中包含的关键字，强制映射到对应的资源名
        return when {
            // 匹配 primary3 / msf:37
            displayName.contains("primary4", ignoreCase = true) || displayName.contains("36") -> "primary4"
            // 匹配 primary3 / msf:37
            displayName.contains("primary3", ignoreCase = true) || displayName.contains("35") -> "primary3"
            // 匹配 primary2 / 包含 "34"
            displayName.contains("primary2", ignoreCase = true) || displayName.contains("34") -> "primary2"
            // 匹配 primary1 / 包含 "33"
            displayName.contains("primary1", ignoreCase = true) || displayName.contains("33") -> "primary1"
            // 匹配 primary4
            displayName.contains("primary4", ignoreCase = true) -> "primary4"
            // 默认兜底
            else -> "primary1"
        }
    }

    fun loadJsonFromAssets(context: Context, fileName: String): String? {
        return try { context.assets.open(fileName).bufferedReader().use { it.readText() } } catch (ex: IOException) { null }
    }

    /**
     * 调用OCR微服务处理图片
     * @param context Android Context
     * @param imageUri 图片URI
     * @return OCR结果JSON字符串，失败返回null
     */
    suspend fun runLocalOcr(context: Context, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // OCR服务地址（可配置）
            val ocrServiceUrl = "http://192.168.1.100:5000/ocr"  // 修改为实际服务器IP
            
            // 读取图片数据
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val imageBytes = inputStream.readBytes()
            inputStream.close()
            
            // 构建multipart请求
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            val lineEnd = "\r\n"
            val twoHyphens = "--"
            
            val url = java.net.URL(ocrServiceUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            // 写入请求体
            val outputStream = connection.outputStream
            val writer = java.io.DataOutputStream(outputStream)
            
            writer.writeBytes(twoHyphens + boundary + lineEnd)
            writer.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"$lineEnd")
            writer.writeBytes("Content-Type: image/jpeg$lineEnd")
            writer.writeBytes(lineEnd)
            writer.write(imageBytes)
            writer.writeBytes(lineEnd)
            writer.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            writer.flush()
            writer.close()
            
            // 读取响应
            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("MosaicUtils", "OCR service responded successfully")
                response
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                android.util.Log.e("MosaicUtils", "OCR service error: $responseCode - $errorResponse")
                null
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("MosaicUtils", "OCR service timeout: ${e.message}")
            null
        } catch (e: java.net.ConnectException) {
            android.util.Log.e("MosaicUtils", "Cannot connect to OCR service: ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.e("MosaicUtils", "OCR error: ${e.message}", e)
            null
        }
    }

    fun parseOcrJson(jsonString: String): Pair<List<OcrRect>, IntSize> {
        val json = JSONObject(jsonString)
        val dim = json.optJSONObject("image_metadata")?.optJSONObject("dimensions")
        val width = dim?.optInt("width") ?: 1000
        val height = dim?.optInt("height") ?: 2000
        val size = IntSize(width, height)
        val rects = mutableListOf<OcrRect>()
        val regions = json.optJSONArray("text_regions") ?: return rects to size
        for (i in 0 until regions.length()) {
            val reg = regions.getJSONObject(i)
            val v = reg.optJSONObject("bounding_box")?.optJSONArray("vertices") ?: continue
            if (v.length() >= 4) {
                rects.add(OcrRect(
                    x = v.getJSONObject(0).optInt("x"), y = v.getJSONObject(0).optInt("y"),
                    width = v.getJSONObject(1).optInt("x") - v.getJSONObject(0).optInt("x"),
                    height = v.getJSONObject(2).optInt("y") - v.getJSONObject(0).optInt("y"),
                    text = reg.optString("text", ""), sensitivity = reg.optString("level", "none"),
                    type = reg.optString("type", "未分类"), associationId = if (reg.isNull("association_id")) null else reg.optString("association_id")
                ))
            }
        }
        return rects to size
    }

    fun coercePanOffset(offset: Offset, zoom: Float, size: IntSize): Offset {
        if (zoom <= 1f) return Offset.Zero
        val maxX = (size.width * (zoom - 1)) / 2f
        val maxY = (size.height * (zoom - 1)) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    fun saveImageToGallery(context: Context, imageUri: Uri, ocrRects: List<OcrRect>, selectedIndices: Set<Int>): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return false
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
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}