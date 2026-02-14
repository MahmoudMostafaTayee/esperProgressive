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
    print(f"✓ Video created: {output_video}")
    return True


def create_sidebyside_video(java_dir, python_dir, output_video, fps=30):
    """Create side-by-side comparison video"""
    java_frames = sorted(java_dir.glob("frame_*.jpg"))
    python_frames = sorted(python_dir.glob("frame_*.jpg"))
    
    if not java_frames or not python_frames:
        print("Missing frames for comparison")
        return False
    
    # Match frames by number
    java_dict = {f.stem.split('_')[1]: f for f in java_frames}
    python_dict = {f.stem.split('_')[1]: f for f in python_frames}
    
    common_frames = sorted(set(java_dict.keys()) & set(python_dict.keys()))
    
    if not common_frames:
        print("No matching frames found")
        return False
    
    # Get dimensions
    first_java = cv2.imread(str(java_dict[common_frames[0]]))
    height, width = first_java.shape[:2]
    
    # Create video writer (double width for side-by-side)
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(str(output_video), fourcc, fps, (width * 2, height))
    
    print(f"Creating comparison video: {output_video.name}")
    for frame_num in tqdm(common_frames, desc="Combining frames"):
        java_frame = cv2.imread(str(java_dict[frame_num]))
        python_frame = cv2.imread(str(python_dict[frame_num]))
        
        if java_frame is not None and python_frame is not None:
            # Concatenate horizontally
            combined = np.hstack([java_frame, python_frame])
            out.write(combined)
    
    out.release()
    print(f"✓ Comparison video created: {output_video}")
    return True


def main():
    parser = argparse.ArgumentParser(description="Generate videos from annotated frames")
    parser.add_argument("--scene", type=str, default="scene_001", help="Scene ID")
    parser.add_argument("--camera", type=int, default=1, help="Camera number")
    parser.add_argument("--base-dir", type=str,
                        default="C:/OURs/Thesis/Visualizations",
                        help="Base directory with annotated frames")
    parser.add_argument("--output-dir", type=str,
                        default="C:/OURs/Thesis/Videos",
                        help="Output directory for videos")
    parser.add_argument("--fps", type=int, default=30, help="Frames per second")
    parser.add_argument("--generate-comparison", action="store_true",
                        help="Generate side-by-side comparison video")
    parser.add_argument("--java-only", action="store_true",
                        help="Generate only Java video")
    parser.add_argument("--python-only", action="store_true",
                        help="Generate only Python video")
    
    args = parser.parse_args()
    
    # Setup paths
    base_dir = Path(args.base_dir) / args.scene
    camera_str = f"camera_{args.camera:04d}"
    
    java_frame_dir = base_dir / f"{camera_str}_java"
    python_frame_dir = base_dir / f"{camera_str}_python"
    
    output_dir = Path(args.output_dir) / args.scene
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate videos
    if not args.python_only:
        if java_frame_dir.exists():
            java_video = output_dir / f"{camera_str}_java.mp4"
            create_video_from_frames(java_frame_dir, java_video, args.fps)
        else:
            print(f"Java frames not found: {java_frame_dir}")
    
    if not args.java_only:
        if python_frame_dir.exists():
            python_video = output_dir / f"{camera_str}_python.mp4"
            create_video_from_frames(python_frame_dir, python_video, args.fps)
        else:
            print(f"Python frames not found: {python_frame_dir}")
    
    # Generate comparison video
    if args.generate_comparison:
        if java_frame_dir.exists() and python_frame_dir.exists():
            comparison_video = output_dir / f"{camera_str}_comparison.mp4"
            create_sidebyside_video(java_frame_dir, python_frame_dir, comparison_video, args.fps)
        else:
            print("Both Java and Python frames needed for comparison")


if __name__ == "__main__":
    main()
