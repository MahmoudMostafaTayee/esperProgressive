/*
    mvn clean install -Dcheckstyle.skip=true
    mvn clean install -U -DskipTests

 */
package com.espertech.esper.example.IOT;

import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.example.IOT.streamers.DeviceCommandStreamer;
import com.espertech.esper.example.IOT.streamers.EmbeddingFeatureGenerator;
import com.espertech.esper.example.IOT.streamers.WildTrackDatasetGenerator;
import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.example.IOT.utils.ClustersUtils;
import com.espertech.esper.example.IOT.listeners.GenericIotEventListener;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import com.espertech.esper.runtime.client.UpdateListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotMain implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IotMain.class);

    private final String runtimeURI;
    static private EPRuntime runtime;

    public static void main(String[] args) {
        new IotMain("IotEventRuntime").run();
    }

    public IotMain(String runtimeURI) {
        this.runtimeURI = runtimeURI;
    }

    /**
     * Initiates the Esper runtime with the provided runtime URI and configuration.
     * This method gets the configuration from {@link EventEPLUtil#getConfiguration()},
     * Gets the runtime from the
     * configuration and initializes it.
     */
    private void initiateRunTime(){
        Configuration configuration = EventEPLUtil.getConfiguration();
        log.info("Setting up runtime");

        runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();
    }

    /**
     * Adds a generator to send events to the runtime.
     */
    private void launchStreams(){
        log.info("Generating and sending events with time advancement");
//        DeviceCommandStreamer.streamDeviceCommands(runtime);
//        WildTrackDatasetGenerator.streamWildTrackDataset(runtime);
        EmbeddingFeatureGenerator.streamEmbeddingFeatures(runtime);
    }

    /**
     * Compiles and deploys the given EPL query, attaching the provided listener to the resulting EPStatement.
     *
     * @param eplQuery  the EPL query to compile and deploy
     * @param listener  the listener to attach to the EPStatement
     */
    private static void compileDeployAddListener(String eplQuery, UpdateListener listener) {
        // Compile and deploy the given EPL query using the runtime
        EventEPLUtil.compileDeployAddListener(
                runtime,  // The EPRuntime instance to use for deployment
                eplQuery, // The EPL query string to compile
                listener  // The listener to attach to the EPStatement
        );
    }

    private static void compileDeploy(String eplQuery){
        EventEPLUtil.compileDeploy(  runtime, eplQuery);
    }

    public void run() {
        initiateRunTime();

//        someExampleQueries();
//        wildTrackDatasetQueries();

        embeddingFeatureQueries();

        launchStreams();
        
        log.info("Done.");
    }

    private void embeddingFeatureQueries(){
        String batchEpl = "insert into EmbeddingWindow select * from embeddingFeature#time_batch(2 sec)";
//        compileDeploy(batchEpl);
        compileDeployAddListener(batchEpl, new GenericIotEventListener("Embedding features Time Batch"));

        String featureBatchEPL =
                "select features, UNum " +
                "from embeddingFeature#time_batch(2 sec)";

        ClustersUtils.AgglomerativeClustering agglomerativeListener = new ClustersUtils.AgglomerativeClustering(0.1);
        compileDeployAddListener(featureBatchEPL, agglomerativeListener.getListener());

        ClustersUtils.CluStream cluStream = new ClustersUtils.CluStream(7);
        compileDeployAddListener(featureBatchEPL, cluStream.getListener());

        String featureStreamEPL =
                "select features, UNum " +
                        "from embeddingFeature";
        ClustersUtils.ClusTree clusTree = new ClustersUtils.ClusTree();
        compileDeployAddListener(featureStreamEPL, clusTree.getListener());

        String similarityEpl = "insert into SimilarityPairs " +
                "select a.curFrame as frame1, a.UNum as id1, " +
                "       b.curFrame as frame2, b.UNum as id2, " +
                "       com.espertech.esper.example.IOT.helpers.SimilarityUtils.cosineSimilarity(a.features, b.features) as similarity, " +
                "       com.espertech.esper.example.IOT.helpers.SpatialFunctions.iou(a, b) as iou " +
                "from embeddingFeature#time_batch(2 sec) as a, embeddingFeature#time_batch(2 sec) as b " +
                "where a.UNum < b.UNum " + /* Avoid duplicate comparisons */
                "and a.curFrame != b.curFrame "; /* Avoid comparing same individuals from the same frame */

        compileDeployAddListener(similarityEpl, new GenericIotEventListener("cosine similarity calculation"));
        String clusterEpl = "insert into PotentialClusters " +
                "select * from SimilarityPairs " +
                "match_recognize ( " +
                "  measures A.id1 as id1, A.id2 as id2 " +
                "  pattern (A) " +
                "  define A as A.similarity > 0.8" + /* Similarity threshold */
                "  and iou > 0.3" + /* Spatial overlap threshold */
                ")";

        compileDeployAddListener(clusterEpl,new GenericIotEventListener("Potential Cluster"));
    }
    private void wildTrackDatasetQueries(){
        String eplQuery;
        eplQuery = "select * from personView;";
        compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("personView raw event")
        );

        // Loop through all view numbers (0 to 6)
        for (int viewNumberCounter = 0; viewNumberCounter <= 6; viewNumberCounter++) {
            String PersonViewExtracted =    "insert into PersonViewExtracted " +
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
            compileDeploy(PersonViewExtracted);

            String OverlapCandidates =  "insert into OverlapCandidates " +
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
            compileDeploy(OverlapCandidates);

            String OverlappingDetections =  "insert into OverlappingDetections " +
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
                    "  (overlapX * overlapY) > 0 " +  // Redundant but explicit safety check
                    "  and (overlapX * overlapY) / (areaA + areaB - (overlapX * overlapY)) > 0.5;";

            // Deploy the query and add the listener with the dynamically generated name
            compileDeployAddListener(
                    OverlappingDetections,
                    new GenericIotEventListener("OverlappingDetections for view Number " + viewNumberCounter)
            );
        }
    }

    private void someExampleQueries(){
        String eplQuery;

        eplQuery = "select * from sensorData output all every 4 seconds order by timestamp;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData output last every 2 seconds;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(4);";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(5);";
        compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("Out sensorData every 4 seconds Event")
        );

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
         * // Same Query but with using multiple selects and where clause not join and on.
         eplQuery = "insert into CombinedEvent(deviceId, type, command, value, timestamp)" +
         "select D.deviceId," +
         "type," +
         "command," +
         "value," +
         "D.timestamp " +
         "from sensorData#time(5 sec) D," +
         "deviceCommand#time(5 sec) C " +
         "where D.deviceId = C.deviceId;";
         */

        compileDeployAddListener(
                eplQuery,
                new GenericIotEventListener("Combined event")
        );
    }

}

