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

import com.espertech.esper.example.IOT.clusterers.Tracker;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // someExampleQueries();
        // wildTrackDatasetQueries();

        embeddingFeatureQueries();
        multiCameraAggregationQueries();
        afterClusteringQueries();

        launchStreams();

        logger.info("Done.");
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
        // Aggregate SingleCameraResult events from all cameras for the same
        // windowIndex.

        int numCameras = cameraList.size();

        System.out.println(">>> DEPLOYING AGGREGATION QUERY...");
        String aggregationEPL = "select windowIndex, count(*) as cnt " +
                "from SingleCameraResult " +
                "group by windowIndex " +
                "having count(*) = " + numCameras + " " +
                "output first every 59 seconds"; // Output once per windowIndex when ready

        EventEPLUtil.compileDeployAddListener(aggregationEPL, (newEvents, oldEvents, statement, runtime) -> {
            if (newEvents != null) {
                for (EventBean event : newEvents) {
                    int winIdx = (int) event.get("windowIndex");
                    long count = (long) event.get("cnt");
                    System.out.println(
                            ">>> AGGREGATED RESULT: Window " + winIdx + " is ready with " + count + " cameras.");
                }
            }
        });
    }

    private void afterClusteringQueries() {
        // String eplTable = """
        // create table PersonTable (
        // personId string primary key,
        // features float[],
        // lastSeen long
        // );
        // """;

        // String eplInsertOrUpdate = """
        // on PersonTracker as pd
        // merge PersonTable as pt
        // where pt.personId = pd.personId
        // when matched then
        // update set pt.features = pd.features, pt.lastSeen = pd.timestamp
        // when not matched then
        // insert (personId, features, lastSeen) values (pd.personId, pd.features,
        // pd.timestamp);
        // """;

        // String eplSchema = """
        // create schema PersonTracker(personId string, timestamp long);
        // """;
        // EventEPLUtil.addEpl(eplSchema);

        // EventEPLUtil.addEpl("""
        // create schema TriggerEvent();
        // """);

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
                    select personId, lastSeen from PersonTable;
                """;
        EventEPLUtil.addEpl(
                eplSelect,
                (EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) -> {
                    String listenerName = "Tracking persons table";
                    if (newEvents != null) {
                        for (EventBean event : newEvents) {
                            int personId = (int) event.get("personId");
                            personId += 1;
                            long lastSeen = (long) event.get("lastSeen");
                            System.out.printf("%s: Person %d last seen at %d (Current Time: %d)%n",
                                    listenerName,
                                    personId,
                                    lastSeen,
                                    EventEPLUtil.getCurrentTime());
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
