import ast
import os
import itertools
import argparse
import shutil

from sklearn.metrics import (
    adjusted_rand_score,
    normalized_mutual_info_score
)

# -------------------------------------------------
# CONFIG
# -------------------------------------------------

DIRECTORIES = [
    r".\scene1\after-bare-clustering",
    r".\scene1\after-trackingByClustering",
    r".\scene1\after-associateClusterBetweenPeriod",
    r".\scene1\after-sequential_nms",
    r".\scene1\after-separate_warp",
    r".\scene1\after-exclude_short",
    r".\scene1\after-exclude_motionless",
    r".\scene1\mcpt-dumps",
]

# -------------------------------------------------
# I/O
# -------------------------------------------------

def read_cluster_file(file_path):
    with open(file_path, "r") as f:
        return ast.literal_eval(f.read().strip())

# -------------------------------------------------
# Similarity metrics
# -------------------------------------------------

def ari_similarity(a, b):
    return adjusted_rand_score(a, b)

def nmi_similarity(a, b):
    return normalized_mutual_info_score(a, b)

def pairwise_agreement(a, b):
    n = len(a)
    agree = 0
    total = 0

    for i, j in itertools.combinations(range(n), 2):
        same_a = (a[i] == a[j])
        same_b = (b[i] == b[j])
        if same_a == same_b:
            agree += 1
        total += 1

    return agree / total

# -------------------------------------------------
# Verdict logic
# -------------------------------------------------

def clustering_verdict(ari, nmi, pairwise):
    if ari >= 0.90 and nmi >= 0.90 and pairwise >= 0.95:
        return "VERY CLOSE (near-identical structure)"
    elif ari >= 0.80 and nmi >= 0.85 and pairwise >= 0.90:
        return "CLOSE (strong structural agreement)"
    elif ari >= 0.65 and nmi >= 0.70 and pairwise >= 0.85:
        return "MODERATELY CLOSE (minor boundary differences)"
    else:
        return "WEAK MATCH (structural divergence)"

# -------------------------------------------------
# CHECK MODE
# -------------------------------------------------

def run_checks():
    for directory in DIRECTORIES:
        print("=" * 80)
        print(f"Checking directory: {directory}")

        if not os.path.exists(directory):
            print("[WARN] Directory does not exist")
            continue

        java_files = {}
        python_files = {}

        for file in os.listdir(directory):
            if not file.endswith(".txt"):
                continue

            # Parse suffix: ..._{camera_id}_{window_no}.txt
            # We split by "_" and attempt to parse the last two segments as integers.
            # This handles "camera_0001" vs "1" (via int conversion) and varying prefixes.
            parts = file.replace(".txt", "").split("_")
            if len(parts) < 3:
                continue

            try:
                # Last part is window_no, second to last is camera_id
                win_no = int(parts[-1])
                cam_id = int(parts[-2])
                key = (cam_id, win_no)
            except ValueError:
                continue

            if file.startswith("clusters-java_"):
                java_files[key] = os.path.join(directory, file)
            elif file.startswith("clusters-python_"):
                python_files[key] = os.path.join(directory, file)

        common_keys = sorted(set(java_files) & set(python_files))

        if not common_keys:
            print("[INFO] No matching (camera, window) pairs found")
            continue

        for key in common_keys:
            cam_id, win_no = key
            print("-" * 80)
            print(f"Camera {cam_id} | Window {win_no}")

            java_clusters = read_cluster_file(java_files[key])
            python_clusters = read_cluster_file(python_files[key])

            if len(java_clusters) != len(python_clusters):
                print(f"[ERROR] Length mismatch; java({len(java_clusters)}) vs python({len(python_clusters)})")
                continue

            ari = ari_similarity(java_clusters, python_clusters)
            nmi = nmi_similarity(java_clusters, python_clusters)
            pa  = pairwise_agreement(java_clusters, python_clusters)

            verdict = clustering_verdict(ari, nmi, pa)

            print(f"ARI      : {ari:.4f}")
            print(f"NMI      : {nmi:.4f}")
            print(f"Pairwise : {pa:.2%}")
            print(f"Verdict  : {verdict}")

        print()

# -------------------------------------------------
# CLEAR MODE
# -------------------------------------------------

def clear_directories():
    print("WARNING: This will DELETE ALL FILES in the following directories:\n")
    for d in DIRECTORIES:
        print("  -", d)

    confirm = input("\nType YES to confirm: ").strip()

    if confirm != "YES":
        print("Aborted.")
        return

    for directory in DIRECTORIES:
        if not os.path.exists(directory):
            continue

        for filename in os.listdir(directory):
            file_path = os.path.join(directory, filename)
            try:
                if os.path.isfile(file_path):
                    os.remove(file_path)
                elif os.path.isdir(file_path):
                    shutil.rmtree(file_path)
            except Exception as e:
                print(f"[ERROR] Failed to delete {file_path}: {e}")

        print(f"[OK] Cleared: {directory}")

# -------------------------------------------------
# ENTRY POINT
# -------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Compare Java vs Python clustering results or clear dump directories."
    )
    parser.add_argument(
        "mode",
        choices=["check", "clear"],
        help="check = run similarity checks, clear = delete dump contents"
    )

    args = parser.parse_args()

    if args.mode == "check":
        run_checks()
    elif args.mode == "clear":
        clear_directories()

if __name__ == "__main__":
    main()
