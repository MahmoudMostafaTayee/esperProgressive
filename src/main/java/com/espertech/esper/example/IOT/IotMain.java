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
        "from sensorData#time(5 sec) D," +
        "deviceCommand#time(5 sec) C " +
        "where D.deviceId = C.deviceId;";
        
        // eplQuery = "select * from deviceCommand;";
        compileDeployAddListener(   
                                    eplQuery, 
                                    new GenericIotEventListener("Combined event")
                                );

        add_generator(new IotStreamGenerator());
        
        log.info("Done.");
    }    
}
