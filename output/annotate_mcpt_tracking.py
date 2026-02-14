"""
Annotate MCPT tracking results on video frames.

Usage:
    python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl ours
    python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl theirs --frames 1-500
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


def load_global_ids_json(filepath):
    """
    Load global IDs from JSON dump.
    Returns: dict {camera_id: {serial: global_id}}
    """
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
            
        global_ids_map = {} # Structure: {camera_id: {serial: global_id}}
        
        # Data is frame_num -> list of objects
        for frame_num, entries in data.items():
            for entry in entries:
                camera_id = str(entry.get('camera'))
                if camera_id not in global_ids_map:
                    global_ids_map[camera_id] = {}
                
                # Normalize serial
                serial_raw = entry.get('serial')
                try:
                    serial = str(int(str(serial_raw)))
                except ValueError:
                    serial = str(serial_raw)
                    
                global_id = entry.get('globalId')
                global_ids_map[camera_id][serial] = global_id
                
        return global_ids_map
    except Exception as e:
        print(f"Error loading JSON {filepath}: {e}")
        return {}

def get_frames_from_json(filepath, camera_id):
    """Get list of frames present in the JSON dump for a specific camera"""
    frames = set()
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
            
        target_cam = str(camera_id)
        for frame_num, entries in data.items():
            # Check if this frame has entries for our camera
            for entry in entries:
                if str(entry.get('camera')) == target_cam:
                    frames.add(int(frame_num))
                    break
    except Exception as e:
        print(f"Error reading frames from JSON: {e}")
    return sorted(list(frames))

def annotate_frames(args):
    """Main annotation function"""
    
    # Setup paths
    scene_dir = Path(args.base_dir) / args.scene
    camera_str = f"camera_{args.camera:04d}"
    
    detection_json = scene_dir / f"{camera_str}.json"
    frame_dir = scene_dir / camera_str / "Frame"
    
    # MCPT dumps directory and file selection
    if args.impl == "ours":
        json_dump = Path(args.mcpt_dumps) / "mcpt-global-ids_0.json"
        txt_dump = Path(args.mcpt_dumps) / "mcpt-global-ids_0.txt"
    else:  # theirs
        json_dump = Path(args.mcpt_dumps) / "mcpt-global-ids-python.json"
        txt_dump = Path(args.mcpt_dumps) / "mcpt-global-ids-python.txt"
    
    # Output directory
    output_dir = Path(args.output_dir) / args.scene / f"{camera_str}_{args.impl}"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Loading detections from: {detection_json}")
    print(f"Frame directory: {frame_dir}")
    print(f"Output directory: {output_dir}")
    
    # Load data
    detections = load_detection_json(detection_json)
    
    # Load Global IDs
    global_ids = {}
    frames_to_process = []
    
    if json_dump.exists():
        print(f"Loading global IDs from JSON: {json_dump}")
        global_ids = load_global_ids_json(json_dump)
        # Get frames strictly from JSON
        frames_to_process = get_frames_from_json(json_dump, args.camera)
        print(f"Found {len(frames_to_process)} frames in JSON dump")
    elif txt_dump.exists():
        print(f"Loading global IDs from Text (Legacy): {txt_dump}")
        global_ids = parse_global_ids(txt_dump)
        # For text dump, we check keys if possible, or fallback to all frames
        camera_id = str(args.camera)
        if camera_id in global_ids:
             # In text dump, keys are serials. We need to map serial -> frame from detections
             # This is complex, so for legacy we might just process all frames or infer from serials
             # But user specifically asked for JSON focus.
             pass
        # Fallback for text: process all frames
        frames_to_process = sorted(set(d['Frame'] for d in detections.values()))
    else:
        print(f"Error: No dump file found! Checked {json_dump} and {txt_dump}")
        return None

    # Get color palette
    colors = get_color_palette(50)
    
    # Get camera ID for global_ids lookup
    camera_id = str(args.camera)
    camera_global_ids = global_ids.get(camera_id, {})
    
    if not frames_to_process:
        print("No frames to process!")
        return None
    
    print(f"Processing {len(frames_to_process)} frames (from {min(frames_to_process)} to {max(frames_to_process)})")
    
    # Process each frame
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
            try:
                normalized_serial = str(int(det_serial))
            except:
                normalized_serial = str(det_serial)
            
            # Get global ID mapping for this serial
            global_id = camera_global_ids.get(normalized_serial, -1)
            
            # Draw bounding box
            if global_id != -1 and global_id is not None:
                # Tracked person - use colored box
                try:
                    gid_int = int(global_id)
                    color = colors[gid_int % len(colors)]
                except:
                    color = colors[0]
                tracked_count += 1
                offline_id = det_data.get("OfflineID", -1)
            else:
                # Untracked detection - use gray
                color = (128, 128, 128)
                offline_id = -1
                global_id = -1
            
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
    
    print(f"Annotation complete! Frames saved to: {output_dir}")
    return output_dir


def main():
    parser = argparse.ArgumentParser(description="Annotate MCPT tracking on video frames")
    parser.add_argument("--scene", type=str, default="scene_001", help="Scene ID (e.g., scene_001)")
    parser.add_argument("--camera", type=int, default=1, help="Camera number (1-4)")
    parser.add_argument("--impl", type=str, choices=["ours", "theirs"], required=True,
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
                        default="C:/OURs/Thesis/Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing/Visualizations",
                        help="Output directory for annotated frames")
    
    args = parser.parse_args()
    annotate_frames(args)


if __name__ == "__main__":
    main()
