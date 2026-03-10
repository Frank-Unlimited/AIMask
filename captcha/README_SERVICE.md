# OCR Service Package (captcha)

## Overview
This package exposes the OCR engine as a microservice.

- Endpoint: POST /ocr/basic
- Content-Type: multipart/form-data
- Fields:
  - file: image binary
  - languages: string (default: ch)

## Quick Start (Windows PowerShell)
```powershell
Set-Location F:\AutoMark\AI-dama\captcha
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn ocr_project.ocr_service:app --host 0.0.0.0 --port 8000
```

## Quick Start (macOS/Linux)
```bash
cd /path/to/captcha
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python -m uvicorn ocr_project.ocr_service:app --host 0.0.0.0 --port 8000
```

## Test Request
```powershell
$img = "F:\AutoMark\AI-dama\captcha\20260122144332_62_269.jpg"
Invoke-RestMethod -Uri http://127.0.0.1:8000/ocr/basic -Method Post -Form @{ file = Get-Item $img; languages = "ch" }
```

## Response Format
```json
{
  "full_text": "string",
  "words_result": [
    {
      "text": "string",
      "confidence": 0.0,
      "box_points": [
        {"x": 0.0, "y": 0.0},
        {"x": 0.0, "y": 0.0},
        {"x": 0.0, "y": 0.0},
        {"x": 0.0, "y": 0.0}
      ]
    }
  ]
}
```

## Environment Variables (Optional)
- MINERU_PATH: override mineru-master path
- PYTHON_EXE: override Python executable path

## Notes
- The service uses a temporary folder per request. Temporary files are deleted after the response is returned.
