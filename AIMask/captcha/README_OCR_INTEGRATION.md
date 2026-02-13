# Android应用本地OCR集成说明

## 概述
本Android打码应用已成功集成本地OCR功能，使用`captcha/ocr_project/run_ocr.py`脚本进行文字识别。

## 架构设计

### 1. OCR调用流程
```
Android App (MosaicViewModel.kt)
    ↓ 添加图片
MosaicUtils.runLocalOcr()
    ↓ 复制图片到临时文件
    ↓ 执行Python脚本
captcha/ocr_project/run_ocr.py
    ↓ 使用mineru OCR模型
    ↓ 生成result.json
    ↓ 返回JSON结果
MosaicUtils.parseOcrJson()
    ↓ 解析OCR结果
应用显示OCR区域
```

### 2. 关键文件修改

#### app/src/main/java/com/example/aidama/ui/MosaicUtils.kt
- **新增函数**: `runLocalOcr(context: Context, imageUri: Uri): String?`
  - 复制图片到临时文件
  - 使用ProcessBuilder调用Python脚本
  - 传递图片路径作为命令行参数
  - 读取并返回result.json内容
  - 异常处理和日志记录

#### app/src/main/java/com/example/aidama/ui/MosaicViewModel.kt
- **修改函数**: `addImages(context: Context, uris: List<Uri>)`
  - 优先调用本地OCR: `MosaicUtils.runLocalOcr()`
  - 失败时回退到assets中的预生成JSON
  - 双重异常处理确保稳定性

#### captcha/ocr_project/run_ocr.py
- **新增参数**:
  - `--image`: 指定待处理图片路径
  - `--output`: 指定输出JSON路径（可选）
- **默认行为**: 无参数时使用原硬编码路径

#### app/src/main/AndroidManifest.xml
- **新增权限**:
  - `READ_EXTERNAL_STORAGE`: 读取图片
  - `WRITE_EXTERNAL_STORAGE`: 写入临时文件
  - `INTERNET`: 备用网络功能

## 使用方法

### 环境要求
1. **Python环境**: captcha/.venv已配置mineru OCR模型
2. **Python可执行文件**: `F:\AutoMark\AI-dama\captcha\.venv\Scripts\python.exe`
3. **OCR脚本**: `F:\AutoMark\AI-dama\captcha\ocr_project\run_ocr.py`

### Android应用使用
1. 启动应用
2. 选择图片（通过相册或其他方式）
3. 应用自动调用本地OCR
4. 识别结果显示在界面上
5. 可以对识别区域进行打码操作

### 手动测试OCR脚本
```bash
cd F:\AutoMark\AI-dama\captcha
.venv\Scripts\python.exe ocr_project\run_ocr.py --image "path\to\image.jpg"
```

## JSON格式兼容性

### OCR输出格式（result.json）
```json
{
  "request_id": "req_1769068424",
  "status": "success",
  "processing_time_ms": 4000,
  "confidence_score": 0.9861,
  "image_metadata": {
    "dimensions": {"width": 1080, "height": 2941},
    "orientation": "portrait"
  },
  "text_regions": [
    {
      "region_id": "region_001",
      "text": "文字",
      "bounding_box": {
        "vertices": [
          {"x": 49.0, "y": 28.0},
          {"x": 115.3, "y": 28.0},
          {"x": 115.3, "y": 86.0},
          {"x": 49.0, "y": 86.0}
        ],
        "confidence": 0.9948
      },
      "attributes": {
        "line_number": 1,
        "font_size": 58,
        "text_color": "#000000",
        "background_color": "#FFFFFF"
      }
    }
  ],
  "full_text": "完整文本内容"
}
```

### 应用解析逻辑（parseOcrJson）
- 提取`image_metadata.dimensions`获取图片尺寸
- 遍历`text_regions`数组
- 解析每个区域的`bounding_box.vertices`（4个顶点坐标）
- 提取`text`, `level`（敏感度）, `type`（类型）, `association_id`

## 错误处理机制

### 1. 本地OCR失败时的回退策略
```kotlin
try {
    val json = MosaicUtils.runLocalOcr(context, uri)
    if (json != null) {
        // 使用本地OCR结果
    } else {
        // 回退到assets中的JSON
        val key = MosaicUtils.getFileNameKey(context, uri)
        MosaicUtils.loadJsonFromAssets(context, "${key}_ai.json")
    }
} catch (e: Exception) {
    // 异常处理：再次尝试回退
}
```

### 2. 日志记录
- **成功**: `Log.d("MosaicUtils", "OCR completed successfully")`
- **失败**: `Log.e("MosaicUtils", "OCR error: ${e.message}", e)`
- **回退**: `Log.w("MosaicViewModel", "Local OCR failed, falling back to assets")`

### 3. 常见问题

#### Python可执行文件未找到
- **错误**: `Python executable not found`
- **解决**: 检查`.venv`是否正确创建，路径是否正确

#### OCR脚本未找到
- **错误**: `OCR script not found`
- **解决**: 确认`captcha/ocr_project/run_ocr.py`存在

#### result.json未生成
- **错误**: `Result file not found`
- **解决**: 
  - 检查Python脚本执行日志
  - 确认mineru模型已正确安装
  - 检查图片路径是否包含特殊字符

#### 进程超时
- **现象**: 等待时间过长
- **解决**: 
  - 检查OCR模型是否首次加载（首次较慢）
  - 增加超时时间
  - 检查图片大小（过大图片处理慢）

## 性能优化建议

### 1. 缓存策略
- 已实现：`ocrCacheMap[uri]`缓存OCR结果
- 相同图片无需重复识别

### 2. 异步处理
- 使用`viewModelScope.launch`协程
- `runLocalOcr`使用`Dispatchers.IO`
- 不阻塞主线程

### 3. 临时文件清理
- 自动删除`temp_ocr_*.jpg`临时文件
- 避免缓存目录积累

## 未来改进方向

1. **配置化路径**: 将captcha路径写入配置文件
2. **进度回调**: 添加OCR处理进度通知
3. **批量处理**: 支持多图片并行OCR
4. **模型切换**: 支持选择不同OCR模型（中文/英文/多语言）
5. **离线包**: 将Python环境打包到APK（可选）

## 测试清单

- [ ] 单张图片OCR识别
- [ ] 多张图片批量识别
- [ ] 本地OCR失败回退到assets
- [ ] 异常情况处理
- [ ] 临时文件清理
- [ ] 日志输出正常
- [ ] 性能测试（识别速度）
- [ ] 内存占用监控

## 技术栈
- **Android**: Kotlin + Jetpack Compose
- **OCR引擎**: mineru (PytorchPaddle OCR)
- **Python**: 3.x with .venv虚拟环境
- **进程通信**: ProcessBuilder
- **协程**: Kotlin Coroutines (Dispatchers.IO)
