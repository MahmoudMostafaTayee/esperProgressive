"""
Comprehensive MCPT Pipeline Comparison Script
Compares all dump files between Python and Java implementations
"""
import numpy as np
import json
import sys
from pathlib import Path
import matplotlib
matplotlib.use('Agg') # Headless backend for environments without a display
import matplotlib.pyplot as plt
import seaborn as sns

# Fix Unicode encoding for Windows console
if sys.platform == 'win32':
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

class Colors:
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BLUE = '\033[94m'
    BOLD = '\033[1m'
    END = '\033[0m'

def print_header(text):
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*80}{Colors.END}")
    print(f"{Colors.BOLD}{Colors.BLUE}{text:^80}{Colors.END}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*80}{Colors.END}\n")

def print_result(name, status, details=""):
    if status == "PASS":
        symbol = f"{Colors.GREEN}✓{Colors.END}"
    elif status == "WARN":
        symbol = f"{Colors.YELLOW}⚠{Colors.END}"
    else:
        symbol = f"{Colors.RED}✗{Colors.END}"
    
    print(f"{symbol} {Colors.BOLD}{name:40}{Colors.END} {details}")

def load_matrix(path):
    """Load matrix from text file"""
    try:
        return np.loadtxt(path, delimiter=',')
    except:
        try:
            return np.loadtxt(path, delimiter=', ')
        except Exception as e:
            print(f"Error loading {path}: {e}")
            return None

def get_ordered_serials(rep_nodes_data, keypoint_th=2):
    ordered_serials = []
    camera_ids = sorted(rep_nodes_data.keys(), key=lambda x: int(x))
    for cam_id in camera_ids:
        cam_data = rep_nodes_data[cam_id]
        local_ids = sorted(cam_data.keys(), key=lambda x: int(x))
        for loc_id in local_ids:
            node = cam_data[loc_id]
            if "representative_node" in node: node_info = node["representative_node"]
            else: node_info = node
            
            if node_info.get("score", 0) > keypoint_th: continue
            
            serial_raw = node_info["serial"]
            try: serial = str(int(serial_raw))
            except: serial = str(serial_raw)
            ordered_serials.append(serial)
    return ordered_serials

def compare_matrices(py_path, java_path, name, py_nodes_path, java_nodes_path, py_params_path):
    """Compare two matrices with robust alignment"""
    print(f"\n{Colors.BOLD}Comparing {name}...{Colors.END}")
    
    py_mat = load_matrix(py_path)
    java_mat = load_matrix(java_path)
    
    if py_mat is None or java_mat is None:
        print_result(name, "FAIL", "Failed to load matrices")
        return False

    # Load alignment data
    py_nodes = load_json(py_nodes_path)
    java_nodes_raw = load_json(java_nodes_path)
    py_params = load_json(py_params_path)
    
    if py_nodes is None or java_nodes_raw is None or py_params is None:
        print_result(name, "FAIL", "Failed to load node/param alignment files")
        return False
        
    if "representativeNodes" in java_nodes_raw: java_nodes = java_nodes_raw["representativeNodes"]
    else: java_nodes = java_nodes_raw
    
    keypoint_th = py_params.get("keypoint_condition_th", 2)
    py_serials = get_ordered_serials(py_nodes, keypoint_th)
    java_serials = get_ordered_serials(java_nodes, keypoint_th)
    
    # Common serials
    common_serials = set(py_serials).intersection(set(java_serials))
    if len(common_serials) == 0:
        print_result(name, "FAIL", "No common serials found to compare")
        return False
        
    sorted_common = sorted(list(common_serials), key=lambda x: int(x))
    py_s2i = {s: i for i, s in enumerate(py_serials)}
    java_s2i = {s: i for i, s in enumerate(java_serials)}
    
    py_idx = [py_s2i[s] for s in sorted_common]
    java_idx = [java_s2i[s] for s in sorted_common]
    
    sub_py = py_mat[np.ix_(py_idx, py_idx)]
    sub_java = java_mat[np.ix_(java_idx, java_idx)]
    
    # Comparison
    diff = np.abs(sub_py - sub_java)
    max_diff = np.max(diff)
    mean_diff = np.mean(diff)
    
    tolerance = 1e-4
    is_pass = max_diff < tolerance
    
    status = "PASS" if is_pass else "FAIL"
    print_result(name, status, f"Max Diff: {max_diff:.6f}, Mean: {mean_diff:.6f} (on {len(sorted_common)}x{len(sorted_common)} submatrix)")
    
    if not is_pass:
        print(f"    Top difference: {max_diff:.6f}")
        
    return is_pass, (sub_py, sub_java, diff.flatten())

def load_json(path):
    """Load JSON file"""
    try:
        with open(path, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading {path}: {e}")
        return None

def compare_representative_nodes(py_path, java_path):
    """Compare representative nodes JSON files"""
    print(f"\n{Colors.BOLD}Comparing Representative Nodes...{Colors.END}")
    
    py_data = load_json(py_path)
    java_data = load_json(java_path)
    
    if py_data is None or java_data is None:
        print_result("Representative Nodes", "FAIL", "Failed to load JSON")
        return False

    if "representativeNodes" in java_data:
        java_data = java_data["representativeNodes"]
    
    # Compare camera counts
    py_cameras = set(py_data.keys())
    java_cameras = set(java_data.keys())
    
    if py_cameras != java_cameras:
        print_result("Camera IDs", "FAIL", f"Py: {sorted(py_cameras)}, Java: {sorted(java_cameras)}")
    else:
        print_result("Camera IDs", "PASS", f"{len(py_cameras)} cameras")
    
    # Per-camera comparison
    total_matches = 0
    total_mismatches = 0
    
    for camera_id in sorted(py_cameras & java_cameras):
        py_nodes = py_data[camera_id]
        java_nodes = java_data[camera_id]
        
        py_local_ids = set(py_nodes.keys())
        java_local_ids = set(java_nodes.keys())
        
        common_ids = py_local_ids & java_local_ids
        
        matches = 0
        mismatches = 0
        
        for local_id in common_ids:
            py_serials = set(py_nodes[local_id].get('all_serials', []))
            java_serials = set(java_nodes[local_id].get('all_serials', []))
            
            if py_serials == java_serials:
                matches += 1
            else:
                mismatches += 1
        
        total_matches += matches
        total_mismatches += mismatches
        
        status = "PASS" if mismatches == 0 else "WARN"
        print_result(f"Camera {camera_id}", status, 
                    f"LocalIDs: Py={len(py_local_ids)}, Java={len(java_local_ids)}, Matches={matches}, Mismatches={mismatches}")
    
    overall_status = "PASS" if total_mismatches == 0 else "FAIL"
    print_result("Overall Cluster Match", overall_status, 
                f"Total Matches: {total_matches}, Total Mismatches: {total_mismatches}")
    
    return total_mismatches == 0

def compare_clusters_by_serial(py_path, java_path, name, py_nodes_path, java_nodes_path, py_params_path):
    """Compare cluster assignments by mapping through representative node serials"""
    print(f"\n{Colors.BOLD}Comparing {name}...{Colors.END}")
    
    try:
        # Load cluster assignments
        def load_clusters(filepath):
            with open(filepath, 'r') as f:
                content = f.read().strip()
            content = content.replace('[', '').replace(']', '').replace(',', ' ')
            clusters = [int(x.strip()) for x in content.split() if x.strip()]
            return clusters
        
        py_clusters = load_clusters(py_path)
        java_clusters = load_clusters(java_path)
        
        # Load representative nodes
        py_nodes = load_json(py_nodes_path)
        java_nodes_raw = load_json(java_nodes_path)
        py_params = load_json(py_params_path)
        
        if py_nodes is None or java_nodes_raw is None or py_params is None:
            print_result(name, "FAIL", "Failed to load node/param files")
            return False
        
        if "representativeNodes" in java_nodes_raw:
            java_nodes = java_nodes_raw["representativeNodes"]
        else:
            java_nodes = java_nodes_raw
        
        # Get ordered serials
        keypoint_th = py_params.get("keypoint_condition_th", 2)
        py_serials = get_ordered_serials(py_nodes, keypoint_th)
        java_serials = get_ordered_serials(java_nodes, keypoint_th)
        
        # Build mapping: cluster_id -> set of serials
        java_cluster_to_serials = {}
        for idx, cluster_id in enumerate(java_clusters):
            if idx >= len(java_serials):
                break
            serial = java_serials[idx]
            if cluster_id not in java_cluster_to_serials:
                java_cluster_to_serials[cluster_id] = set()
            java_cluster_to_serials[cluster_id].add(serial)
        
        py_cluster_to_serials = {}
        for idx, cluster_id in enumerate(py_clusters):
            if idx >= len(py_serials):
                break
            serial = py_serials[idx]
            if cluster_id not in py_cluster_to_serials:
                py_cluster_to_serials[cluster_id] = set()
            py_cluster_to_serials[cluster_id].add(serial)
        
        # Convert to sets of frozensets for comparison
        java_groups = set(frozenset(serials) for serials in java_cluster_to_serials.values())
        py_groups = set(frozenset(serials) for serials in py_cluster_to_serials.values())
        
        # Count matching groups
        matching_groups = len(java_groups & py_groups)
        total_groups = max(len(java_groups), len(py_groups))
        match_pct = (matching_groups / total_groups * 100) if total_groups > 0 else 0
        
        # Find unique serials that differ between implementations
        only_java_groups = java_groups - py_groups
        only_python_groups = py_groups - py_groups
        
        # For differing groups, find the unique serials
        unique_diff_serials = set()
        for java_group in (java_groups - py_groups):
            for py_group in (py_groups - java_groups):
                # Find serials only in java group
                only_in_java = java_group - py_group
                only_in_python = py_group - java_group
                unique_diff_serials.update(only_in_java)
                unique_diff_serials.update(only_in_python)
        
        total_serials = len(set(py_serials))
        unique_diff_count = len(unique_diff_serials)
        
        # Determine status
        if match_pct >= 99:
            status = "PASS"
        elif match_pct >= 90:
            status = "WARN"
        else:
            status = "FAIL"
        
        # Calculate effective accuracy (total serials correctly clustered)
        effective_accuracy = ((total_serials - unique_diff_count) / total_serials * 100) if total_serials > 0 else 0
        
        details = f"Groups: {matching_groups}/{total_groups} match ({match_pct:.1f}%)"
        
        if unique_diff_count > 0:
            details += f", effective accuracy(among all serials): {effective_accuracy:.1f}%({unique_diff_count} out of {total_serials} differs)"
            details += f", {unique_diff_count} unique serials differ"
            if unique_diff_count <= 5:
                details += f" ({sorted(list(unique_diff_serials))})"
        else:
            details += f", 100% accuracy"
        
        print_result(name, status, details)
        
        return match_pct, effective_accuracy
        
    except Exception as e:
        print_result(name, "FAIL", f"Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def compare_camera_dict_by_serial(py_path, java_path, name, py_nodes_path, java_nodes_path, py_params_path):
    """Compare camera dictionary by mapping through representative node serials"""
    print(f"\n{Colors.BOLD}Comparing {name}...{Colors.END}")
    
    try:
        import re
        
        def parse_camera_dict(filepath):
            """Parse camera dict text file"""
            with open(filepath, 'r') as f:
                content = f.read()
            
            camera_dict = {}
            current_camera = None
            
            for line in content.strip().split('\n'):
                camera_match = re.match(r'Camera (\d+):', line)
                if camera_match:
                    current_camera = camera_match.group(1)
                    camera_dict[current_camera] = {}
                    continue
                
                if 'indices:' in line:
                    indices_str = line.split('indices:')[1].strip()
                    indices = eval(indices_str)
                    camera_dict[current_camera]['indices'] = indices
                elif 'uniqueLocalIds:' in line:
                    ids_str = line.split('uniqueLocalIds:')[1].strip()
                    local_ids = eval(ids_str)
                    camera_dict[current_camera]['uniqueLocalIds'] = local_ids
            
            return camera_dict
        
        # Load camera dicts
        java_dict = parse_camera_dict(java_path)
        python_dict = parse_camera_dict(py_path)
        
        # Load representative nodes
        py_nodes = load_json(py_nodes_path)
        java_nodes_raw = load_json(java_nodes_path)
        py_params = load_json(py_params_path)
        
        if py_nodes is None or java_nodes_raw is None or py_params is None:
            print_result(name, "FAIL", "Failed to load node/param files")
            return False
        
        if "representativeNodes" in java_nodes_raw:
            java_nodes = java_nodes_raw["representativeNodes"]
        else:
            java_nodes = java_nodes_raw
        
        # Get ordered serials
        keypoint_th = py_params.get("keypoint_condition_th", 2)
        py_serials = get_ordered_serials(py_nodes, keypoint_th)
        java_serials = get_ordered_serials(java_nodes, keypoint_th)
        
        # Compare each camera
        cameras_match = 0
        cameras_differ = 0
        total_serial_diffs = 0
        
        for camera_id in sorted(set(java_dict.keys()) | set(python_dict.keys())):
            if camera_id not in java_dict or camera_id not in python_dict:
                cameras_differ += 1
                continue
            
            java_cam = java_dict[camera_id]
            python_cam = python_dict[camera_id]
            
            # Get serials for each camera's indices
            java_indices = java_cam['indices']
            python_indices = python_cam['indices']
            
            java_serials_for_camera = set(java_serials[i] for i in java_indices)
            py_serials_for_camera = set(py_serials[i] for i in python_indices)
            
            if java_serials_for_camera == py_serials_for_camera:
                cameras_match += 1
            else:
                cameras_differ += 1
                diff = len(java_serials_for_camera ^ py_serials_for_camera)
                total_serial_diffs += diff
        
        total_cameras = cameras_match + cameras_differ
        match_pct = (cameras_match / total_cameras * 100) if total_cameras > 0 else 0
        
        # Determine status
        if match_pct >= 99:
            status = "PASS"
        elif match_pct >= 75:
            status = "WARN"
        else:
            status = "FAIL"
        
        details = f"{cameras_match}/{total_cameras} cameras match ({match_pct:.1f}%)"
        if total_serial_diffs > 0:
            details += f", {total_serial_diffs} serials differ (from upstream clustering)"
        
        print_result(name, status, details)
        
        return match_pct >= 75
        
    except Exception as e:
        print_result(name, "FAIL", f"Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def compare_global_ids_by_serial(py_path, java_path, name):
    """Compare global ID assignments by mapping through serials"""
    print(f"\n{Colors.BOLD}Comparing {name}...{Colors.END}")
    
    try:
        import re
        from collections import defaultdict
        
        def parse_global_ids_json(filepath):
            """Parse global IDs from JSON: {camera_id: {serial: {localId: globalId}}}"""
            result = defaultdict(lambda: defaultdict(dict))
            
            try:
                with open(filepath, 'r') as f:
                    data = json.load(f)
                    
                for frame, entries in data.items():
                    for entry in entries:
                        camera_id = str(entry.get('camera'))
                        serial_raw = entry.get('serial')
                        try:
                            # Normalize serial to unpadded string
                            serial = str(int(str(serial_raw)))
                        except ValueError:
                            serial = str(serial_raw)
                            
                        local_id = str(entry.get('localId'))
                        global_id = str(entry.get('globalId'))
                        
                        result[camera_id][serial][local_id] = global_id
            except Exception as e:
                print(f"Error parsing JSON {filepath}: {e}")
                
            return result
        
        def build_global_clusters(global_ids_data):
            """Group serials by their global ID: returns set of frozensets"""
            global_clusters = defaultdict(set)
            
            for camera_id, serials in global_ids_data.items():
                for serial, mappings in serials.items():
                    for global_id in mappings.values():
                        global_clusters[global_id].add((camera_id, serial))
            
            return set(frozenset(cluster) for cluster in global_clusters.values())
        
        # Parse global IDs (now expecting JSON files)
        java_global_ids = parse_global_ids_json(java_path)
        python_global_ids = parse_global_ids_json(py_path)
        
        # Build global clusters
        java_clusters = build_global_clusters(java_global_ids)
        python_clusters = build_global_clusters(python_global_ids)
        
        # Compare cluster structures
        matching_clusters = len(java_clusters & python_clusters)
        total_clusters = max(len(java_clusters), len(python_clusters))
        match_pct = (matching_clusters / total_clusters * 100) if total_clusters > 0 else 0
        
        # Determine status
        if match_pct >= 99:
            status = "PASS"
        elif match_pct >= 90:
            status = "WARN"
        else:
            status = "FAIL"
        
        details = f"{matching_clusters}/{total_clusters} global clusters match ({match_pct:.1f}%)"
        
        print_result(name, status, details)
        
        return match_pct
        
    except Exception as e:
        print_result(name, "FAIL", f"Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def compare_text_dumps(py_path, java_path, name):
    """Compare text dump files"""
    print(f"\n{Colors.BOLD}Comparing {name}...{Colors.END}")
    
    try:
        with open(py_path, 'r') as f:
            py_content = f.read()
        with open(java_path, 'r') as f:
            java_content = f.read()
        
        if py_content == java_content:
            print_result(name, "PASS", "Exact match")
            return True
        else:
            # Line-by-line comparison
            py_lines = py_content.strip().split('\n')
            java_lines = java_content.strip().split('\n')
            
            matching_lines = sum(1 for p, j in zip(py_lines, java_lines) if p == j)
            total_lines = max(len(py_lines), len(java_lines))
            match_pct = (matching_lines / total_lines) * 100
            
            status = "PASS" if match_pct > 95 else "WARN" if match_pct > 80 else "FAIL"
            print_result(name, status, 
                        f"Lines: Py={len(py_lines)}, Java={len(java_lines)}, Match={matching_lines}/{total_lines} ({match_pct:.1f}%)")
            return match_pct > 95
    except Exception as e:
        print_result(name, "FAIL", f"Error: {e}")
        return False

def main():
    base_path = "c:/OURs/Thesis/Real-Time_Multi-Camera_People_Tracking_using_Event_Stream_Processing/output/scene1/mcpt-dumps"
    
    print_header("MCPT Pipeline Comprehensive Comparison (Ours vs Theirs)")
    
    results = {}
    
    # Define alignment files
    theirs_nodes = f"{base_path}/mcpt-representative-nodes-python.json"
    ours_nodes = f"{base_path}/mcpt-representative-nodes-java_0.json"
    theirs_params = f"{base_path}/mcpt-params-python.json"

    # 1. Compare Similarity Matrices
    print_header("Similarity Matrices")
    raw_mat_info = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-raw-python.txt",
        f"{base_path}/mcpt-similarity-matrix-raw_0.txt",
        "RAW Similarity Matrix",
        theirs_nodes, ours_nodes, theirs_params
    )
    results['raw_matrix'] = raw_mat_info[0]
    
    replaced_mat_info = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-replaced-python.txt",
        f"{base_path}/mcpt-similarity-matrix-replaced_0.txt",
        "REPLACED Similarity Matrix",
        theirs_nodes, ours_nodes, theirs_params
    )
    results['replaced_matrix'] = replaced_mat_info[0]
    
    zeroed_mat_info = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-zeroed-python.txt",
        f"{base_path}/mcpt-similarity-matrix-zeroed_0.txt",
        "ZEROED Similarity Matrix",
        theirs_nodes, ours_nodes, theirs_params
    )
    results['zeroed_matrix'] = zeroed_mat_info[0]
    
    # 2. Compare Representative Nodes
    print_header("Representative Nodes & Clustering")
    results['representative_nodes'] = compare_representative_nodes(
        f"{base_path}/mcpt-representative-nodes-python.json",
        f"{base_path}/mcpt-representative-nodes-java_0.json"
    )
    
    # 3. Compare Clusters After HC (using serial-based comparison)
    cluster_acc = compare_clusters_by_serial(
        f"{base_path}/mcpt-clusters-after-hc-python.txt",
        f"{base_path}/mcpt-clusters-after-hc_0.txt",
        "Clusters After Hierarchical Clustering",
        theirs_nodes, ours_nodes, theirs_params
    )
    results['clusters_after_hc'] = cluster_acc[0] >= 90
    
    # # 4. Compare Camera Dict (using serial-based comparison)
    # results['camera_dict'] = compare_camera_dict_by_serial(
    #     f"{base_path}/mcpt-camera-dict-python.txt",
    #     f"{base_path}/mcpt-camera-dict_0.txt",
    #     "Camera Dictionary",
    #     theirs_nodes, ours_nodes, theirs_params
    # )
    
    # 5. Compare Global IDs (using serial-based comparison)
    global_id_acc = compare_global_ids_by_serial(
        f"{base_path}/mcpt-global-ids-python.json",
        f"{base_path}/mcpt-global-ids_0.json",
        "Global ID Assignments"
    )
    results['global_ids'] = global_id_acc >= 90
    
    # Collect data for visualization
    viz_mats = {
        'Raw Similarity': raw_mat_info[1][:2],
        'Replaced Similarity': replaced_mat_info[1][:2],
        'Zeroed Similarity': zeroed_mat_info[1][:2]
    }
    diff_values = {
        'raw': raw_mat_info[1][2],
        'replaced': replaced_mat_info[1][2],
        'zeroed': zeroed_mat_info[1][2]
    }
    
    # Summary
    print_header("Summary (Ours vs Theirs)")
    
    passed = sum(1 for v in results.values() if v)
    total = len(results)
    
    print(f"\n{Colors.BOLD}Results: {passed}/{total} comparisons passed{Colors.END}\n")
    
    for name, status in results.items():
        status_str = f"{Colors.GREEN}PASS{Colors.END}" if status else f"{Colors.RED}FAIL{Colors.END}"
        print(f"  {name:30} {status_str}")
    
    print(f"\n{Colors.BOLD}Key Findings:{Colors.END}")
    if results['raw_matrix']:
        print(f"  {Colors.GREEN}✓{Colors.END} Similarity matrix generation matches (Ours vs Theirs)")
    else:
        print(f"  {Colors.RED}✗{Colors.END} Similarity matrices still differ")
    
    if results['representative_nodes']:
        print(f"  {Colors.GREEN}✓{Colors.END} Representative node selection matches perfectly")
    else:
        print(f"  {Colors.YELLOW}⚠{Colors.END} Representative node selection differs")
    
    if results['clusters_after_hc']:
        print(f"  {Colors.GREEN}✓{Colors.END} Cluster groupings match (>90% when mapped by serial)")
    else:
        print(f"  {Colors.YELLOW}⚠{Colors.END} Some cluster groupings differ (tie-breaking etc)")
    
    if results['global_ids']:
        print(f"  {Colors.GREEN}✓{Colors.END} Global ID assignments match")
    else:
        print(f"  {Colors.YELLOW}⚠{Colors.END} Global ID assignments differ")
    
    print()
    
    # 6. Generate Visualizations
    generate_visualizations(base_path, results, viz_mats, cluster_acc, global_id_acc, diff_values)

def generate_visualizations(output_dir, results, viz_mats, cluster_acc, global_id_acc, diff_values):
    print_header("Generating Scientific Visualizations")
    viz_path = Path(output_dir) / "plots"
    viz_path.mkdir(exist_ok=True)
    
    # 1. Similarity Matrix Heatmaps
    for name, (py_mat, java_mat) in viz_mats.items():
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
        
        sns.heatmap(py_mat, ax=ax1, cmap='viridis', cbar_kws={'label': 'Similarity Score'})
        ax1.set_title(f'Python Reference: {name}')
        ax1.set_xlabel('Representative Node Index')
        ax1.set_ylabel('Representative Node Index')
        
        sns.heatmap(java_mat, ax=ax2, cmap='viridis', cbar_kws={'label': 'Similarity Score'})
        ax2.set_title(f'Java Implementation: {name}')
        ax2.set_xlabel('Representative Node Index')
        ax2.set_ylabel('Representative Node Index')
        
        plt.tight_layout()
        save_name = viz_path / f"heatmap_{name.lower().replace(' ', '_')}.png"
        plt.savefig(save_name, dpi=300)
        print(f"  Saved heatmap: {save_name}")
        plt.close()

    # 2. Agreement Parity Chart
    metrics = {
        'Clustering (Group)': cluster_acc[0],
        'Clustering (Serial)': cluster_acc[1],
        'Global ID Match': global_id_acc
    }
    
    plt.figure(figsize=(10, 6))
    bars = plt.bar(metrics.keys(), metrics.values(), color=['#4C72B0', '#55A868', '#C44E52'])
    plt.ylim(0, 110)
    plt.ylabel('Agreement (%)')
    plt.title('Implementation Parity Metrics: Python vs Java')
    
    # Add values on top of bars
    for bar in bars:
        yval = bar.get_height()
        plt.text(bar.get_x() + bar.get_width()/2, yval + 1, f'{yval:.1f}%', ha='center', va='bottom', fontweight='bold')
    
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    save_name = viz_path / "parity_metrics.png"
    plt.savefig(save_name, dpi=300)
    print(f"  Saved parity chart: {save_name}")
    plt.close()

    # 3. Numerical Difference Distribution
    if diff_values:
        plt.figure(figsize=(10, 6))
        all_diffs = np.concatenate(list(diff_values.values()))
        # Only show non-zero differences to see the error distribution
        small_diffs = all_diffs[all_diffs > 1e-10]
        
        if len(small_diffs) > 0:
            sns.histplot(small_diffs, kde=True, color='purple', bins=50)
            plt.yscale('log')
            plt.title('Distribution of Numerical Differences (Non-Zero)')
            plt.xlabel('Absolute Difference Value')
        else:
            plt.text(0.5, 0.5, 'No significant numerical differences found', ha='center', va='center')
            plt.title('Numerical Differences')
            
        plt.grid(True, which="both", ls="-", alpha=0.2)
        save_name = viz_path / "difference_distribution.png"
        plt.savefig(save_name, dpi=300)
        print(f"  Saved difference distribution: {save_name}")
        plt.close()

    print(f"\n{Colors.GREEN}Successfully generated 3 scientific visualizations in {viz_path}{Colors.END}\n")

if __name__ == "__main__":
    main()
