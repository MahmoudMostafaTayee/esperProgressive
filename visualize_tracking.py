#!/usr/bin/env python3
"""
Tracking Visualization Script
Draws bounding boxes with track IDs on video frames based on JSON tracking results.
"""

import json
import cv2
import numpy as np
import os
import argparse
from pathlib import Path
from typing import Dict, List, Tuple

# Color palette for track IDs (BGR format for OpenCV)
COLORS = [
    (255, 0, 0),      # Blue
    (0, 255, 0),      # Green
    (0, 0, 255),      # Red
    (255, 255, 0),    # Cyan
    (255, 0, 255),    # Magenta
    (0, 255, 255),    # Yellow
    (128, 0, 128),    # Purple
    (255, 128, 0),    # Orange
    (0, 128, 255),    # Light Blue
    (128, 255, 0),    # Lime
]

def get_color_for_track_id(track_id: int) -> Tuple[int, int, int]:
    """Get a consistent color for a given track ID."""
    return COLORS[track_id % len(COLORS)]

def draw_tracking_results(
    frame: np.ndarray,
    detections: List[Dict],
    show_id: bool = True,
    thickness: int = 2
) -> np.ndarray:
    """
    Draw bounding boxes and track IDs on a frame.
    
    Args:
        frame: Input image (BGR format)
        detections: List of detections with 'track_id' and 'bbox' [x1, x2, y1, y2]
        show_id: Whether to show track ID text
        thickness: Bounding box line thickness
    
    Returns:
        Annotated frame
    """
    annotated = frame.copy()
    
    for det in detections:
        track_id = det['track_id']
        bbox = det['bbox']  # [x1, x2, y1, y2]
        
        x1, x2, y1, y2 = bbox
        color = get_color_for_track_id(track_id)
        
        # Draw bounding box
        cv2.rectangle(annotated, (x1, y1), (x2, y2), color, thickness)
        
        if show_id:
            # Draw track ID label
            label = f"ID: {track_id}"
            font = cv2.FONT_HERSHEY_SIMPLEX
            font_scale = 0.6
            font_thickness = 2
            
            # Get text size for background
            (text_width, text_height), baseline = cv2.getTextSize(
                label, font, font_scale, font_thickness
            )
            
            # Draw background rectangle for text
            cv2.rectangle(
                annotated,
                (x1, y1 - text_height - 10),
                (x1 + text_width + 10, y1),
                color,
                -1  # Filled
            )
            
            # Draw text
            cv2.putText(
                annotated,
                label,
                (x1 + 5, y1 - 5),
                font,
                font_scale,
                (255, 255, 255),  # White text
                font_thickness
            )
    
    return annotated

def visualize_tracking(
    json_dir: str,
    frames_dir: str,
    output_dir: str,
    camera_id: str = "camera_0001",
    create_video: bool = False,
    fps: int = 30
):
    """
    Visualize tracking results on video frames.
    
    Args:
        json_dir: Directory containing JSON tracking results
        frames_dir: Base directory containing camera frames (e.g., Original/scene_001)
        output_dir: Output directory for annotated frames
        camera_id: Camera ID (e.g., "camera_0001")
        create_video: Whether to create a video from annotated frames
        fps: Frames per second for output video
    """
    # Setup paths
    json_path = Path(json_dir)
    frames_path = Path(frames_dir) / camera_id / "Frame"
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"Reading JSON files from: {json_path}")
    print(f"Reading frames from: {frames_path}")
    print(f"Saving annotated frames to: {output_path}")
    
    # Get all JSON files
    json_files = sorted(json_path.glob("frame_*.json"))
    
    if not json_files:
        print(f"No JSON files found in {json_path}")
        return
    
    print(f"Found {len(json_files)} JSON files")
    
    # Process each frame
    video_writer = None
    frame_size = None
    
    for json_file in json_files:
        # Load JSON
        with open(json_file, 'r') as f:
            data = json.load(f)
        
        frame_num = data['frame']
        detections = data['detections']
        
        # Load corresponding frame image
        frame_filename = f"{frame_num:06d}.jpg"
        frame_path = frames_path / frame_filename
        
        if not frame_path.exists():
            print(f"Warning: Frame {frame_filename} not found, skipping...")
            continue
        
        frame = cv2.imread(str(frame_path))
        if frame is None:
            print(f"Warning: Could not read {frame_path}, skipping...")
            continue
        
        # Draw tracking results
        annotated = draw_tracking_results(frame, detections)
        
        # Save annotated frame
        output_filename = output_path / f"tracked_{frame_num:06d}.jpg"
        cv2.imwrite(str(output_filename), annotated)
        
        # Initialize video writer if needed
        if create_video and video_writer is None:
            frame_size = (frame.shape[1], frame.shape[0])
            video_path = output_path / f"tracking_{camera_id}.mp4"
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            video_writer = cv2.VideoWriter(
                str(video_path), fourcc, fps, frame_size
            )
            print(f"Creating video: {video_path}")
        
        # Write to video
        if video_writer is not None:
            video_writer.write(annotated)
        
        if (len(json_files) < 50) or (len(json_files) % 10 == 0):
            print(f"Processed frame {frame_num}")
    
    # Release video writer
    if video_writer is not None:
        video_writer.release()
        print(f"Video saved successfully")
    
    print(f"\nVisualization complete! Annotated frames saved to: {output_path}")

def main():
    parser = argparse.ArgumentParser(
        description="Visualize tracking results on video frames"
    )
    parser.add_argument(
        "--json-dir",
        type=str,
        required=True,
        help="Directory containing JSON tracking results"
    )
    parser.add_argument(
        "--frames-dir",
        type=str,
        required=True,
        help="Base directory containing camera frames (e.g., Original/scene_001)"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="./visualized_tracking",
        help="Output directory for annotated frames"
    )
    parser.add_argument(
        "--camera-id",
        type=str,
        default="camera_0001",
        help="Camera ID (e.g., camera_0001)"
    )
    parser.add_argument(
        "--create-video",
        action="store_true",
        help="Create video from annotated frames"
    )
    parser.add_argument(
        "--fps",
        type=int,
        default=30,
        help="Frames per second for output video"
    )
    
    args = parser.parse_args()
    
    visualize_tracking(
        json_dir=args.json_dir,
        frames_dir=args.frames_dir,
        output_dir=args.output_dir,
        camera_id=args.camera_id,
        create_video=args.create_video,
        fps=args.fps
    )

if __name__ == "__main__":
    main()
