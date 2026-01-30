import numpy as np
import os

EPSILON = 0.1                 # SAME epsilon used in SCPT
THRESH = 1.0 - EPSILON
NUM_TOL = 1e-4
NEAR_TOL = 1e-5


# ---------- Loading ----------
def load_matrix(path):
    with open(path, "r") as f:
        rows = []
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split(",") if "," in line else line.split()
            rows.append([float(x) for x in parts])
    return np.array(rows)


# ---------- Diagnostics ----------
def structural_diff(J, P):
    Jb = (J > 0).astype(np.int8)
    Pb = (P > 0).astype(np.int8)
    diff = np.sum(Jb != Pb)
    return diff, J.size


def numeric_diff(J, P):
    D = J - P
    return {
        "max": np.max(np.abs(D)),
        "mean": np.mean(np.abs(D)),
        "l2": np.linalg.norm(D),
    }


def threshold_analysis(J, P):
    near_J = np.sum(np.abs(J - THRESH) < NEAR_TOL)
    near_P = np.sum(np.abs(P - THRESH) < NEAR_TOL)
    return near_J, near_P


def rowwise_divergence(J, P, topk=5):
    row_norms = np.linalg.norm(J - P, axis=1)
    worst = np.argsort(row_norms)[-topk:]
    return worst, row_norms


def first_mismatches(J, P, tol=NUM_TOL, limit=5):
    diff = np.abs(J - P)
    idx = np.argwhere(diff > tol)
    return idx[:limit]


# ---------- Comparison ----------
def compare(java_path, python_path, window_id):
    print(f"\n========== WINDOW {window_id} ==========")

    J = load_matrix(java_path)
    P = load_matrix(python_path)

    print(f"Java shape   : {J.shape}")
    print(f"Python shape : {P.shape}")

    if J.shape != P.shape:
        print("❌ SHAPE MISMATCH — aborting")
        return

    # Structural
    struct_diff, total = structural_diff(J, P)
    print(f"Structural diff : {struct_diff}/{total} "
          f"({100*struct_diff/total:.6f}%)")

    # Numeric
    num = numeric_diff(J, P)
    print(f"Numeric diff    : "
          f"max={num['max']:.6e}, "
          f"mean={num['mean']:.6e}, "
          f"L2={num['l2']:.6e}")

    # Threshold
    near_J, near_P = threshold_analysis(J, P)
    print(f"Near threshold : Java={near_J}, Python={near_P}")

    # Rows
    worst_rows, row_norms = rowwise_divergence(J, P)
    print("Worst rows     :", worst_rows.tolist())
    for r in worst_rows:
        print(f"  Row {r} norm  : {row_norms[r]:.6e}")

    # Concrete mismatches
    mismatches = first_mismatches(J, P)
    if len(mismatches) > 0:
        print("First mismatches:")
        for r, c in mismatches:
            print(f"  ({r},{c}) Java={J[r,c]:.6f}, Python={P[r,c]:.6f}")
    else:
        print("No element-wise mismatches above tolerance")

    # Verdict
    if struct_diff == 0:
        print("✅ STRUCTURALLY IDENTICAL")
    else:
        print("❌ STRUCTURAL DIVERGENCE — clustering WILL differ")


# ---------- Main ----------
directory = r".\similatiry-matrix"

java_files = {}
python_files = {}

for file in os.listdir(directory):
    if file.startswith("java-") and file.endswith(".txt"):
        idx = file.split("_")[-1].replace(".txt", "")
        java_files[idx] = os.path.join(directory, file)
    elif file.startswith("python-") and file.endswith(".txt"):
        idx = file.split("_")[-1].replace(".txt", "")
        python_files[idx] = os.path.join(directory, file)

for idx in sorted(set(java_files) & set(python_files)):
    compare(java_files[idx], python_files[idx], idx)
