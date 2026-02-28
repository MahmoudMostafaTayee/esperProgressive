/*
    mvn clean install -Dcheckstyle.skip=true
    mvn clean install -U -DskipTests

 */
package com.espertech.esper.example.IOT;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.ErrorCode;
import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import com.espertech.esper.example.IOT.streamers.*;
import com.espertech.esper.example.IOT.streams.*;
import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.example.IOT.utils.GenericIotEventListener;
import com.espertech.esper.example.IOT.utils.TableSocketServer;
import com.google.gson.Gson;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.espertech.esper.example.IOT.clusterers.GlobalTrackState;

import com.espertech.esper.example.IOT.clusterers.Tracker;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.StringJoiner;

public class IotMain implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IotMain.class);

    public IotMain(String runtimeURI) {
        EventEPLUtil.setRuntimeURI(runtimeURI);
    }

    public static void main(String[] args) {
        ErrorCode retval = TrackingParameters.getTrackingParams(args);

        TrackingParameters.printArgs();

        if (retval == ErrorCode.SUCCESS) {
            new IotMain("IotEventRuntime").run();
        } else {
            logger.error("Error Code: " + retval.getCode() + " - " + retval.getMessage());
        }

    }

    /**
     * Initiates the Esper runtime with the provided runtime URI and configuration.
     * Gets the runtime from the
     * configuration and initializes it.
     */
    private static final java.util.List<String> AVAILABLE_CAMERAS = java.util.Arrays.asList(
            "camera_0001", "camera_0002", "camera_0003", "camera_0004");

    private java.util.List<String> cameraList;
    private final TableSocketServer socketServer = new TableSocketServer(9999);
    private final Gson gson = new Gson();

    public static final java.util.Map<Integer, GlobalTrackState> globalTrackRegistry = new ConcurrentHashMap<>();
    public static final AtomicInteger nextGlobalId = new AtomicInteger(1);

    private void initiateRunTime() {
        EventEPLUtil.setConfiguration();
        EventEPLUtil.addEventType("personView", PersonView.class);
        EventEPLUtil.addEventType("sensorData", SensorData.class);
        EventEPLUtil.addEventType("deviceCommand", DeviceCommand.class);

        // Dynamic camera registration
        cameraList = getCameraList();
        for (String camera : cameraList) {
            EventEPLUtil.addEventType("embeddingFeature" + "_" + camera, EmbeddingFeature.class);
        }

        EventEPLUtil.addEventType("PersonTracker", PersonTracker.class);
        EventEPLUtil.addEventType("TriggerEvent", TriggerEvent.class);

        // Register SingleCameraResult event
        EventEPLUtil.addEventType("SingleCameraResult",
                com.espertech.esper.example.IOT.streams.SingleCameraResult.class);

        // Register CameraCalibration event
        EventEPLUtil.addEventType("CameraCalibration",
                com.espertech.esper.example.IOT.streams.CameraCalibration.class);

        // Register GlobalPersonEvent event
        EventEPLUtil.addEventType("GlobalPersonEvent",
                com.espertech.esper.example.IOT.streams.GlobalPersonEvent.class);

        // Register TrackExpiredEvent
        EventEPLUtil.addEventType("TrackExpiredEvent",
                com.espertech.esper.example.IOT.streams.TrackExpiredEvent.class);

        // Register CameraTopology
        EventEPLUtil.addEventType("CameraTopology",
                com.espertech.esper.example.IOT.streams.CameraTopology.class);

        logger.info("Setting up runtime");
        EventEPLUtil.initiateRuntime();
    }

    private java.util.List<String> getCameraList() {
        java.util.List<String> cameras = new java.util.ArrayList<>();
        String filter = TrackingParameters.CAMERA_FILTER;

        if (filter.equalsIgnoreCase("all")) {
            return new java.util.ArrayList<>(AVAILABLE_CAMERAS);
        }

        String[] parts = filter.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty())
                continue;

            // Normalize "1" -> "camera_0001" or "camera_01" -> "camera_0001"
            // We assume standard format "camera_XXXX" where X is digit.
            if (token.matches("\\d+")) {
                int id = Integer.parseInt(token);
                cameras.add(String.format("camera_%04d", id));
            } else if (!token.startsWith("camera_")) {
                cameras.add("camera_" + token);
            } else {
                cameras.add(token);
            }
        }

        if (cameras.isEmpty()) {
            logger.warn("No valid cameras found in filter: " + filter + ". Defaulting to all.");
            return new java.util.ArrayList<>(AVAILABLE_CAMERAS);
        }

        logger.info("Selected cameras: " + cameras);
        return cameras;
    }

    /**
     * Adds a generator to send events to the runtime.
     */
    private void launchStreams() {
        logger.info("Generating and sending events with time advancement");
        // SomeExamplesStreamer.streamSomeExamples();
        // WildTrackDatasetStreamer.streamWildTrackDataset();
        EmbeddingFeatureStreamer.streamEmbeddingFeatures();
    }

    public void run() {
        initiateRunTime();
        socketServer.start();

        // Add shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, stopping socket server and Esper runtime...");
            socketServer.stop();
            EventEPLUtil.destroyRuntime();
        }));

        // someExampleQueries();
        // wildTrackDatasetQueries();

        prepareCalibrationQueries();
        prepareTopologyQueries();
        embeddingFeatureQueries();
        multiCameraAggregationQueries();
        afterClusteringQueries();

        socketServer.waitForFirstClient();
        launchStreams();

        EmbeddingFeatureStreamer.waitForCompletion();

        // Give a small grace period for the final broadcasts to settle
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }

        socketServer.stop();
        EventEPLUtil.destroyRuntime();
        logger.info("Done.");
    }

    // Global map to hold calibration data
    public static final java.util.Map<Integer, double[][]> calibrationMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void prepareCalibrationQueries() {
        // Create an Esper Table for calibration
        String createTableEPL = "create table CalibrationTable (cameraId int primary key, homographyMatrix Object);";
        EventEPLUtil.addEpl(createTableEPL);

        // Insert incoming CameraCalibration events into the table
        String insertEPL = "insert into CalibrationTable select cameraId, homographyMatrix from CameraCalibration;";
        EventEPLUtil.addEpl(insertEPL);

        // Listener to keep local map updated (Optional, or just select from table when
        // needed)
        // For efficiency in the heavy MCPT loop, a local concurrent map is faster than
        // querying the engine every time.
        // We can listen to the stream itself to update our map.
        String updateMapEPL = "select * from CameraCalibration";
        EventEPLUtil.compileDeployAddListener(updateMapEPL, (newEvents, oldEvents, statement, runtime) -> {
            if (newEvents != null) {
                for (EventBean event : newEvents) {
                    com.espertech.esper.example.IOT.streams.CameraCalibration cc = (com.espertech.esper.example.IOT.streams.CameraCalibration) event
                            .getUnderlying();
                    calibrationMap.put(cc.getCameraId(), cc.getHomographyMatrix());
                    logger.info("Updated calibration map for camera " + cc.getCameraId());
                }
            }
        });
    }

    private void prepareTopologyQueries() {
        // Create an Esper Table for Camera Topology (Neighborhood Graph)
        // This allows us to query which cameras are neighbors at runtime.
        String createTableEPL = "create table CameraTopologyTable (cameraId string primary key, neighborId string primary key, enabled boolean);";
        EventEPLUtil.addEpl(createTableEPL);
        EventEPLUtil.addEpl("create index CameraTopologyNeighborIndex on CameraTopologyTable (neighborId);");

        // Insert incoming CameraTopology events into the table to allow runtime updates
        String insertEPL = "insert into CameraTopologyTable select cameraId, neighborId, enabled from CameraTopology;";
        EventEPLUtil.addEpl(insertEPL);

        // Initial population from static groups to bootstrap the "Graph"
        String groupsConfig = TrackingParameters.CAMERA_GROUPS;
        if (!groupsConfig.equalsIgnoreCase("all")) {
            String[] groupStrings = groupsConfig.split(";");
            for (String groupStr : groupStrings) {
                String[] parts = groupStr.split(",");
                for (int i = 0; i < parts.length; i++) {
                    for (int j = i + 1; j < parts.length; j++) {
                        String camI = normalizeCameraName(parts[i].trim());
                        String camJ = normalizeCameraName(parts[j].trim());
                        // Add bidirectional links
                        EventEPLUtil.addEpl(
                                "insert into CameraTopologyTable values ('" + camI + "', '" + camJ + "', true)");
                        EventEPLUtil.addEpl(
                                "insert into CameraTopologyTable values ('" + camJ + "', '" + camI + "', true)");
                    }
                }
            }
        }

        logger.info("Initialized CameraTopologyTable with reconfigurable neighborhood data.");
    }

    private String normalizeCameraName(String token) {
        if (token.matches("\\d+")) {
            return String.format("camera_%04d", Integer.parseInt(token));
        } else if (!token.startsWith("camera_")) {
            return "camera_" + token;
        }
        return token;
    }

    private void embeddingFeatureQueries() {
        for (String camera : cameraList) {
            String featureBatchEPL = "select detectedUsers, curFrame, timestamp " +
                    "from embeddingFeature_" + camera + ".win:length_batch( "
                    + TrackingParameters.fps * TrackingParameters.timePeriod + " )";

            Tracker cameraTracker = new Tracker(camera);
            EventEPLUtil.compileDeployAddListener(featureBatchEPL, cameraTracker.getListener());
            logger.info("Deployed Tracker for " + camera);
        }
    }

    private void multiCameraAggregationQueries() {
        // Aggregate SingleCameraResult events from specific camera groups

        String groupsConfig = TrackingParameters.CAMERA_GROUPS;
        java.util.List<java.util.List<String>> groups = new java.util.ArrayList<>();

        if (groupsConfig.equalsIgnoreCase("all")) {
            groups.add(cameraList);
            logger.info("Aggregation Group: All " + cameraList.size() + " cameras: " + cameraList);
        } else {
            String[] groupStrings = groupsConfig.split(";");
            for (String groupStr : groupStrings) {
                java.util.List<String> group = new java.util.ArrayList<>();
                String[] parts = groupStr.split(",");
                for (String part : parts) {
                    String token = part.trim();
                    if (token.isEmpty())
                        continue;

                    String camName;
                    if (token.matches("\\d+")) {
                        int id = Integer.parseInt(token);
                        camName = String.format("camera_%04d", id);
                    } else if (!token.startsWith("camera_")) {
                        camName = "camera_" + token;
                    } else {
                        camName = token;
                    }

                    // Only add if it's in the active camera list (streamed)
                    if (cameraList.contains(camName)) {
                        group.add(camName);
                    } else {
                        logger.warn("Camera " + camName + " in group [" + groupStr
                                + "] is not in active camera list. Skipping.");
                    }
                }
                if (!group.isEmpty()) {
                    groups.add(group);
                    logger.info("Aggregation Group: " + group);
                }
            }
        }

        if (groups.isEmpty()) {
            logger.warn("No valid aggregation groups found!");
            return;
        }

        int groupIndex = 0;
        for (java.util.List<String> group : groups) {
            final int currentGroupIndex = groupIndex++;
            int numCameras = group.size();

            // Build camera ID list for IN clause: 'camera_0001', 'camera_0002'
            StringJoiner joiner = new StringJoiner("', '", "'", "'");
            for (String cam : group) {
                joiner.add(cam);
            }
            String cameraInClause = joiner.toString();

            System.out.println(">>> DEPLOYING AGGREGATION QUERY FOR GROUP " + currentGroupIndex + ": " + group);

            // Unique aggregation name for each group to avoid collisions if needed (though
            // EPL prevents duplicats usually context/name)
            // We use the filter in the FROM clause
            String aggregationEPL = "select window( * ) as results " +
                    "from SingleCameraResult(cameraId in (" + cameraInClause + ")).win:time(2 min) " +
                    "group by windowIndex " +
                    "having count(*) = " + numCameras + " " +
                    "output first every 59 seconds"; // Output once per windowIndex when ready

            EventEPLUtil.compileDeployAddListener(aggregationEPL, (newEvents, oldEvents, statement, runtime) -> {
                if (newEvents != null) {
                    for (EventBean event : newEvents) {
                        com.espertech.esper.example.IOT.streams.SingleCameraResult[] results = (com.espertech.esper.example.IOT.streams.SingleCameraResult[]) event
                                .get("results");
                        if (results != null && results.length > 0) {
                            int winIdx = results[0].getWindowIndex();
                            System.out.println(">>> AGGREGATED RESULT (Group " + currentGroupIndex + "): Window "
                                    + winIdx + " is ready with "
                                    + results.length + " cameras.");

                            // Prepare data for MCPT
                            java.util.Map<Integer, java.util.Map<String, java.util.Map<String, Object>>> trackingResults = new java.util.HashMap<>();

                            for (com.espertech.esper.example.IOT.streams.SingleCameraResult scr : results) {
                                int cameraId = Integer.parseInt(scr.getCameraId().replace("camera_", ""));

                                java.util.Map<String, java.util.Map<String, Object>> trackingDict = new java.util.HashMap<>();
                                java.util.List<Integer> clusterLabels = scr.getClusterLabels();
                                java.util.List<Integer> idList = scr.getIdList();
                                java.util.List<Integer[]> boundingBoxList = scr.getBoundingBoxList();
                                java.util.List<double[]> featureList = scr.getFeatureList();
                                java.util.List<java.util.List<java.util.List<Float>>> keypointsList = scr
                                        .getKeypointsList();
                                java.util.List<Integer> frameNumbers = scr.getFrameNumbers();

                                for (int i = 0; i < clusterLabels.size(); i++) {
                                    String serial = String.valueOf(idList.get(i));

                                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                                    data.put("OfflineID", clusterLabels.get(i));
                                    data.put("Frame", frameNumbers.get(i));

                                    Integer[] bbox = boundingBoxList.get(i);
                                    java.util.Map<String, Integer> coord = new java.util.HashMap<>();
                                    coord.put("x1", bbox[0]);
                                    coord.put("x2", bbox[1]);
                                    coord.put("y1", bbox[2]);
                                    coord.put("y2", bbox[3]);
                                    data.put("Coordinate", coord);

                                    data.put("Feature", featureList.get(i));
                                    data.put("Keypoints", keypointsList.get(i));

                                    trackingDict.put(serial, data);
                                }
                                trackingResults.put(cameraId, trackingDict);
                            }

                            // Run MCPT
                            com.espertech.esper.example.IOT.clusterers.MCPT.multiCameraPeopleTracking(
                                    calibrationMap, // Pass the calibration map
                                    trackingResults,
                                    globalTrackRegistry,
                                    nextGlobalId,
                                    TrackingParameters.representativeSelectionMethod, // Using keypoint selection
                                    TrackingParameters.epsilonMcpt,
                                    TrackingParameters.shortTrackTh,
                                    TrackingParameters.keypointTh,
                                    TrackingParameters.keypointConditionTh,
                                    TrackingParameters.replaceSimilarityByWCoordinate,
                                    TrackingParameters.distanceType,
                                    TrackingParameters.distanceTh,
                                    TrackingParameters.replaceValue,
                                    new int[] { 1920, 1080 }, // imageSize
                                    TrackingParameters.aspectTh, // aspectTh
                                    2000, // stackMaxSize
                                    winIdx);

                            // Send GlobalPersonEvent for tracker table
                            long timestamp = EventEPLUtil.getCurrentTime();
                            for (Integer cam : trackingResults.keySet()) {
                                java.util.Map<String, java.util.Map<String, Object>> camRes = trackingResults.get(cam);
                                for (String serial : camRes.keySet()) {
                                    java.util.Map<String, Object> attrs = camRes.get(serial);
                                    if (attrs.containsKey("GlobalOfflineID")) {
                                        int globalId = (int) attrs.get("GlobalOfflineID");
                                        int localId = (int) attrs.get("OfflineID");
                                        @SuppressWarnings("unchecked")
                                        java.util.Map<String, Integer> coord = (java.util.Map<String, Integer>) attrs
                                                .get("Coordinate");
                                        int x = (coord.get("x1") + coord.get("x2")) / 2;
                                        int y = coord.get("y2");

                                        // Flexible attributes map for Case Management style
                                        java.util.Map<String, Object> eventAttrs = new java.util.HashMap<>(attrs);
                                        eventAttrs.remove("Feature"); // Too large for table usually
                                        eventAttrs.remove("Keypoints");

                                        GlobalPersonEvent gpe = new GlobalPersonEvent(
                                                globalId, cam, serial, localId, x, y, timestamp, eventAttrs);
                                        EventEPLUtil.streamEvent(gpe, "GlobalPersonEvent");
                                    }
                                }
                            }
                            // Trigger table print
                            EventEPLUtil.streamEvent(new com.espertech.esper.example.IOT.streams.TriggerEvent(),
                                    "TriggerEvent");
                        }
                    }
                }
            });
        }
    }

    private void afterClusteringQueries() {
        // Global ID Tracking Table (Case Management Style)
        EventEPLUtil.addEpl("""
                    create table GlobalIDTable (
                        globalId int primary key,
                        lastSeen long,
                        attributes java.util.Map
                    );
                """);

        EventEPLUtil.addEpl("""
                    on GlobalPersonEvent as gpe
                    merge into GlobalIDTable as gt
                    where gt.globalId = gpe.globalId
                    when matched then
                        update set gt.lastSeen = gpe.timestamp, gt.attributes = gpe.attributes
                    when not matched then
                        insert select gpe.globalId as globalId, gpe.timestamp as lastSeen, gpe.attributes as attributes;
                """);

        // Extract the expired tracks into an event BEFORE deleting them
        EventEPLUtil.addEpl("@Priority(10) on pattern [every timer:interval(10000)]\n" +
                "insert into TrackExpiredEvent select globalId from GlobalIDTable\n" +
                "where current_timestamp() - lastSeen > 120000;");

        // Clean up persons from GlobalIDTable who haven’t been seen in 2 minutes
        EventEPLUtil.addEpl("@Priority(1) on pattern [every timer:interval(10000)]\n" +
                "delete from GlobalIDTable\n" +
                "where current_timestamp() - lastSeen > 120000;");

        EventEPLUtil.compileDeployAddListener("select * from TrackExpiredEvent",
                (newEvents, oldEvents, statement, runtime) -> {
                    if (newEvents != null) {
                        for (com.espertech.esper.common.client.EventBean event : newEvents) {
                            int expiredId = (int) event.get("globalId");
                            // Free up the heavy embedding memory!
                            globalTrackRegistry.remove(expiredId);
                            logger.info("Track " + expiredId + " expired. Cleaned up features.");
                        }
                    }
                });

        // Existing PersonTable queries (can stay or be replaced)
        EventEPLUtil.addEpl("""
                    create table PersonTable (
                        personId int primary key,
                        lastSeen long
                    );
                """);

        EventEPLUtil.addEpl("""
                    on PersonTracker as pd
                    merge into PersonTable as pt
                    where pt.personId = pd.personId
                    when matched then
                        update set pt.lastSeen = pd.timestamp
                    when not matched then
                        insert select pd.personId as personId, pd.timestamp as lastSeen;
                """);

        // Clean up persons who haven’t been seen in 5 seconds
        EventEPLUtil.addEpl("on pattern [every timer:interval(1000)]\n" +
                "delete from PersonTable\n" +
                "where current_timestamp() - lastSeen > 5000;");

        String eplSelect = """
                    on TriggerEvent
                    select globalId, lastSeen, attributes from GlobalIDTable;
                """;
        EventEPLUtil.addEpl(
                eplSelect,
                (EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) -> {
                    String listenerName = "Global ID Table Status";
                    if (newEvents != null) {
                        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
                        System.out.println("--- Global ID Table Update ---");
                        for (EventBean event : newEvents) {
                            int globalId = (int) event.get("globalId");
                            long lastSeen = (long) event.get("lastSeen");
                            java.util.Map<String, Object> attrs = (java.util.Map<String, Object>) event
                                    .get("attributes");

                            System.out.printf("%s: GlobalID %d last seen at %d. Attributes: %s%n",
                                    listenerName,
                                    globalId,
                                    lastSeen,
                                    attrs != null ? attrs.toString() : "none");

                            java.util.Map<String, Object> row = new java.util.HashMap<>();
                            row.put("globalId", globalId);
                            row.put("lastSeen", lastSeen);
                            row.put("attributes", attrs);
                            rows.add(row);
                        }
                        System.out.println("------------------------------");

                        // Broadcast to Python clients
                        if (!rows.isEmpty()) {
                            socketServer.broadcast(gson.toJson(rows));
                        }
                    }
                });
        EventEPLUtil.deployAll();

    }

    private void wildTrackDatasetQueries() {
        String eplQuery;
        eplQuery = "select * from personView;";
        EventEPLUtil.compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("personView raw event"));

        // Loop through all view numbers (0 to 6)
        for (int viewNumberCounter = 0; viewNumberCounter <= 6; viewNumberCounter++) {
            String PersonViewExtracted = "insert into PersonViewExtracted " +
                    "select " +
                    "  personID, " +
                    "  frameNumber, " +
                    "  timeStamp, " +
                    "  views[" + viewNumberCounter + "].viewNum as viewNum, " +
                    "  views[" + viewNumberCounter + "].xmin as xmin, " +
                    "  views[" + viewNumberCounter + "].xmax as xmax, " +
                    "  views[" + viewNumberCounter + "].ymin as ymin, " +
                    "  views[" + viewNumberCounter + "].ymax as ymax " +
                    "from personView;";
            EventEPLUtil.compileDeploy(PersonViewExtracted);

            String OverlapCandidates = "insert into OverlapCandidates " +
                    "select " +
                    "  A.personID as personID_1, " +
                    "  B.personID as personID_2, " +
                    "  A.frameNumber as frameNumber, " +
                    "  A.timeStamp as eventTime, " +
                    "  A.viewNum as viewNum_1, " +
                    "  B.viewNum as viewNum_2, " +
                    "  (min(A.xmax, B.xmax) - max(A.xmin, B.xmin)) as overlapX, " +
                    "  (min(A.ymax, B.ymax) - max(A.ymin, B.ymin)) as overlapY, " +
                    "  (A.xmax - A.xmin) * (A.ymax - A.ymin) as areaA, " +
                    "  (B.xmax - B.xmin) * (B.ymax - B.ymin) as areaB " +
                    "from " +
                    "  PersonViewExtracted#ext_timed_batch(timeStamp, 5 sec) A " +
                    "  join " +
                    "  PersonViewExtracted#ext_timed_batch(timeStamp, 5 sec) B " +
                    "  on A.frameNumber = B.frameNumber " +
                    "where " +
                    "  A.personID != B.personID " +
                    "  and (min(A.xmax, B.xmax) > max(A.xmin, B.xmin)) " +
                    "  and (min(A.ymax, B.ymax) > max(A.ymin, B.ymin));";
            EventEPLUtil.compileDeploy(OverlapCandidates);

            String OverlappingDetections = "insert into OverlappingDetections " +
                    "select " +
                    "  personID_1, " +
                    "  personID_2, " +
                    "  frameNumber, " +
                    "  eventTime, " +
                    "  viewNum_1, " +
                    "  viewNum_2, " +
                    "  (overlapX * overlapY) / (areaA + areaB - (overlapX * overlapY)) as iou " +
                    "from OverlapCandidates " +
                    "where " +
                    "  (overlapX * overlapY) > 0 " + // Redundant but explicit safety check
                    "  and (overlapX * overlapY) / (areaA + areaB - (overlapX * overlapY)) > 0.5;";

            // Deploy the query and add the listener with the dynamically generated name
            EventEPLUtil.compileDeployAddListener(
                    OverlappingDetections,
                    new GenericIotEventListener("OverlappingDetections for view Number " + viewNumberCounter));
        }
    }

    private void someExampleQueries() {
        String eplQuery;

        eplQuery = "select * from sensorData output all every 4 seconds order by timestamp;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as
        // total from sensorData output last every 2 seconds;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as
        // total from sensorData#time(4);";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as
        // total from sensorData#time(5);";
        EventEPLUtil.compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("Out sensorData every 4 seconds Event"));

        eplQuery = "insert into CombinedEvent(deviceId, type, command, value, timestamp)" +
                "select D.deviceId," +
                "type," +
                "command," +
                "value," +
                "D.timestamp " +
                "from sensorData#time(5 sec) D JOIN " +
                "deviceCommand#time(5 sec) C " +
                "ON D.deviceId = C.deviceId;";

        /*
         * // Same Query but with using multiple selects and where clause not join and
         * on.
         * eplQuery =
         * "insert into CombinedEvent(deviceId, type, command, value, timestamp)" +
         * "select D.deviceId," +
         * "type," +
         * "command," +
         * "value," +
         * "D.timestamp " +
         * "from sensorData#time(5 sec) D," +
         * "deviceCommand#time(5 sec) C " +
         * "where D.deviceId = C.deviceId;";
         */

        EventEPLUtil.compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("Combined event"));
    }

}
