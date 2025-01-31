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
            // Generate the EPL query for the current view number
            eplQuery = "insert into OverlappingDetections(personID_1, personID_2, frameNumber, eventTime, viewNum_1, viewNum_2, iou) " +
                        "select A.personID, " +
                        "B.personID, " +
                        "A.frameNumber, " +
                        "A.timeStamp, " +
                        "A.views[" + viewNumberCounter + "].viewNum as viewNum_1, " +
                        "B.views[" + viewNumberCounter + "].viewNum as viewNum_2, " +
                        "(" +
                        "   1.0 * ( " +  // Ensure floating-point division
                        "      (min(A.views[" + viewNumberCounter + "].xmax, B.views[" + viewNumberCounter + "].xmax) - " +
                        "       max(A.views[" + viewNumberCounter + "].xmin, B.views[" + viewNumberCounter + "].xmin)) * " +
                        "      (min(A.views[" + viewNumberCounter + "].ymax, B.views[" + viewNumberCounter + "].ymax) - " +
                        "       max(A.views[" + viewNumberCounter + "].ymin, B.views[" + viewNumberCounter + "].ymin)) " +
                        "   ) / ( " +  // Start denominator
                        "      ( (A.views[" + viewNumberCounter + "].xmax - A.views[" + viewNumberCounter + "].xmin) * " +
                        "        (A.views[" + viewNumberCounter + "].ymax - A.views[" + viewNumberCounter + "].ymin) ) + " +
                        "      ( (B.views[" + viewNumberCounter + "].xmax - B.views[" + viewNumberCounter + "].xmin) * " +
                        "        (B.views[" + viewNumberCounter + "].ymax - B.views[" + viewNumberCounter + "].ymin) ) - " +
                        "      ( (min(A.views[" + viewNumberCounter + "].xmax, B.views[" + viewNumberCounter + "].xmax) - " +
                        "          max(A.views[" + viewNumberCounter + "].xmin, B.views[" + viewNumberCounter + "].xmin)) * " +
                        "        (min(A.views[" + viewNumberCounter + "].ymax, B.views[" + viewNumberCounter + "].ymax) - " +
                        "         max(A.views[" + viewNumberCounter + "].ymin, B.views[" + viewNumberCounter + "].ymin)) ) " +
                        "   ) " +
                        ") as iou " +  // Close IoU calculation
                        "from personView#ext_timed_batch(timeStamp, 5 sec) A JOIN " +
                        "personView#ext_timed_batch(timeStamp, 5 sec) B " +
                        "ON A.frameNumber = B.frameNumber " +
                        "WHERE A.personID != B.personID " +
                        "AND (min(A.views[" + viewNumberCounter + "].xmax, B.views[" + viewNumberCounter + "].xmax) - " +
                        "     max(A.views[" + viewNumberCounter + "].xmin, B.views[" + viewNumberCounter + "].xmin)) > 0 " +
                        "AND (min(A.views[" + viewNumberCounter + "].ymax, B.views[" + viewNumberCounter + "].ymax) - " +
                        "     max(A.views[" + viewNumberCounter + "].ymin, B.views[" + viewNumberCounter + "].ymin)) > 0;";



            // Deploy the query and add the listener with the dynamically generated name
            compileDeployAddListener(
                    eplQuery,
                    new GenericIotEventListener("DetectOverlaps for view Number " + viewNumberCounter)
            );
        }


        add_generator(new IotStreamGenerator());
        
        log.info("Done.");
    }    
}
