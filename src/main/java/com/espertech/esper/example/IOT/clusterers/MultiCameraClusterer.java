package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.events.LocalTrackEvent;
import com.espertech.esper.example.IOT.events.GlobalTrackEvent;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.util.*;
import java.util.stream.Collectors;

public class MultiCameraClusterer {
    private static final Logger logger = LoggerFactory.getLogger(MultiCameraClusterer.class);
    private final double epsilon;
    private int globalIdCounter = 0;

    public MultiCameraClusterer(double epsilon) {
        this.epsilon = epsilon;
    }

    public UpdateListener getListener() {
        return (newEvents, oldEvents, statement, runtime) -> {
            processMultiCameraClusters(newEvents, runtime);
        };
    }

    private void processMultiCameraClusters(EventBean[] newEvents, EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0)
            return;

        List<LocalTrackEvent> trackEvents = new ArrayList<>();
        for (EventBean event : newEvents) {
            Object underlying = event.getUnderlying();
            if (underlying instanceof LocalTrackEvent) {
                trackEvents.add((LocalTrackEvent) underlying);
            }
        }

        if (trackEvents.isEmpty())
            return;

        logger.info("Processing {} local tracks from multiple cameras.", trackEvents.size());

        // 1. Calculate Pairwise Distances with Overlap Suppression
        // Since we are clustering tracklets, we need to compute distance between
        // tracklet A and tracklet B.
        // We use average linkage or just distance between centroids (average features)
        // if available?
        // Let's assume average feature vector is sufficient for now.
        // But LocalTrackEvent has List<double[]> features. We should compute mean
        // feature first.

        List<double[]> meanFeatures = new ArrayList<>();
        for (LocalTrackEvent track : trackEvents) {
            meanFeatures.add(computeMeanFeature(track.getFeatures()));
        }

        double[][] distanceMatrix = new double[trackEvents.size()][trackEvents.size()];
        for (int i = 0; i < trackEvents.size(); i++) {
            for (int j = 0; j < i; j++) {
                double dist = SimilarityUtils.cosineDistance(meanFeatures.get(i), meanFeatures.get(j));

                // OVERLAP SUPPRESSION LOGIC
                // If tracks are from the SAME camera and overlap in time, distance is INFINITE.
                LocalTrackEvent t1 = trackEvents.get(i);
                LocalTrackEvent t2 = trackEvents.get(j);

                if (t1.getCameraId().equals(t2.getCameraId())) {
                    // Check time overlap
                    // Simplistic check: if their window overlaps.
                    // Since we are processing a batch window of events, they likely all overlap in
                    // the large window.
                    // But strictly, tracks from the same camera in the same batch SHOULD be
                    // distinct people
                    // (since SCMT already clustered them).
                    // So we should ALWAYS suppress merging tracks from the same camera in the same
                    // batch?
                    // Yes, SCMT says "these are different people". We shouldn't merge them back.
                    dist = 1000.0;
                }

                if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
                    logger.info("Dist Calc: {} (Cam {}) vs {} (Cam {}): CosSim={}, FinalDist={}",
                            i, t1.getCameraId(), j, t2.getCameraId(),
                            SimilarityUtils.cosineSimilarity(meanFeatures.get(i), meanFeatures.get(j)),
                            dist);
                }

                distanceMatrix[i][j] = dist;
                distanceMatrix[j][i] = dist;
            }
        }

        // 2. Hierarchical Clustering
        if (trackEvents.size() > 1) {
            // Flatten matrix for Smile (lower triangular) - wait, Smile takes full matrix
            // or triangular?
            // Smile's HierarchicalClustering.fit takes a triangular distance matrix
            // (double[])?
            // Or can take a square raw matrix?
            // Checking AgglomerativeClusterer usage:
            // HierarchicalClustering hc = HierarchicalClustering.fit(new
            // SingleLinkage(distanceMatrix));
            // SingleLinkage takes double[][] proximity.

            HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
            int[] clusterLabels = hc.partition(this.epsilon);

            if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
                logger.info("Epsilon: {}", this.epsilon);
                logger.info("Cluster Labels: {}", Arrays.toString(clusterLabels));
            }

            Map<Integer, List<LocalTrackEvent>> clusters = new HashMap<>();
            for (int i = 0; i < clusterLabels.length; i++) {
                clusters.computeIfAbsent(clusterLabels[i], k -> new ArrayList<>()).add(trackEvents.get(i));
            }

            // 3. Emit Global Tracks
            long currentTimestamp = System.currentTimeMillis();
            for (Map.Entry<Integer, List<LocalTrackEvent>> entry : clusters.entrySet()) {
                int clusterLabel = entry.getKey(); // This is local to this batch clustering
                List<LocalTrackEvent> members = entry.getValue();

                // Assign a global ID.
                // Ideally we match this cluster to previous global clusters.
                // For this iteration, we simplified to just assigning new IDs or hashing.
                // Let's just increment a counter for demonstration or use the lowest uniqueId
                // from members if consistent?
                // Let's use a simple counter for now, but to avoid explosion we might want to
                // map it.
                // Actually, if we don't link across batches, the ID is useless.
                // However, "Offline" tracking implies we have all data. "Real-time" implies we
                // need state.
                // Let's assume for this step we just output the cluster.

                // Simple Strategy: Use the minimum uniqueId from the members (which comes from
                // the dataset ground truth usually?
                // Wait, detectedUser.getUNum() is likely the ground truth ID or detection ID?)
                // If it's ground truth, we can use it to verify. If it's just detection ID,
                // it's ephemeral.
                // Let's generate a Global ID.

                int globalId = ++globalIdCounter;

                GlobalTrackEvent globalEvent = new GlobalTrackEvent(
                        globalId,
                        members,
                        currentTimestamp);

                logger.info("Generated Global Cluster ID {}: Members: {}", globalEvent.getGlobalId(),
                        members.stream().map(m -> m.getCameraId() + ":" + m.getLocalTrackId())
                                .collect(Collectors.joining(", ")));

                runtime.getEventService().sendEventBean(globalEvent, "GlobalTrackEvent");
                exportTrackingResults(Collections.singletonList(globalEvent));
            }

        } else {
            // Single track, just emit it as a new global track
            GlobalTrackEvent globalEvent = new GlobalTrackEvent(
                    ++globalIdCounter,
                    trackEvents,
                    System.currentTimeMillis());
            runtime.getEventService().sendEventBean(globalEvent, "GlobalTrackEvent");
            exportTrackingResults(Collections.singletonList(globalEvent));
        }
    }

    private void exportTrackingResults(List<GlobalTrackEvent> globalTracks) {
        if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
            // Keep existing CSV export
            try (java.io.FileWriter fw = new java.io.FileWriter(
                    com.espertech.esper.example.IOT.helpers.TrackingParameters.OUTPUT_DIR + "/global_tracks.csv",
                    true)) {
                for (GlobalTrackEvent gt : globalTracks) {
                    int globalId = gt.getGlobalId();
                    for (LocalTrackEvent lt : gt.getConstituentTracks()) {
                        String cam = lt.getCameraId();
                        if (lt.getDetectedUsers() != null) {
                            for (com.espertech.esper.example.IOT.utils.DetectedUser user : lt.getDetectedUsers()) {
                                fw.write(String.format("%d,%s,%d,%d,%d,%d,%d\n",
                                        user.getFrameNumber(),
                                        cam, globalId, user.getX1(), user.getX2(), user.getY1(), user.getY2()));
                            }
                        }
                    }
                }
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }

        if (com.espertech.esper.example.IOT.helpers.TrackingParameters.enable_visualization_export) {
            // Group by Camera -> Frame -> Detections
            // We use temp class or lists to hold data before exporting
            Map<String, Map<Integer, List<com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection>>> data = new HashMap<>();

            for (GlobalTrackEvent gt : globalTracks) {
                // int globalId = gt.getGlobalId(); // Not used for single-camera viz
                for (LocalTrackEvent lt : gt.getConstituentTracks()) {
                    String cam = lt.getCameraId();
                    int localTrackId = lt.getLocalTrackId(); // Use localTrackId for per-person tracking within a camera
                    if (lt.getDetectedUsers() != null) {
                        for (com.espertech.esper.example.IOT.utils.DetectedUser user : lt.getDetectedUsers()) {
                            int frame = user.getFrameNumber();
                            int[] bbox = new int[] { user.getX1(), user.getX2(), user.getY1(), user.getY2() };

                            data.computeIfAbsent(cam, k -> new HashMap<>())
                                    .computeIfAbsent(frame, k -> new ArrayList<>())
                                    .add(new com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection(
                                            localTrackId, bbox));
                        }
                    }
                }
            }

            // Export to files
            for (Map.Entry<String, Map<Integer, List<com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection>>> camEntry : data
                    .entrySet()) {
                String cam = camEntry.getKey();
                String camDir = com.espertech.esper.example.IOT.helpers.TrackingParameters.visualization_output_dir
                        + "/" + cam;

                for (Map.Entry<Integer, List<com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection>> frameEntry : camEntry
                        .getValue().entrySet()) {
                    int frame = frameEntry.getKey();
                    List<com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection> dets = frameEntry
                            .getValue();

                    List<Integer> tIds = new ArrayList<>();
                    List<Integer[]> bboxes = new ArrayList<>();
                    for (com.espertech.esper.example.IOT.helpers.TrackingResultExporter.Detection d : dets) {
                        tIds.add(d.track_id);
                        bboxes.add(new Integer[] { d.bbox[0], d.bbox[1], d.bbox[2], d.bbox[3] });
                    }

                    try {
                        com.espertech.esper.example.IOT.helpers.TrackingResultExporter.exportFrameToJSON(frame, tIds,
                                bboxes, camDir);
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private double[] computeMeanFeature(List<double[]> features) {
        if (features == null || features.isEmpty())
            return new double[0];
        int dim = features.get(0).length;
        double[] mean = new double[dim];
        for (double[] f : features) {
            for (int k = 0; k < dim; k++) {
                mean[k] += f[k];
            }
        }
        for (int k = 0; k < dim; k++) {
            mean[k] /= features.size();
        }
        return mean;
    }
}
