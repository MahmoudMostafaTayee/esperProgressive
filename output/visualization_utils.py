"""
Visualization utilities for MCPT tracking annotation
"""
import json
import re
import cv2
import numpy as np
from collections import defaultdict
from pathlib import Path


def parse_global_ids(filepath):
    """
    Parse global IDs from MCPT dump file.
    
   Returns:
        dict: {camera_id: {serial: {local_id: global_id}}}
    """
    result = defaultdict(lambda: defaultdict(dict))
    current_camera = None
    
    with open(filepath, 'r') as f:
        for line in f:
            camera_match = re.match(r'Camera (\d+):', line)
            if camera_match:
                current_camera = camera_match.group(1)
                continue
            
            entry_match = re.match(r'\s+(\d+):\s+localId=(\d+)\s*->\s*globalId=(\d+)', line)
            if entry_match and current_camera:
                serial = str(int(entry_match.group(1)))  # Normalize
                local_id = int(entry_match.group(2))
                global_id = int(entry_match.group(3))
                result[current_camera][serial][local_id] = global_id
    
    return result


def load_detection_json(filepath):
    """Load detection JSON with bounding boxes"""
    with open(filepath, 'r') as f:
        return json.load(f)


def get_color_palette(num_colors=50):
    """
    Generate a distinct color palette for global IDs.
    
    Returns:
        list: List of (B, G, R) tuples for OpenCV
    """
    np.random.seed(42)  # Fixed seed for consistency
    colors = []
    
    # Generate distinct colors using HSV and convert to BGR
    for i in range(num_colors):
        hue = int(180 * i / num_colors)
        saturation = 200 + np.random.randint(-50, 50)
        value = 200 + np.random.randint(-50, 50)
        
        # Create 1x1 HSV image and convert to BGR
        hsv = np.uint8([[[hue, saturation, value]]])
        bgr = cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR)
        colors.append(tuple(map(int, bgr[0][0])))
    
    return colors


def draw_bbox_with_label(image, bbox, global_id, local_id, color, thickness=2):
    """
    Draw bounding box with labels on image.
    
    Args:
        image: OpenCV image (BGR)
        bbox: (x1, y1, x2, y2) coordinates
        global_id: Global tracking ID
        local_id: Camera-local ID  
        color: (B, G, R) tuple
        thickness: Line thickness
    """
    x1, y1, x2, y2 = bbox
    
    # Draw rectangle
    cv2.rectangle(image, (x1, y1), (x2, y2), color, thickness)
    
    # Prepare label
    label = f"G:{global_id} L:{local_id}"
    
    # Get text size for background
    font = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 0.6
    label_size = cv2.getTextSize(label, font, font_scale, thickness)[0]
    
    # Draw label background
    label_ymin = max(y1 - label_size[1] - 10, 0)
    cv2.rectangle(
        image,
        (x1, label_ymin),
        (x1 + label_size[0] + 10, y1),
        color,
        -1  # Filled
    )
    
    # Draw text
    cv2.putText(
        image,
        label,
        (x1 + 5, y1 - 5),
        font,
        font_scale,
        (255, 255, 255),  # White text
        thickness-1
    )


def create_frame_overlay(image, frame_num, camera_id, impl_type, tracked_count):
    """
    Add metadata overlay to frame.
    
    Args:
        image: OpenCV image
        frame_num: Frame number
        camera_id: Camera ID
        impl_type: "Java" or "Python"
        tracked_count: Number of tracked persons in frame
    """
    overlay = image.copy()
    height, width = image.shape[:2]
    
    # Semi-transparent black bar at top
    cv2.rectangle(overlay, (0, 0), (width, 60), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.5, image, 0.5, 0, image)
    
    # Text overlay
    font = cv2.FONT_HERSHEY_SIMPLEX
    text1 = f"Camera {camera_id} | Frame {frame_num:05d} | {impl_type}"
    text2 = f"Tracked: {tracked_count} persons"
    
    cv2.putText(image, text1, (10, 25), font, 0.7, (255, 255, 255), 2)
    cv2.putText(image, text2, (10, 50), font, 0.6, (200, 200, 200), 1)


def build_serial_to_frame_map(detections):
    """
    Build mapping from serial (detection ID) to frame info.
    
    Returns:
        dict: {serial: {frame: detection_data}}
    """
    serial_map = defaultdict(dict)
    
    for serial, det_data in detections.items():
        frame_num = det_data["Frame"]
        serial_map[serial][frame_num] = det_data
    
    return serial_map


def get_frame_detections(detections, frame_num):
    """Get all detections for a specific frame"""
    frame_dets = {}
    for serial, det_data in detections.items():
        if det_data["Frame"] == frame_num:
            frame_dets[serial] = det_data
    return frame_dets
