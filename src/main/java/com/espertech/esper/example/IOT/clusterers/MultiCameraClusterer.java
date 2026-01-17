package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.events.LocalTrackEvent;
import com.espertech.esper.example.IOT.events.GlobalTrackEvent;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

public class MultiCameraClusterer {
    private static final Logger logger = LoggerFactory.getLogger(MultiCameraClusterer.class);
    private final double epsilon;
    private int globalIdCounter = 0;

    public MultiCameraClusterer(double epsilon) {
        this.epsilon = epsilon;
        // Clean output file on startup
        if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
            try {
                java.io.File file = new java.io.File(
                        com.espertech.esper.example.IOT.helpers.TrackingParameters.OUTPUT_DIR + "/global_tracks.csv");
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public UpdateListener getListener() {
        return (newEvents, oldEvents, statement, runtime) -> {
            logger.info("MultiCameraClusterer onUpdate called with {} events",
                    newEvents != null ? newEvents.length : 0);
            if (newEvents == null) {
                return;
            }
            processMultiCameraClusters(newEvents, runtime);
        };
    }

    private void processMultiCameraClusters(EventBean[] newEvents, EPRuntime runtime) {
        if (newEvents == null || newEvents.length == 0)
            return;

        System.out.println("DEBUG: MultiCameraClusterer received " + newEvents.length + " tracklets.");
        if (newEvents.length <= 1)
            return; // Not clustering a single tracklet
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

        performClustering(trackEvents, runtime);
    }

    public void performClustering(List<LocalTrackEvent> trackEvents, EPRuntime runtime) {
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
            meanFeatures.add(SimilarityUtils.computeMeanFeature(track.getFeatures()));
            // Compute world coordinates for each detection if not already present
            computeWorldCoordinates(track);
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
                    // Overlap suppression for same camera
                    dist = 1000.0;
                } else if (com.espertech.esper.example.IOT.helpers.TrackingParameters.replaceSimilarityByWCoordinate) {
                    // Proximity check for different cameras
                    double worldDist = computeWorldDistance(t1, t2);
                    if (worldDist >= 0
                            && worldDist > com.espertech.esper.example.IOT.helpers.TrackingParameters.distanceTh) {
                        dist = 1000.0; // High distance to suppress clustering
                    }
                }

                if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
                    logger.info("Dist Calc: {} (Cam {}) vs {} (Cam {}): CosSim={}, FinalDist={}",
                            i, t1.getCameraId(), j, t2.getCameraId(),
                            com.espertech.esper.example.IOT.helpers.SimilarityUtils
                                    .cosineSimilarity(meanFeatures.get(i), meanFeatures.get(j)),
                            dist);
                }

                distanceMatrix[i][j] = dist;
                distanceMatrix[j][i] = dist;
            }
        }

        if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
            logger.info("Distance Matrix: {}", java.util.Arrays.deepToString(distanceMatrix));
        }

        // 2. Perform Hierarchical Clustering
        if (trackEvents.size() > 1) {
            // Custom BFS Clustering to handle disconnected graph robustly
            // 1. Initialize all labels to -1
            int[] clusterLabels = new int[trackEvents.size()];
            Arrays.fill(clusterLabels, -1);
            int currentClusterId = 0;

            for (int i = 0; i < trackEvents.size(); i++) {
                if (clusterLabels[i] != -1)
                    continue; // Already visited

                // Start new cluster
                clusterLabels[i] = currentClusterId;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(i);

                while (!queue.isEmpty()) {
                    int u = queue.poll();

                    // Find neighbors
                    for (int v = 0; v < trackEvents.size(); v++) {
                        // Check if unvisited and within distance threshold
                        if (clusterLabels[v] == -1 && distanceMatrix[u][v] <= this.epsilon) {

                            // CONFLICT CHECK: Does 'v' conflict with any EXISTING member of
                            // currentClusterId?
                            // Conflict = Same Camera AND Time Overlap
                            boolean conflict = false;
                            for (int k = 0; k < trackEvents.size(); k++) {
                                if (clusterLabels[k] == currentClusterId) {
                                    LocalTrackEvent m1 = trackEvents.get(k);
                                    LocalTrackEvent m2 = trackEvents.get(v);

                                    if (m1.getCameraId().equals(m2.getCameraId())) {
                                        // Overlap Check: max(start1, start2) < min(end1, end2)
                                        long start = Math.max(m1.getStartTime(), m2.getStartTime());
                                        long end = Math.min(m1.getEndTime(), m2.getEndTime());

                                        if (start < end) {
                                            conflict = true;
                                            if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
                                                logger.info(
                                                        "Conflict Detected! Ignoring merge of {} and {} (Cam {}, ID {}). Overlap: {}-{}",
                                                        u, v, m1.getCameraId(), currentClusterId, start, end);
                                            }
                                            break;
                                        } else {
                                            if (com.espertech.esper.example.IOT.helpers.TrackingParameters.isDebug) {
                                                logger.info(
                                                        "No Overlap for same camera: {} vs {} (Cam {}). {}-{} vs {}-{}",
                                                        u, v, m1.getCameraId(), m1.getStartTime(), m1.getEndTime(),
                                                        m2.getStartTime(), m2.getEndTime());
                                            }
                                        }
                                    }
                                }
                            }

                            if (!conflict) {
                                clusterLabels[v] = currentClusterId;
                                queue.add(v);
                            }
                        }
                    }
                }
                currentClusterId++;
            }

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

        } else

        {
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

    private void computeWorldCoordinates(LocalTrackEvent track) {
        String camIdStr = track.getCameraId().replace("camera_", "");
        int camId = Integer.parseInt(camIdStr);
        for (com.espertech.esper.example.IOT.utils.DetectedUser user : track.getDetectedUsers()) {
            if (!user.hasWorldCoords()) {
                try {
                    double centerX = (user.getX1() + user.getX2()) / 2.0;
                    double bottomY = user.getY2();
                    double[] world = com.espertech.esper.example.IOT.helpers.HomographyManager.toWorldCoordinates(camId,
                            centerX, bottomY);
                    if (world != null) {
                        user.setWorldX(world[0]);
                        user.setWorldY(world[1]);
                    }
                } catch (java.io.IOException e) {
                    logger.warn(
                            "World coordinates unavailable for camera " + camId + " (calibration file likely missing)");
                }
            }
        }
    }

    private double computeWorldDistance(LocalTrackEvent t1, LocalTrackEvent t2) {
        Map<Integer, double[]> frames1 = new HashMap<>();
        for (com.espertech.esper.example.IOT.utils.DetectedUser u : t1.getDetectedUsers()) {
            if (u.hasWorldCoords()) {
                frames1.put(u.getFrameNumber(), new double[] { u.getWorldX(), u.getWorldY() });
            }
        }

        if (frames1.isEmpty()) {
            return -1.0; // Signal that world coordinates are missing for T1
        }

        List<Double> distances = new ArrayList<>();
        for (com.espertech.esper.example.IOT.utils.DetectedUser u : t2.getDetectedUsers()) {
            if (u.hasWorldCoords() && frames1.containsKey(u.getFrameNumber())) {
                double[] p1 = frames1.get(u.getFrameNumber());
                double dx = p1[0] - u.getWorldX();
                double dy = p1[1] - u.getWorldY();
                distances.add(Math.sqrt(dx * dx + dy * dy));
            }
        }

        if (distances.isEmpty()) {
            // Check if T2 even has world coordinates
            boolean t2HasCoords = false;
            for (com.espertech.esper.example.IOT.utils.DetectedUser u : t2.getDetectedUsers()) {
                if (u.hasWorldCoords()) {
                    t2HasCoords = true;
                    break;
                }
            }
            if (!t2HasCoords) {
                return -1.0; // Signal that world coordinates are missing for T2
            }
            return 0.0; // No common frames, but both have coords (don't suppress)
        }

        String type = com.espertech.esper.example.IOT.helpers.TrackingParameters.distanceType;
        if ("min".equals(type)) {
            return Collections.min(distances);
        } else if ("max".equals(type)) {
            return Collections.max(distances);
        } else {
            // Average
            double sum = 0;
            for (double d : distances)
                sum += d;
            return sum / distances.size();
        }
    }

}
