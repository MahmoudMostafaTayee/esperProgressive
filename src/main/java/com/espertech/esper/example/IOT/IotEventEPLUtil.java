package com.espertech.esper.example.IOT;

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

public class IotEventEPLUtil {
    private static final Logger log = LoggerFactory.getLogger(IotEventEPLUtil.class);

    public static EPCompiled compileEPL(Configuration configuration) {
        String eplQuery = "@name('out') select * from iotEvent;";
        
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
        configuration.getCommon().addEventType("iotEvent", IotEvent.class);
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
