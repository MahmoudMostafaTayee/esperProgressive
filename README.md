# Overlap Suppression Clustering for Multi-Camera People Tracking (Real-Time Implementation)

This repository contains a real-time implementation of the paper  
**"Overlap Suppression Clustering for Offline Multi-Camera People Tracking"**,  
adapted using streaming event processing with **Esper** and online clustering with **CluStream**.

## 📌 Overview

This project tackles the problem of tracking people across multiple camera views using real-time feature streams. It adapts the **Overlap Suppression Clustering (OSC)** approach to work in an **online streaming setup**, leveraging:

- 📡 **Esper** for Complex Event Processing (CEP)
- 🧠 **CluStream** for online micro-cluster generation
- ♻️ **Sliding window aggregation** and snapshot queries
- 📁 **.npy feature files** as input streams for each camera and frame

---

## 🚀 Features

- Real-time feature ingestion from `.npy` files
- Streaming feature grouping via Esper using `ext_timed` and `output snapshot` queries
- CluStream-based online clustering of embedded vectors
- Overlap Suppression logic for filtering duplicate detections
- Bipartite matching for associating clusters across sliding windows
- Multi-threaded scheduling with adaptive timing based on FPS
- Optional switching between internal and external time control

---

## 🛠 Technologies

| Component    | Description                              |
|--------------|------------------------------------------|
| **Esper**    | Streaming event processor (CEP engine)   |
| **Java**     | Core language for implementation         |
| **CluStream**| Micro-/macro-clustering algorithm        |
| **NumPy**    | For extracting embedding `.npy` vectors  |
| **Smile**    | (Optional) For similarity and clustering |
| **MOA**      | For CluStream integration (via SAMOA)    |

---

## 📂 Project Structure

```
📦 project-root/
 ┣ 📁 data/
 ┃ ┣ 📁 scene_01/
 ┃ ┃ ┣ 📁 camera_01/
 ┃ ┃ ┃ ┣ 📄 000001.npy
 ┃ ┃ ┃ ┣ 📄 000002.npy
 ┃ ┃ ┣ 📁 camera_02/
 ┃ ┃ ┃ ┣ 📄 000001.npy
 ┃ ┃ ┃ ┣ 📄 000002.npy
 ┃ ┣ 📁 scene_02/
 ┃ ┃ ┗ 📁 ...
 ┣ 📁 src/
 ┃ ┣ 📄 Main.java
 ┃ ┣ 📄 StreamingManager.java
 ┃ ┣ 📄 ClusteringProcessor.java
 ┃ ┣ 📄 FeatureEvent.java
 ┃ ┗ 📄 BipartiteMatcher.java
 ┣ 📄 TrackingParameters.java
 ┣ 📄 README.md
```


---

## ⚙️ How It Works

1. **Streaming**: Features are streamed from `.npy` files per camera using a fixed frame rate.
2. **Event Processing**: Esper groups features using sliding windows per frame via `#ext_timed`.
3. **Clustering**: Features are passed to a CluStream model to incrementally form micro-clusters.
4. **Matching**: Clusters from the current window are matched with previous ones using bipartite mapping.
5. **Suppression**: Overlapping detections are filtered to keep only unique individuals.

---

## 📈 Parameters to Tune

- `fps`: Target frames per second
- `timePeriod`: Window slide interval (in seconds)
- `numMicroClusters`: Granularity of streaming clustering
- `numMacroClusters`: Used in batch cluster summarization (for evaluation or merging)
- `overlapThreshold`: Spatial threshold to filter overlapping tracks

---

## 🧪 Running the Project

```bash
# Clone the repo
git clone https://github.com/your-username/your-repo-name.git

# Make sure to set BASE_PATH in TrackingParameters.java
# Then compile and run
cd your-repo-name
javac -cp ".:lib/*" src/*.java
java -cp ".:lib/*:src" Main

