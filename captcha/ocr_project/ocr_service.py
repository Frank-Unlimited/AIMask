import json
import os
import subprocess
import sys
import tempfile
from typing import Any, Dict, List

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI(title="OCR Service")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RUN_OCR = os.path.join(SCRIPT_DIR, "run_ocr.py")


def _to_words_result(result: Dict[str, Any]) -> List[Dict[str, Any]]:
    words_result: List[Dict[str, Any]] = []
    for region in result.get("text_regions", []):
        box = region.get("bounding_box", {})
        vertices = box.get("vertices", [])
        words_result.append(
            {
                "text": region.get("text", ""),
                "confidence": box.get("confidence", 0.0),
                "box_points": [
                    {"x": float(v.get("x", 0)), "y": float(v.get("y", 0))}
                    for v in vertices
                ],
            }
        )
    return words_result


@app.post("/ocr/basic")
async def basic_ocr(file: UploadFile = File(...), languages: str = Form("ch")):
    if not os.path.exists(RUN_OCR):
        raise HTTPException(status_code=500, detail="run_ocr.py not found")

    with tempfile.TemporaryDirectory() as tmp_dir:
        image_path = os.path.join(tmp_dir, file.filename)
        output_path = os.path.join(tmp_dir, "result.json")

        content = await file.read()
        with open(image_path, "wb") as f:
            f.write(content)

        python_exe = os.environ.get("PYTHON_EXE") or sys.executable
        cmd = [
            python_exe,
            RUN_OCR,
            "--image",
            image_path,
            "--output",
            output_path,
            "--languages",
            languages,
        ]

        process = subprocess.run(cmd, capture_output=True, text=True)
        if process.returncode != 0:
            raise HTTPException(
                status_code=500,
                detail=f"OCR failed: {process.stderr or process.stdout}",
            )

        if not os.path.exists(output_path):
            raise HTTPException(status_code=500, detail="OCR result not found")

        with open(output_path, "r", encoding="utf-8") as f:
            result = json.load(f)

        response = {
            "full_text": result.get("full_text", ""),
            "words_result": _to_words_result(result),
        }
        return JSONResponse(content=response)
