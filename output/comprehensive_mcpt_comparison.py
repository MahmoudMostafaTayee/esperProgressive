"""
Comprehensive MCPT Pipeline Comparison Script
Compares all dump files between Python and Java implementations
"""
import numpy as np
import json
import sys
from pathlib import Path

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
        
    return is_pass

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
    
    print_header("MCPT Pipeline Comprehensive Comparison")
    
    results = {}
    
    # Define alignment files
    py_nodes = f"{base_path}/mcpt-representative-nodes-python.json"
    java_nodes = f"{base_path}/mcpt-representative-nodes-java_0.json"
    py_params = f"{base_path}/mcpt-params-python.json"

    # 1. Compare Similarity Matrices
    print_header("Similarity Matrices")
    results['raw_matrix'] = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-raw-python.txt",
        f"{base_path}/mcpt-similarity-matrix-raw_0.txt",
        "RAW Similarity Matrix",
        py_nodes, java_nodes, py_params
    )
    
    results['replaced_matrix'] = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-replaced-python.txt",
        f"{base_path}/mcpt-similarity-matrix-replaced_0.txt",
        "REPLACED Similarity Matrix",
        py_nodes, java_nodes, py_params
    )
    
    results['zeroed_matrix'] = compare_matrices(
        f"{base_path}/mcpt-similarity-matrix-zeroed-python.txt",
        f"{base_path}/mcpt-similarity-matrix-zeroed_0.txt",
        "ZEROED Similarity Matrix",
        py_nodes, java_nodes, py_params
    )
    
    # 2. Compare Representative Nodes
    print_header("Representative Nodes & Clustering")
    results['representative_nodes'] = compare_representative_nodes(
        f"{base_path}/mcpt-representative-nodes-python.json",
        f"{base_path}/mcpt-representative-nodes-java_0.json"
    )
    
    # 3. Compare Clusters After HC
    results['clusters_after_hc'] = compare_text_dumps(
        f"{base_path}/mcpt-clusters-after-hc-python.txt",
        f"{base_path}/mcpt-clusters-after-hc_0.txt",
        "Clusters After Hierarchical Clustering"
    )
    
    # 4. Compare Camera Dict
    results['camera_dict'] = compare_text_dumps(
        f"{base_path}/mcpt-camera-dict-python.txt",
        f"{base_path}/mcpt-camera-dict_0.txt",
        "Camera Dictionary"
    )
    
    # 5. Compare Global IDs
    results['global_ids'] = compare_text_dumps(
        f"{base_path}/mcpt-global-ids-python.txt",
        f"{base_path}/mcpt-global-ids_0.txt",
        "Global ID Assignments"
    )
    
    # Summary
    print_header("Summary")
    
    passed = sum(1 for v in results.values() if v)
    total = len(results)
    
    print(f"\n{Colors.BOLD}Results: {passed}/{total} comparisons passed{Colors.END}\n")
    
    for name, status in results.items():
        status_str = f"{Colors.GREEN}PASS{Colors.END}" if status else f"{Colors.RED}FAIL{Colors.END}"
        print(f"  {name:30} {status_str}")
    
    print(f"\n{Colors.BOLD}Key Findings:{Colors.END}")
    if results['raw_matrix']:
        print(f"  {Colors.GREEN}✓{Colors.END} Similarity matrix generation is now correct (dense matrices)")
    else:
        print(f"  {Colors.RED}✗{Colors.END} Similarity matrices still differ")
    
    if results['representative_nodes']:
        print(f"  {Colors.GREEN}✓{Colors.END} Representative node selection matches perfectly")
    else:
        print(f"  {Colors.YELLOW}⚠{Colors.END} Clustering results differ - likely hierarchical clustering algorithm")
    
    if results['global_ids']:
        print(f"  {Colors.GREEN}✓{Colors.END} Global ID assignments match")
    else:
        print(f"  {Colors.YELLOW}⚠{Colors.END} Global ID assignments differ due to upstream clustering")
    
    print()

if __name__ == "__main__":
    main()
