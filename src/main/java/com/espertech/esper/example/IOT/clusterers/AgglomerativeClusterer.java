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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.exit;

public class AgglomerativeClusterer {
    private static final Logger logger = LoggerFactory.getLogger(AgglomerativeClusterer.class);

    private long agglomerative_clustering_time_tracker = 0;
    private final double epsilon;
    private int numberOfClusters = 1;
    Integer number_of_winodws_processed = 1;

    public AgglomerativeClusterer(double epsilon) {
        this.epsilon = epsilon;
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement,
            EPRuntime runtime) {
        Instant start_time = Instant.now();
        Long first_timestamp = 0L;
        long lasttimestamp = 0L;
        Integer first_id = 0;
        Integer last_id = 0;
        if (newEvents != null) {
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
                    flag = false;
                }
                lasttimestamp = timestamp;

                System.out.println("Current Frame Number: " + curFrame);
                // Process each detected user in the frame
                for (DetectedUser user : detectedUsers) {
                    List<Float> feature = user.getFeatures();
                    Integer id = user.getUNum();

                    if (first_id == 0) {
                        first_id = id;
                    }
                    last_id = id;

                    int x1 = user.getX1();
                    int x2 = user.getX2();
                    int y1 = user.getY1();
                    int y2 = user.getY2();
                    boundingBoxList.add(new Integer[] { x1, x2, y1, y2 });

                    featureList.add(feature.stream().mapToDouble(Float::doubleValue).toArray());
                    frameNumbers.add(curFrame);
                    serialNumbers.add(id);
                    idList.add(id);
                    System.out.println(user);

                    // Long frameRecordCount = (Long) e.get("frameRecordCount");
                    // if (numberOfClusters < frameRecordCount) {
                    // numberOfClusters = frameRecordCount.intValue();
                    // }
                    // if (id == 1228) {
                    // break;
                    // }
                }

            }

            double[][] distanceMatrix = SimilarityUtils
                    .computeCosineDistanceMatrix(featureList.toArray(new double[0][]), this.epsilon);
            HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
            int[] clusterLabels = hc.partition(this.epsilon);
            System.out.println("clusterLabels: " + Arrays.toString(clusterLabels));
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            agglomerative_clustering_time_tracker += durationMs;
            System.out.println("Agglomerative Clustering Time: " + agglomerative_clustering_time_tracker + " ms");

            List<Integer> clusterLabelsList = Arrays.stream(clusterLabels)
                    .boxed()
                    .collect(Collectors.toList());

            if (TrackingParameters.isDebug) {
                // This code snippet saves the distance matrix and frame numbers to CSV files to
                // be compared with original code.
                try {
                    debug.saveDoubleMatrix(TrackingParameters.OUTPUT_DIR + "\\distance_matrix.csv", distanceMatrix);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
                try {
                    debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\cluster_labels.txt", clusterLabelsList);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            List<Integer> newClusterLabels = ClusteringUtils.tracking_by_clustering(
                    distanceMatrix,
                    frameNumbers,
                    serialNumbers,
                    clusterLabelsList,
                    this.epsilon);

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

            Map<Integer, List<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < clusterLabels.length; i++) {
                int label = newClusterLabels.get(i);
                int id = idList.get(i);
                clusters.computeIfAbsent(label, k -> new ArrayList<>()).add(id);
            }

            // 2. Print each cluster in order
            clusters.keySet().stream()
                    .sorted()
                    .forEach(label -> {
                        List<Integer> members = clusters.get(label);
                        System.out.printf("Cluster %d: %s%n", label, members);
                    });
        }
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("Window period: " + (lasttimestamp - first_timestamp));
        System.out.println("First ID: " + first_id + " And Last Id: " + last_id);
        System.out.println("Time taken: " + HelperUtils.elapsedMillis(start_time) + " ms");
        System.out.println("--------------------------------------------------------------------------");
        if (TrackingParameters.isDebug
                && (number_of_winodws_processed >= TrackingParameters.max_number_of_windows_to_process)) {
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
