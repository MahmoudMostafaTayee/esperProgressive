"""
Generate videos from annotated frames.

Usage:
    python generate_videos.py --scene scene_001 --camera 1 --generate-comparison
"""
import argparse
import cv2
import numpy as np
from pathlib import Path
from tqdm import tqdm


def create_video_from_frames(frame_dir, output_video, fps=30):
    """Create video from directory of frames"""
    frame_files = sorted(frame_dir.glob("frame_*.jpg"))
    
    if not frame_files:
        print(f"No frames found in {frame_dir}")
        return False
    
    # Get frame dimensions
    first_frame = cv2.imread(str(frame_files[0]))
    height, width = first_frame.shape[:2]
    
    # Create video writer
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(str(output_video), fourcc, fps, (width, height))
    
    print(f"Creating video: {output_video.name}")
    for frame_path in tqdm(frame_files, desc="Writing frames"):
        frame = cv2.imread(str(frame_path))
        if frame is not None:
            out.write(frame)
    
    out.release()
    print(f"Video created: {output_video}")
    return True


def create_sidebyside_video(ours_dir, theirs_dir, output_video, fps=30):
    """Create side-by-side comparison video"""
    ours_frames = sorted(ours_dir.glob("frame_*.jpg"))
    theirs_frames = sorted(theirs_dir.glob("frame_*.jpg"))
    
    if not ours_frames or not theirs_frames:
        print("Missing frames for comparison")
        return False
    
    # Match frames by number
    ours_dict = {f.stem.split('_')[1]: f for f in ours_frames}
    theirs_dict = {f.stem.split('_')[1]: f for f in theirs_frames}
    
    common_frames = sorted(set(ours_dict.keys()) & set(theirs_dict.keys()))
    
    if not common_frames:
        print("No matching frames found")
        return False
    
    # Get dimensions
    first_ours = cv2.imread(str(ours_dict[common_frames[0]]))
    height, width = first_ours.shape[:2]
    
    # Create video writer (double width for side-by-side)
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(str(output_video), fourcc, fps, (width * 2, height))
    
    print(f"Creating comparison video: {output_video.name}")
    for frame_num in tqdm(common_frames, desc="Combining frames"):
        ours_frame = cv2.imread(str(ours_dict[frame_num]))
        theirs_frame = cv2.imread(str(theirs_dict[frame_num]))
        
        if ours_frame is not None and theirs_frame is not None:
            # Concatenate horizontally
            combined = np.hstack([ours_frame, theirs_frame])
            out.write(combined)
    
    out.release()
    print(f"Comparison video created: {output_video}")
    return True


def create_grid_video(cam_dirs, output_video, fps=30):
    """Create a grid video from multiple camera directories"""
    cam_frames = []
    for d in cam_dirs:
        cam_frames.append(sorted(d.glob("frame_*.jpg")))
    
    if not all(cam_frames) or not cam_frames:
        print("Missing frames for grid video")
        return False
    
    # Match frames by number
    cam_dicts = []
    for frames in cam_frames:
        cam_dicts.append({f.stem.split('_')[1]: f for f in frames})
    
    common_frames = set(cam_dicts[0].keys())
    for d in cam_dicts[1:]:
        common_frames &= set(d.keys())
    
    common_frames = sorted(list(common_frames))
    
    if not common_frames:
        print("No matching frames found across all cameras")
        return False
    
    # Get dimensions of first frame
    first_frame = cv2.imread(str(cam_dicts[0][common_frames[0]]))
    h, w = first_frame.shape[:2]
    
    # Determine grid size
    num_cams = len(cam_dirs)
    if num_cams <= 2:
        grid_rows, grid_cols = 1, num_cams
    elif num_cams <= 4:
        grid_rows, grid_cols = 2, 2
    else:
        grid_rows = int(np.ceil(np.sqrt(num_cams)))
        grid_cols = int(np.ceil(num_cams / grid_rows))
    
    # Create video writer
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(str(output_video), fourcc, fps, (w * grid_cols, h * grid_rows))
    
    print(f"Creating multi-camera grid video: {output_video.name} ({num_cams} cameras)")
    for frame_num in tqdm(common_frames, desc="Generating grid"):
        rows = []
        for r in range(grid_rows):
            cols = []
            for c in range(grid_cols):
                cam_idx = r * grid_cols + c
                if cam_idx < num_cams:
                    img = cv2.imread(str(cam_dicts[cam_idx][frame_num]))
                    if img is None:
                        img = np.zeros((h, w, 3), dtype=np.uint8)
                    elif img.shape[:2] != (h, w):
                        img = cv2.resize(img, (w, h))
                else:
                    img = np.zeros((h, w, 3), dtype=np.uint8)
                cols.append(img)
            rows.append(np.hstack(cols))
        
        combined = np.vstack(rows)
        out.write(combined)
    
    out.release()
    print(f"Grid video created: {output_video}")
    return True


def main():
    parser = argparse.ArgumentParser(description="Generate videos from annotated frames")
    parser.add_argument("--scene", type=str, default="scene_001", help="Scene ID")
    parser.add_argument("--camera", type=int, default=1, help="Camera number (for single-cam modes)")
    parser.add_argument("--base-dir", type=str,
                        default="C:/OURs/Thesis/Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing/Visualizations",
                        help="Base directory with annotated frames")
    parser.add_argument("--output-dir", type=str,
                        default="C:/OURs/Thesis/Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing/Visualizations",
                        help="Output directory for videos")
    parser.add_argument("--fps", type=int, default=30, help="Frames per second")
    parser.add_argument("--generate-comparison", action="store_true",
                        help="Generate side-by-side comparison video")
    parser.add_argument("--generate-multi-cam", action="store_true",
                        help="Generate grid video of all cameras (Ours)")
    parser.add_argument("--ours-only", action="store_true",
                        help="Generate only 'Ours' video")
    parser.add_argument("--theirs-only", action="store_true",
                        help="Generate only 'Theirs' video")
    
    args = parser.parse_args()
    
    # Setup paths
    base_dir = Path(args.base_dir) / args.scene
    camera_str = f"camera_{args.camera:04d}"
    
    ours_frame_dir = base_dir / f"{camera_str}_ours"
    theirs_frame_dir = base_dir / f"{camera_str}_theirs"
    
    output_dir = Path(args.output_dir) / args.scene
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate multi-cam grid video
    if args.generate_multi_cam:
        impl_type = "theirs" if args.theirs_only else "ours"
        suffix = f"_{impl_type}"
        matched_dirs = sorted(list(base_dir.glob(f"camera_*{suffix}")))
        
        if matched_dirs:
            grid_video = output_dir / f"grid_{impl_type}_{len(matched_dirs)}cam.mp4"
            create_grid_video(matched_dirs, grid_video, args.fps)
        else:
            print(f"No '{impl_type}' frame directories found in {base_dir}")
        return

    # Generate videos
    if not args.theirs_only:
        if ours_frame_dir.exists():
            ours_video = output_dir / f"{camera_str}_ours.mp4"
            create_video_from_frames(ours_frame_dir, ours_video, args.fps)
        else:
            print(f"Ours frames not found: {ours_frame_dir}")
    
    if not args.ours_only:
        if theirs_frame_dir.exists():
            theirs_video = output_dir / f"{camera_str}_theirs.mp4"
            create_video_from_frames(theirs_frame_dir, theirs_video, args.fps)
        else:
            print(f"Theirs frames not found: {theirs_frame_dir}")
    
    # Generate comparison video
    if args.generate_comparison:
        if ours_frame_dir.exists() and theirs_frame_dir.exists():
            comparison_video = output_dir / f"{camera_str}_comparison.mp4"
            create_sidebyside_video(ours_frame_dir, theirs_frame_dir, comparison_video, args.fps)
        else:
            print("Both Ours and Theirs frames needed for comparison")


if __name__ == "__main__":
    main()
