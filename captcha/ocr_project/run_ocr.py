import os
import sys
import ctypes
from pathlib import Path

# Add mineru path FIRST
# Get the mineru path from environment or use default
mineru_path = os.getenv('MINERU_PATH', r'f:\captcha\mineru-master')
# Also try relative path if not found
if not os.path.exists(mineru_path):
    # Try relative to this script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    mineru_path = os.path.join(script_dir, '..', 'mineru-master')
    if not os.path.exists(mineru_path):
        # Try workspace root
        mineru_path = os.path.join(script_dir, '..', '..', 'captcha', 'mineru-master')

mineru_path = os.path.abspath(mineru_path)
if mineru_path not in sys.path:
    sys.path.insert(0, mineru_path)

# Fix DLL issues for PyTorch on Windows
try:
    # Attempt to locate site-packages/torch/lib
    # We can infer it from where sys.executable is, or just try relative imports
    # But since we are running via .venv python, we can guess.
    
    # Method 1: Use site packages of current python
    import site
    site_packages = site.getsitepackages()
    
    torch_lib_paths = []
    for sp in site_packages:
        p = Path(sp) / "torch" / "lib"
        if p.exists():
            torch_lib_paths.append(p)
    
    # Also check local venv relative path if not found in site packages
    if not torch_lib_paths:
        venv_path = Path(sys.executable).parent.parent
        p = venv_path / "Lib" / "site-packages" / "torch" / "lib"
        if p.exists():
            torch_lib_paths.append(p)

    for p in torch_lib_paths:
        print(f"Adding DLL directory: {p}")
        os.add_dll_directory(str(p))
        
        # Try loading libiomp5md.dll explicitly
        omp_path = p / "libiomp5md.dll"
        if omp_path.exists():
            print(f"Pre-loading OpenMP: {omp_path}")
            ctypes.CDLL(str(omp_path))
            
except Exception as e:
    print(f"Warning: Failed to setup DLL paths manually: {e}")

import json
import numpy as np
import cv2
import time
import torch


from mineru.model.ocr.paddleocr2pytorch.pytorch_paddle import PytorchPaddleOCR
from mineru.utils.post_char_bbox_converter import PostCharBboxConverter


def create_ocr_model(languages: str, det_db_box_thresh: float = 0.3):
    return PytorchPaddleOCR(lang=languages, det_db_box_thresh=det_db_box_thresh)

def main():
    # 支持命令行参数传入图片路径
    import argparse
    parser = argparse.ArgumentParser(description='Run OCR on an image')
    parser.add_argument('--image', type=str, default=r'f:\captcha\20260122144332_62_269.jpg',
                        help='Path to the image file')
    parser.add_argument('--output', type=str, default=None,
                        help='Path to the output JSON file (default: result.json in script directory)')
    parser.add_argument('--languages', type=str, default='ch',
                        help='OCR languages, e.g. ch, en')
    args = parser.parse_args()
    
    image_path = args.image
    output_path = args.output if args.output else os.path.join(os.path.dirname(__file__), 'result.json')
    
    # Check if image exists
    if not os.path.exists(image_path):
        print(f"Error: Image not found at {image_path}")
        return

    print(f"Processing image: {image_path}")
    
    # 1. Load Image
    # Use numpy fromfile to handle potential unicode paths correctly on Windows, then decode
    img_np = np.fromfile(image_path, dtype=np.uint8)
    img = cv2.imdecode(img_np, cv2.IMREAD_COLOR)
    
    if img is None:
        print("Error: Failed to load image")
        return

    h, w = img.shape[:2]
    
    # 2. Init Model
    # Explicitly set device
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    print(f"Using device: {device}")
    
    # Initialize OCR model with specified languages
    try:
        ocr_model = create_ocr_model(args.languages, det_db_box_thresh=0.3)
    except Exception as e:
        print(f"Error initializing model: {e}")
        import traceback
        traceback.print_exc()
        return
    
    # 3. Predict
    print("Running OCR...")
    start_time = time.time()
    
    # PytorchPaddleOCR instance is callable
    try:
        # returns (dt_boxes, rec_res) via __call__ which runs both det and rec
        ocr_result = ocr_model(img)
        
        # Check structure
        if isinstance(ocr_result, tuple) and len(ocr_result) == 2:
             dt_boxes, rec_res = ocr_result
        else:
             # Fallback or error
             print(f"Unexpected result format: {type(ocr_result)}")
             dt_boxes, rec_res = None, None
                 
    except Exception as e:
        print(f"Error during inference: {e}")
        import traceback
        traceback.print_exc()
        return
    
    processing_time = (time.time() - start_time) * 1000
    print(f"OCR completed in {processing_time:.2f}ms")
    
    # 4. Process results
    text_regions = []
    converter = PostCharBboxConverter()
    
    full_text = ""
    line_number = 1
    total_confidence = 0
    char_count = 0
    
    if dt_boxes is None or rec_res is None:
        print("No text detected.")
        dt_boxes = []
        rec_res = []
        
    # Ensure dt_boxes and rec_res are lists
    if not isinstance(dt_boxes, list): 
        dt_boxes = dt_boxes.tolist() if hasattr(dt_boxes, 'tolist') else []
    if not isinstance(rec_res, list):
         rec_res = rec_res.tolist() if hasattr(rec_res, 'tolist') else []

    for i, (box, rec) in enumerate(zip(dt_boxes, rec_res)):
        text = rec[0]
        score = rec[1]
        
        # box is usually list of 4 points [[x1,y1],...]. 
        # Ensure it's list of lists or similar valid format for processing
        if isinstance(box, np.ndarray):
            box = box.tolist()
            
        # Get bounding box for the line
        x_coords = [p[0] for p in box]
        y_coords = [p[1] for p in box]
        x0, x1 = min(x_coords), max(x_coords)
        y0, y1 = min(y_coords), max(y_coords)
        
        line_bbox = [x0, y0, x1, y1]
        
        # Calculate char bboxes
        # converter.calculate_char_bboxes expects text and [x0,y0,x1,y1]
        try:
            char_bboxes = converter.calculate_char_bboxes(text, line_bbox)
        except Exception as e:
            print(f"Error converting char box for '{text}': {e}")
            char_bboxes = []
            
        # Add entry for each character
        for j, char in enumerate(text):
            if j < len(char_bboxes):
                c_bbox = char_bboxes[j]
                # c_bbox is [x0, y0, x1, y1]
                
                # Create vertices
                vertices = [
                    {"x": float(c_bbox[0]), "y": float(c_bbox[1])},
                    {"x": float(c_bbox[2]), "y": float(c_bbox[1])},
                    {"x": float(c_bbox[2]), "y": float(c_bbox[3])},
                    {"x": float(c_bbox[0]), "y": float(c_bbox[3])}
                ]
                
                region = {
                    "region_id": f"region_{len(text_regions)+1:03d}",
                    "text": char,
                    "bounding_box": {
                        "vertices": vertices,
                        "confidence": float(score) # Using line confidence for char
                    },
                    "attributes": {
                        "line_number": line_number,
                        # Estimate font size from box height
                        "font_size": int(c_bbox[3] - c_bbox[1]),
                        "text_color": "#000000", # Placeholder
                        "background_color": "#FFFFFF" # Placeholder
                    }
                }
                text_regions.append(region)
                
                total_confidence += score
                char_count += 1
                
        full_text += text + "\n"
        line_number += 1
        
    avg_confidence = total_confidence / char_count if char_count > 0 else 0.0
    avg_confidence = float(avg_confidence)

    # Clean full_text
    full_text = full_text.strip()
    
    # Construct final JSON
    result = {
        "request_id": f"req_{int(time.time())}",
        "status": "success",
        "processing_time_ms": int(processing_time),
        "confidence_score": round(avg_confidence, 4), 
        "image_metadata": {
            "dimensions": {"width": w, "height": h},
            "orientation": "portrait" if h >= w else "landscape",
        },
        "text_regions": text_regions,
        "full_text": full_text
    }
    
    # Save to file
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
        
    print(f"Result saved to {output_path}")

if __name__ == '__main__':
    main()
