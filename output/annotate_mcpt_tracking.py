"""
Annotate MCPT tracking results on video frames.

Usage:
    python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl java
    python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl python --frames 1-500
"""
import argparse
import cv2
import json
from pathlib import Path
from tqdm import tqdm
from visualization_utils import (
    parse_global_ids, load_detection_json, get_color_palette,
    draw_bbox_with_label, create_frame_overlay, get_frame_detections
)


def annotate_frames(args):
    """Main annotation function"""
    
    # Setup paths
    scene_dir = Path(args.base_dir) / args.scene
    camera_str = f"camera_{args.camera:04d}"
    
    detection_json = scene_dir / f"{camera_str}.json"
    frame_dir = scene_dir / camera_str / "Frame"
    
    # MCPT dumps directory
    if args.impl == "java":
        global_ids_file = Path(args.mcpt_dumps) / "mcpt-global-ids_0.txt"
    else:  # python
        global_ids_file = Path(args.mcpt_dumps) / "mcpt-global-ids-python.txt"
    
    # Output directory
    output_dir = Path(args.output_dir) / args.scene / f"{camera_str}_{args.impl}"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Loading detections from: {detection_json}")
    print(f"Loading global IDs from: {global_ids_file}")
    print(f"Frame directory: {frame_dir}")
    print(f"Output directory: {output_dir}")
    
    # Load data
    detections = load_detection_json(detection_json)
    global_ids = parse_global_ids(global_ids_file)
    
    # Get color palette
    colors = get_color_palette(50)
    
    # Get camera ID for global_ids lookup
    camera_id = str(args.camera)
    camera_global_ids = global_ids.get(camera_id, {})
    
    # Get the set of serials (frame numbers) that were actually streamed
    # These are the frames that appear in the global IDs dump
    streamed_serials = set(camera_global_ids.keys())
    print(f"Found {len(streamed_serials)} streamed frames in global IDs dump")
    
    # Convert serials to frame numbers (they're already frame numbers/serials)
    streamed_frames = sorted(int(s) for s in streamed_serials)
    
    # Parse frame range if provided, otherwise use all streamed frames
    if args.frames:
        start, end = map(int, args.frames.split('-'))
        # Filter to only streamed frames within range
        frames_to_process = [f for f in streamed_frames if start <= f <= end]
    else:
        # Process all streamed frames
        frames_to_process = streamed_frames
    
    if not frames_to_process:
        print("No frames to process!")
        return None
    
    print(f"Processing {len(frames_to_process)} streamed frames (from {min(frames_to_process)} to {max(frames_to_process)})")
    
    # Process each streamed frame
    for frame_num in tqdm(frames_to_process, desc="Annotating frames"):
        # Load frame image
        frame_path = frame_dir / f"{frame_num:06d}.jpg"
        if not frame_path.exists():
            continue
        
        image = cv2.imread(str(frame_path))
        if image is None:
            print(f"Warning: Failed to load {frame_path}")
            continue
        
        # Get detections for this frame
        frame_dets = get_frame_detections(detections, frame_num)
        
        # Track count for overlay
        tracked_count = 0
        
        # Draw each detection
        for det_serial, det_data in frame_dets.items():
            bbox = det_data["Coordinate"]
            x1, y1, x2, y2 = bbox["x1"], bbox["y1"], bbox["x2"], bbox["y2"]
            
            # Normalize detection serial (remove leading zeros)
            # Detection JSON has "00000000", global IDs has "0"
            normalized_serial = str(int(det_serial))
            
            # Get global ID mapping for this serial
            serial_global_map = camera_global_ids.get(normalized_serial, {})
            
            # Get OfflineID (local tracking ID) if available
            offline_id = det_data.get("OfflineID")
            
            # Try to get global ID
            global_id = -1
            if serial_global_map and offline_id is not None and offline_id >= 0:
                # Look up global_id using local_id (offline_id)
                global_id = serial_global_map.get(offline_id, -1)
            elif serial_global_map:
                # If we have a mapping but no offline_id, get the first global_id
                local_ids = sorted(serial_global_map.keys())
                if local_ids:
                    global_id = serial_global_map[local_ids[0]]
                    offline_id = local_ids[0]
            
            # Draw bounding box
            if global_id >= 0:
                # Tracked person - use colored box
                color = colors[global_id % len(colors)]
                tracked_count += 1
            else:
                # Untracked detection - use gray
                color = (128, 128, 128)
                offline_id = -1  # Display -1 for untracked
            
            # Always draw the box (tracked or not)
            draw_bbox_with_label(image, (x1, y1, x2, y2), global_id, offline_id, color)
        
        # Add frame overlay
        create_frame_overlay(
            image, frame_num, args.camera,
            args.impl.capitalize(), tracked_count
        )
        
        # Save annotated frame
        output_path = output_dir / f"frame_{frame_num:06d}.jpg"
        cv2.imwrite(str(output_path), image, [cv2.IMWRITE_JPEG_QUALITY, 95])
    
    print(f"✓ Annotation complete! Frames saved to: {output_dir}")
    return output_dir


def main():
    parser = argparse.ArgumentParser(description="Annotate MCPT tracking on video frames")
    parser.add_argument("--scene", type=str, default="scene_001", help="Scene ID (e.g., scene_001)")
    parser.add_argument("--camera", type=int, default=1, help="Camera number (1-4)")
    parser.add_argument("--impl", type=str, choices=["java", "python"], required=True,
                        help="Implementation type")
    parser.add_argument("--frames", type=str, default=None,
                        help="Frame range to process (e.g., '1-500'), default: all")
    parser.add_argument("--base-dir", type=str,
                        default="C:/OURs/Thesis/Original",
                        help="Base directory for scenes")
    parser.add_argument("--mcpt-dumps", type=str,
                        default="C:/OURs/Thesis/Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing/output/scene1/mcpt-dumps",
                        help="Directory containing MCPT dumps")
    parser.add_argument("--output-dir", type=str,
                        default="C:/OURs/Thesis/Visualizations",
                        help="Output directory for annotated frames")
    
    args = parser.parse_args()
    annotate_frames(args)


if __name__ == "__main__":
    main()
