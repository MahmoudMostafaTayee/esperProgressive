import json
import os
import collections

def load_json(filepath):
    """Loads JSON data from a file."""
    try:
        with open(filepath, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return None

def get_tracking_results(data):
    """Extracts tracking results handling both Python and Java key conventions."""
    if data is None: return None
    if "tracking_results" in data:
        return data["tracking_results"]
    if "trackingResults" in data:
        return data["trackingResults"]
    return None

def compute_jaccard_similarity(set1, set2):
    """Computes Jaccard Index between two sets."""
    intersection = len(set1.intersection(set2))
    union = len(set1.union(set2))
    return intersection / union if union > 0 else 0.0

def analyze_reps_for_camera(cam_id, py_nodes, java_nodes):
    """
    Analyzes representative nodes for a single camera.
    Returns a dictionary of metrics.
    """
    # --- 1. Build Serial-to-Score Maps ---
    # map: {serial_string: score_float}
    # clusters: list of sets of serials
    
    def parse_nodes(nodes_dict):
        serial_map = {}
        clusters = []
        for local_id, node in nodes_dict.items():
            score = node.get("score", 0)
            serials = node.get("allSerials", [])
            # Normalize serials: "000123" -> 123 -> "123"
            try:
                serials = [str(int(s)) for s in serials]
            except ValueError:
                # Fallback if serial isn't a number (unlikely)
                serials = [str(s) for s in serials]
                
            cluster_set = set(serials)
            if cluster_set:
                clusters.append(cluster_set)
            
            for s in serials:
                serial_map[s] = score
        return serial_map, clusters

    py_map, py_clusters = parse_nodes(py_nodes)
    java_map, java_clusters = parse_nodes(java_nodes)

    # --- 2. Calculate Serial-Level Metrics ---
    py_serials = set(py_map.keys())
    java_serials = set(java_map.keys())
    
    union_serials = py_serials.union(java_serials)
    intersection_serials = py_serials.intersection(java_serials)
    
    total_unique = len(union_serials)
    common_count = len(intersection_serials)
    
    score_matches = 0
    score_mismatches = 0
    
    # We allow a small float tolerance
    EPSILON = 1e-5
    
    for s in intersection_serials:
        py_score = py_map[s]
        java_score = java_map[s]
        if abs(py_score - java_score) < EPSILON:
            score_matches += 1
        else:
            score_mismatches += 1
            
    # Presence Overlap % (How many serials found in both vs total unique)
    presence_pct = (common_count / total_unique * 100) if total_unique > 0 else 0.0
    
    # Score Similarity % (Global accuracy: Correct Serials / Total Unique)
    global_score_sim_pct = (score_matches / total_unique * 100) if total_unique > 0 else 0.0
    
    # Score Agreement % (Conditional accuracy: Correct / Common)
    agreement_pct = (score_matches / common_count * 100) if common_count > 0 else 0.0

    # --- 3. Calculate Group Similarity (Jaccard) ---
    # For each Py cluster, find best matching Java cluster
    jaccard_scores = []
    
    # Python to Java best match
    for p_clust in py_clusters:
        best_iou = 0.0
        for j_clust in java_clusters:
            iou = compute_jaccard_similarity(p_clust, j_clust)
            if iou > best_iou:
                best_iou = iou
        jaccard_scores.append(best_iou)
        
    # Java to Python best match (symmetric check usually good, let's average them or just take all)
    # The requirement is "how close they are", usually one-way validation (Reference vs Candidate) is enough.
    # We'll treat Python as Ground Truth.
    
    avg_group_similarity = (sum(jaccard_scores) / len(jaccard_scores) * 100) if jaccard_scores else 0.0

    return {
        "total_unique": total_unique,
        "common": common_count,
        "missing_in_java": len(py_serials - java_serials),
        "extra_in_java": len(java_serials - py_serials),
        "score_matches": score_matches,
        "presence_pct": presence_pct,
        "global_score_sim_pct": global_score_sim_pct,
        "agreement_pct": agreement_pct,
        "group_sim_pct": avg_group_similarity
    }

def print_progress_bar(val, max_val=100, length=20, label=""):
    pct = val
    fill = int(length * pct / 100)
    bar = '#' * fill + '-' * (length - fill)
    print(f"  {label:<20} |{bar}| {pct:6.2f}%")

def compare_representative_nodes(python_path, java_path):
    print(f"\n{'='*60}")
    print(f"COMPARISON REPORT: Representative Nodes")
    print(f"Python (Ref): {python_path}")
    print(f"Java (Cand):  {java_path}")
    print(f"{'='*60}\n")

    py_data = load_json(python_path)
    java_data_raw = load_json(java_path)
    
    if py_data is None or java_data_raw is None:
        return

    java_data = java_data_raw.get("representativeNodes", {})

    py_cams = set(py_data.keys())
    java_cams = set(java_data.keys())
    
    all_cams = sorted(list(py_cams.union(java_cams)))
    
    # Global Aggregators
    total_group_sim = 0
    total_score_sim = 0
    count_cams = 0

    print(f"Cameras Found: {len(all_cams)} {all_cams}")
    
    for cam_id in all_cams:
        print(f"\n--- Camera {cam_id} ---")
        if cam_id not in py_cams:
            print(f"  [MISSING] Not found in Python Reference.")
            continue
        if cam_id not in java_cams:
            print(f"  [MISSING] Not found in Java Candidate.")
            continue
            
        metrics = analyze_reps_for_camera(cam_id, py_data[cam_id], java_data[cam_id])
        
        # Print Summary
        print(f"  Total Serials (Union): {metrics['total_unique']}")
        print(f"  Common Serials:        {metrics['common']} (Missing: {metrics['missing_in_java']}, Extra: {metrics['extra_in_java']})")
        print(f"  Exact Score Matches:   {metrics['score_matches']}")
        
        print("")
        print_progress_bar(metrics['presence_pct'], label="Presence Overlap")
        print_progress_bar(metrics['global_score_sim_pct'], label="Global Score Match")
        print_progress_bar(metrics['agreement_pct'], label="Score Agreement")
        print_progress_bar(metrics['group_sim_pct'], label="Grouping Similarity")
        
        total_group_sim += metrics['group_sim_pct']
        total_score_sim += metrics['global_score_sim_pct']
        count_cams += 1

    if count_cams > 0:
        print(f"\n{'='*60}")
        print(f"OVERALL SYSTEM SUMMARY")
        print(f"{'='*60}")
        avg_group = total_group_sim / count_cams
        avg_score = total_score_sim / count_cams
        
        print(f"  Average Grouping Similarity: {avg_group:.2f}%")
        print(f"  Average Global Score Match:  {avg_score:.2f}%")
        
        if avg_group > 90 and avg_score > 90:
            print(f"\n  [RESULT] PASSED: High fidelity between implementations.")
        elif avg_group > 75:
            print(f"\n  [RESULT] WARNING: Moderate differences detected.")
        else:
            print(f"\n  [RESULT] FAILED: Significant divergence.")
    else:
        print("\nNo common cameras to compare.")

if __name__ == "__main__":
    # Standard paths - can be adjusted or made arguments
    py_rep_path = "./scene1/mcpt-dumps/mcpt-representative-nodes-python.json"
    java_rep_path = "./scene1/mcpt-dumps/mcpt-representative-nodes-java_0.json"

    if os.path.exists(py_rep_path) and os.path.exists(java_rep_path):
        compare_representative_nodes(py_rep_path, java_rep_path)
    else:
        print("Could not find dump files to compare.")
        if not os.path.exists(py_rep_path): print(f"Missing: {py_rep_path}")
        if not os.path.exists(java_rep_path): print(f"Missing: {java_rep_path}")
