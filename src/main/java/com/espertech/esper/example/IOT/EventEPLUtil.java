package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.SensorData.SensorData;
import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.DeploymentOptions;
import com.espertech.esper.runtime.client.EPDeployException;
import com.espertech.esper.runtime.client.EPRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventEPLUtil {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);

    public static EPCompiled compileEPL(Configuration configuration) {
        String eplQuery = "@name('out') select * from sensorData output all every 4 seconds order by timestamp;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData output last every 2 seconds;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(4);";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(5);";
        
        log.info("Compiling EPL");
        try {
            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(eplQuery, new CompilerArguments(configuration));

            return compiled;
        } catch (EPCompileException ex) {
            throw new RuntimeException("Failed to compile EPL", ex);
        }
    }

    public static Configuration getConfiguration() {
        Configuration configuration = new Configuration();
        configuration.getCommon().addEventType("sensorData", SensorData.class);
        return configuration;
    }

    public static void deploy(EPRuntime runtime, EPCompiled compiled) {
        try {
            runtime.getDeploymentService().deploy(compiled);
        } catch (EPDeployException ex) {
            throw new RuntimeException("Failed to deploy EPL", ex);
        }
    }
}
