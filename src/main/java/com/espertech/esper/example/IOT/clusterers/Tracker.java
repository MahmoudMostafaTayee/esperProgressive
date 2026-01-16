package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.*;
import com.espertech.esper.example.IOT.utils.DetectedUser;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import java.io.BufferedWriter;
import java.io.FileWriter;
//import java.io.IOException;
//import java.util.List;

import static java.lang.System.exit;

public class Tracker {
    private static final Logger logger = LoggerFactory.getLogger(Tracker.class);

    private long agglomerative_clustering_time_tracker = 0;
    private int numberOfClusters = 1;
    Integer number_of_winodws_processed = 0;

    // State for association across windows
    private List<double[]> pastFeatures = new ArrayList<>();
    private List<Integer> pastFrames = new ArrayList<>();
    private List<Integer> pastClusters = new ArrayList<>();
    private int maxOfflineId = -1;

    public Tracker() {
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0) return;

        Instant start_time = Instant.now();
        Long first_timestamp = 0L;
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

        long start = System.nanoTime();
        boolean flag = true;

        for (EventBean e : newEvents) {
            // Get frame-level data
            List<DetectedUser> detectedUsers = (List<DetectedUser>) e.get("detectedUsers");
            Integer curFrame = (Integer) e.get("curFrame");
            Long timestamp = (Long) e.get("timestamp");

            if (flag) {
                first_timestamp = timestamp;
            }
            lasttimestamp = timestamp;

            System.out.println("Current Frame Number: " + curFrame);
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
                frameNumbers.add(curFrame);
                serialNumbers.add(id);
                idList.add(id);
                System.out.println(user);
                flag = false;
            }

        }

        if (featureList.isEmpty()) return;

        // 2. Intra-Window Clustering (Local Tracking)
        List<Integer> newClusterLabels = SCPT.trackingByClustering(featureList, frameNumbers, serialNumbers,
                boundingBoxList, number_of_winodws_processed);

        if (TrackingParameters.isDebug) {
            System.out.println("clusterLabels after trackingByClustering: " + newClusterLabels);
            String filePath = "C:\\OURs\\Thesis\\dumps\\after-trackingByClustering\\clusters-java_" + (number_of_winodws_processed) + ".txt";

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
//                writer.write("clusterLabels after trackingByClustering: ");
                writer.write(newClusterLabels.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 3. Label Shifting (Global ID Generation)
        // Shift cluster labels to ensure global uniqueness and temporal continuity
        // Matches Python logic: clusters = [cluster+max_offlineid+1 if cluster != -1
        // else -i ...]
        for (int i = 0; i < newClusterLabels.size(); i++) {
            int cluster = newClusterLabels.get(i);
            if (cluster != -1) {
                newClusterLabels.set(i, cluster + maxOfflineId + 1);
            } else {
                newClusterLabels.set(i, -i);
            }
        }

        // 4. Update Max Global ID
        int currentMax = -1;
        for (Integer label : newClusterLabels) {
            if (label > currentMax) {
                currentMax = label;
            }
        }
        if (currentMax > maxOfflineId) {
            maxOfflineId = currentMax;
        }

        // 5. Inter-Window Association (Global Tracking)
        // Associate with past window if available
        if (number_of_winodws_processed >= 1) {
            newClusterLabels = SCPT.associateClusterBetweenPeriod(
                    featureList,
                    newClusterLabels,
                    frameNumbers,
                    pastFeatures,
                    pastClusters,
                    pastFrames,
                    TrackingParameters.epsilonScpt);
            if (TrackingParameters.isDebug) {
                System.out.println("pastClusters: " + pastClusters);
                System.out.println("clusterLabels after associateClusterBetweenPeriod: " + newClusterLabels);
            }
        }

        // 6. State Update for Next Window
        // Store current data as past data for the next window
        pastFeatures = new ArrayList<>(featureList);
        pastClusters = new ArrayList<>(newClusterLabels);
        pastFrames = new ArrayList<>(frameNumbers);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        agglomerative_clustering_time_tracker += durationMs;

        if (TrackingParameters.isDebug) {
            // This code snippet saves the distance matrix and frame numbers to CSV files to
            // be compared with original code.
            // try {
            // debug.saveDoubleMatrix(TrackingParameters.OUTPUT_DIR +
            // "\\distance_matrix.csv", distanceMatrix);
            // } catch (IOException e) {
            // throw new RuntimeException(e);
            // }
            try {
                debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\frame_numbers.txt", frameNumbers);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\serial_numbers.txt", serialNumbers);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // try {
            // debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\cluster_labels.txt",
            // clusterLabelsList);
            // } catch (IOException e) {
            // throw new RuntimeException(e);
            // }
        }

        if (TrackingParameters.sequential_nms) {
            newClusterLabels = ClusteringUtils.sequentialNonMaximumSuppression(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.temporally_snms_th,
                    TrackingParameters.spatially_snms_th,
                    TrackingParameters.merge_nonoverlap);
        }

        if (TrackingParameters.separate_warp) {
            newClusterLabels = ClusteringUtils.separateWarpTracklet(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.warp_th,
                    TrackingParameters.alpha);
        }

        if (TrackingParameters.exclude_short) {
            newClusterLabels = ClusteringUtils.excludeShortTracklet(
                    newClusterLabels,
                    TrackingParameters.short_tracklet_th);
        }

        if (TrackingParameters.exclude_motionless) {
            newClusterLabels = ClusteringUtils.excludeMotionlessTracklet(
                    newClusterLabels,
                    frameNumbers,
                    boundingBoxList,
                    TrackingParameters.stop_track_th);
        }

        System.out.println("newClusterLabels: " + Arrays.toString(newClusterLabels.toArray()));

        // Map<Integer, List<Integer>> clusters = new HashMap<>();
        // for (int i = 0; i < clusterLabels.length; i++) {
        // int label = newClusterLabels.get(i);
        // int id = idList.get(i);
        // clusters.computeIfAbsent(label, k -> new ArrayList<>()).add(id);
        // }
        //
        // // 2. Print each cluster in order
        // clusters.keySet().stream()
        // .sorted()
        // .forEach(label -> {
        // List<Integer> members = clusters.get(label);
        // System.out.printf("Cluster %d: %s%n", label, members);
        // });
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("Window period: " + (lasttimestamp - first_timestamp));
        System.out.println("First ID: " + first_id + " And Last Id: " + last_id);
        System.out.println("First Frame: " + first_frame + " And Last Frame: " + last_frame);
        System.out.println("Time taken: " + HelperUtils.elapsedMillis(start_time) + " ms");
        System.out.println("--------------------------------------------------------------------------");
        if (TrackingParameters.isDebug
                && ((number_of_winodws_processed+1) >= TrackingParameters.max_number_of_windows_to_process)) {
            exit(0);
        }
        number_of_winodws_processed += 1;
    }

    public UpdateListener getListener() {
        return (newEvents, oldEvents, statement, runtime) -> {
            processStreamingClusters(newEvents, oldEvents, statement, runtime);
        };
    }
}
