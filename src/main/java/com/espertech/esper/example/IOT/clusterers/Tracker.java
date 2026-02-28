package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.*;
import com.espertech.esper.example.IOT.utils.DetectedUser;
import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
//import java.io.IOException;
//import java.util.List;

public class Tracker {

    private String cameraId;
    Integer windowIndex = 0;

    // State for association across windows
    private List<double[]> pastFeatures = new ArrayList<>();
    private List<Integer> pastFrames = new ArrayList<>();
    private List<Integer> pastClusters = new ArrayList<>();
    private int maxOfflineId = -1;

    public Tracker(String cameraId) {
        this.cameraId = cameraId;
    }

    public Tracker() {
        this.cameraId = "unknown";
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement,
            EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0)
            return;

        Instant start_time = Instant.now();
        long lasttimestamp = 0L;
        Integer first_id = 0;
        Integer last_id = 0;
        Integer first_frame = 0;
        Integer last_frame = 0;

        // 1. Data Extraction
        List<double[]> featureList = new ArrayList<>();
        List<Integer> frameNumbers = new ArrayList<>();
        List<Integer> serialNumbers = new ArrayList<>();
        List<Integer> idList = new ArrayList<>();
        List<Integer[]> boundingBoxList = new ArrayList<>();
        List<List<List<Float>>> keypointsList = new ArrayList<>();
        List<Long> timestampsList = new ArrayList<>();

        boolean flag = true;
        for (EventBean e : newEvents) {
            // Get frame-level data
            List<DetectedUser> detectedUsers = (List<DetectedUser>) e.get("detectedUsers");
            Integer curFrame = (Integer) e.get("curFrame");
            Long timestamp = (Long) e.get("timestamp");

            lasttimestamp = timestamp;

            // Process each detected user in the frame
            for (DetectedUser user : detectedUsers) {
                List<Float> feature = user.getFeatures();
                Integer id = user.getUNum();

                if (flag) {
                    first_id = id;
                    first_frame = curFrame;
                }
                last_id = id;
                last_frame = curFrame;

                boundingBoxList.add(new Integer[] {
                        user.getX1(), user.getX2(), user.getY1(), user.getY2()
                });

                featureList.add(feature.stream().mapToDouble(Float::doubleValue).toArray());
                keypointsList.add(user.getKeypoints());
                frameNumbers.add(curFrame);
                timestampsList.add(timestamp);
                serialNumbers.add(id);
                idList.add(id);
                flag = false;
            }

        }

        if (featureList.isEmpty())
            return;

        System.out.println(
                "[" + cameraId + "] Processing Window " + windowIndex + " | Frames: " + first_frame + "-" + last_frame);

        // 2. Intra-Window Clustering (Local Tracking)
        List<Integer> newClusterLabels = SCPT.trackingByClustering(featureList, frameNumbers, serialNumbers,
                boundingBoxList, windowIndex, cameraId);

        if (TrackingParameters.isDebug) {
            try {
                java.nio.file.Path dumpDir = java.nio.file.Paths.get(TrackingParameters.OUTPUT_DIR,
                        "after-trackingByClustering");
                java.nio.file.Files.createDirectories(dumpDir);
                java.nio.file.Path filePath = dumpDir.resolve("clusters-java_" + cameraId + "_" + windowIndex + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                    writer.write(newClusterLabels.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 3. Label Shifting (Shift new cluster labels to be uniquely above current max)
        for (int i = 0; i < newClusterLabels.size(); i++) {
            int cluster = newClusterLabels.get(i);
            if (cluster != -1) {
                newClusterLabels.set(i, cluster + maxOfflineId + 1);
            } else {
                newClusterLabels.set(i, -i);
            }
        }
        // NOTE: We no longer update maxOfflineId here.
        // We wait until after association to see which IDs are actually kept.

        // 5. Inter-Window Association (Global Tracking)
        if (windowIndex >= 1) {
            newClusterLabels = SCPT.associateClusterBetweenPeriod(
                    featureList,
                    newClusterLabels,
                    frameNumbers,
                    pastFeatures,
                    pastClusters,
                    pastFrames,
                    TrackingParameters.epsilonScpt);

            if (TrackingParameters.isDebug) {
                String filePath = TrackingParameters.OUTPUT_DIR
                        + "\\after-associateClusterBetweenPeriod\\clusters-java_" + cameraId + "_" + (windowIndex)
                        + ".txt";

                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                    java.nio.file.Files.createDirectories(path.getParent());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                        writer.write(newClusterLabels.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 6. State Update for Next Window
        pastFeatures = new ArrayList<>(featureList);
        pastClusters = new ArrayList<>(newClusterLabels);
        pastFrames = new ArrayList<>(frameNumbers);

        // 7. Post-Processing (NMS, Warp, etc.)
        if (TrackingParameters.sequential_nms) {
            newClusterLabels = ClusteringUtils.sequentialNonMaximumSuppression(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.temporally_snms_th,
                    TrackingParameters.spatially_snms_th,
                    TrackingParameters.merge_nonoverlap);

            if (TrackingParameters.isDebug) {
                String filePath = TrackingParameters.OUTPUT_DIR + "\\after-sequential_nms\\clusters-java_" + cameraId
                        + "_" + (windowIndex) + ".txt";
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                    java.nio.file.Files.createDirectories(path.getParent());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                        writer.write(newClusterLabels.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (TrackingParameters.separate_warp) {
            newClusterLabels = ClusteringUtils.separateWarpTracklet(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.warp_th,
                    TrackingParameters.alpha);

            if (TrackingParameters.isDebug) {
                String filePath = TrackingParameters.OUTPUT_DIR + "\\after-separate_warp\\clusters-java_" + cameraId
                        + "_" + (windowIndex) + ".txt";
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                    java.nio.file.Files.createDirectories(path.getParent());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                        writer.write(newClusterLabels.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (TrackingParameters.exclude_short) {
            newClusterLabels = ClusteringUtils.excludeShortTracklet(
                    newClusterLabels,
                    TrackingParameters.short_tracklet_th);

            if (TrackingParameters.isDebug) {
                String filePath = TrackingParameters.OUTPUT_DIR + "\\after-exclude_short\\clusters-java_" + cameraId
                        + "_" + (windowIndex) + ".txt";
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                    java.nio.file.Files.createDirectories(path.getParent());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                        writer.write(newClusterLabels.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (TrackingParameters.exclude_motionless) {
            newClusterLabels = ClusteringUtils.excludeMotionlessTracklet(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.stop_track_th);

            if (TrackingParameters.isDebug) {
                String filePath = TrackingParameters.OUTPUT_DIR + "\\after-exclude_motionless\\clusters-java_"
                        + cameraId + "_" + (windowIndex) + ".txt";
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                    java.nio.file.Files.createDirectories(path.getParent());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                        writer.write(newClusterLabels.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 8. Update Max Offline ID for the next window
        // This ensures new labels in the next window start strictly after the highest
        // ID currently in our history or current state.
        for (Integer label : newClusterLabels) {
            if (label > maxOfflineId) {
                maxOfflineId = label;
            }
        }

        // 9. Emit SingleCameraResult event
        com.espertech.esper.example.IOT.streams.SingleCameraResult result = new com.espertech.esper.example.IOT.streams.SingleCameraResult(
                cameraId,
                windowIndex,
                lasttimestamp,
                newClusterLabels,
                idList,
                boundingBoxList,
                featureList,
                keypointsList,
                frameNumbers,
                timestampsList);
        EventEPLUtil.streamEvent(result, "SingleCameraResult");

        System.out.println("[" + cameraId + "] Window " + windowIndex + " Finished. Time: "
                + HelperUtils.elapsedMillis(start_time) + " ms");
        System.out.println("first_id: " + first_id + ", last_id: " + last_id);
        System.out.println("--------------------------------------------------------------------------");

        windowIndex += 1;
    }

    public UpdateListener getListener() {
        return (newEvents, oldEvents, statement, runtime) -> {
            processStreamingClusters(newEvents, oldEvents, statement, runtime);
        };
    }
}
