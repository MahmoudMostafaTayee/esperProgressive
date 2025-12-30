# Overlap Suppression Clustering for Multi-Camera People Tracking  

### Real-Time Streaming Implementation

This repository contains a **real-time implementation** of the paper:

**Overlap Suppression Clustering for Offline Multi-Camera People Tracking**

The original offline approach is adapted here to an **online streaming setting** using **Esper** for event processing and **CluStream** for incremental clustering.

## 📌 Overview

This project tackles the problem of tracking people across multiple camera views using real-time feature streams. It adapts the **Overlap Suppression Clustering (OSC)** approach to work in an **online streaming setup**, leveraging:

- 📡 **Esper** for Complex Event Processing (CEP)
- 🧠 **CluStream** for online micro-cluster generation
- ♻️ **Sliding window aggregation** and snapshot queries
- 📁 **.npy feature files** as input streams for each camera and frame

---

## 🚀 Features

- Real-time streaming of `.npy` embedding features
- Sliding window aggregation using Esper (`#ext_timed`, `output snapshot`)
- Online micro-clustering using **CluStream**
- Overlap Suppression to remove duplicate detections
- Bipartite matching between clusters across time windows
- Adaptive scheduling based on FPS
- Supports **internal** and **external** Esper time
- Multi-camera and multi-scene support

---

## 🛠 Technologies

| Component       | Description                        |
| --------------- | ---------------------------------- |
| **Java**        | Core implementation language       |
| **Esper**       | Complex Event Processing (CEP)     |
| **CluStream**   | Online streaming clustering        |
| **MOA / SAMOA** | CluStream integration              |
| **Smile**       | (Optional) similarity & clustering |
| **NumPy**       | `.npy` embedding files             |
| **Maven**       | Build & dependency management      |

---

## 🧩 Build System & Environment

- **Build system**: Maven  
- All dependencies are resolved automatically via `pom.xml`
- **IDE**: IntelliJ IDEA
- Tested and verified on:
  - **Ubuntu 24.04**
  - **Windows 11**

---

- ## 📦 Building the Project

  ```bash
  mvn clean install
  ```

  ---

  ## ▶️ Running the Project

  ### Entry Point

  ```
  com.espertech.esper.example.IOT.IotMain
  ```

  ---

  ## 🔧 Program Arguments

  | Argument            | Description                   |
  | ------------------- | ----------------------------- |
  | `--dataRoot`        | Root directory of the dataset |
  | `--scene`           | Scene name to process         |
  | `--fps`             | Target frames per second      |
  | `--window`          | Sliding window size (seconds) |
  | `--useExternalTime` | Enable Esper external time    |

  ### Example

  ```text
  --dataRoot /home/user/Datasets/EmbedFeature
  --scene scene_001
  --fps 10
  --window 1.0
  --useExternalTime true
  ```

  ---

  ## 📂 Dataset Structure

  ```text
  dataRoot/
   ┣ scene_001/
   ┃ ┣ camera_0001/
   ┃ ┃ ┣ 000001.npy
   ┃ ┃ ┣ 000002.npy
   ┃ ┣ camera_0002/
   ┃ ┃ ┣ 000001.npy
   ┃ ┃ ┣ 000002.npy
  ```


## 📚 Reference

**Overlap Suppression Clustering for Offline Multi-Camera People Tracking**
