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

### Using Terminal Scripts (Recommended)
You can run the project directly from the terminal using the provided scripts. These scripts use Maven to manage dependencies and bypass checkstyle for a smooth run.

#### Windows (Command Prompt)
```cmd
.\run_project.bat
```

#### Windows (PowerShell)
```powershell
.\run_project.ps1
```

### Manual Run with Maven
Alternatively, you can use Maven's `exec:java` goal:
```bash
mvn exec:java -Dexec.mainClass="com.espertech.esper.example.IOT.IotMain" -Dexec.args="--scene 1 --features_dir C:\OURs\Thesis\Datasets\EmbedFeature --camera 0001 --output_dir ./outputs/scene1" -Dcheckstyle.skip
```

---

## 🔧 Program Arguments

| Argument         | Description                                      | Default     |
| ---------------- | ------------------------------------------------ | ----------- |
| `--scene`        | **(Required)** Scene number (e.g., 1, 2, 3)      | -           |
| `--features_dir` | Base directory for embedding features            | -           |
| `--camera`       | Camera number (e.g., 0001, 0002) or `all`        | `all`       |
| `--output_dir`   | Directory to save logs and outputs               | `./output`  |
| `--exec_all`     | Execute all tracking stages (SCPT and MCPT)      | (Enabled)   |
| `--exec_scpt`    | Execute only Single-Camera People Tracking       | -           |
| `--exec_mcpt`    | Execute only Multi-Camera People Tracking        | -           |

### Example Arguments
```text
--scene 1 
--features_dir C:\OURs\Thesis\Datasets\EmbedFeature 
--camera 0001 
--output_dir ./outputs/scene1
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







For visualizations:
```bash
# Annotate Java MCPT results for camera 1
python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl java

# Annotate Python results
python annotate_mcpt_tracking.py --scene scene_001 --camera 1 --impl python

# Generate comparison video
python generate_videos.py --scene scene_001 --camera 1 --generate-comparison
```



