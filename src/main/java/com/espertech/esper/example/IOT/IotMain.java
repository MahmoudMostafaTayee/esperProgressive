package com.espertech.esper.example.IOT;

import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import com.espertech.esper.runtime.client.UpdateListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotMain implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IotMain.class);

    private final String runtimeURI;
    static private EPRuntime runtime;
    private Configuration configuration;

    public static void main(String[] args) {
        new IotMain("IotEventRuntime").run();
    }

    public IotMain(String runtimeURI) {
        this.runtimeURI = runtimeURI;
    }

    private void initiateRunTime(){
        configuration = EventEPLUtil.getConfiguration();
        log.info("Setting up runtime");

        runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();
    }

    private void add_generator(IotStreamGenerator generator){
        log.info("Generating and sending events with time advancement");
        generator.generateEvents(runtime);
    }

    private static void compileDeployAddListener(String eplQuery, UpdateListener listener){
        EventEPLUtil.compileDeployAddListener(  runtime, 
                                                eplQuery, 
                                                listener);
    }

    private static void compileDeploy(String eplQuery){
        EventEPLUtil.compileDeploy(  runtime, eplQuery);
    }

    public void run() {
        initiateRunTime();
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


        add_generator(new IotStreamGenerator());
        
        log.info("Done.");
    }    
}
