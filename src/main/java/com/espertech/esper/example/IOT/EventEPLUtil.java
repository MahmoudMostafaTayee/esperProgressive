package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.SensorData.SensorData;
import com.espertech.esper.example.IOT.DeviceCommand.DeviceCommand;
import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventEPLUtil {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);

    public static Configuration getConfiguration() {
        Configuration configuration = new Configuration();
        configuration.getCommon().addEventType("sensorData", SensorData.class);
        configuration.getCommon().addEventType("deviceCommand", DeviceCommand.class);
        return configuration;
    }

    public static void compileDeployAddListener(EPRuntime runtime, String eplQuery, UpdateListener listener){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(runtime, eplQuery);
        EventEPLUtil.add_listener(statement, listener);
    }

    private static EPStatement compileDeploy(EPRuntime runtime, String epl) {
        try {
            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(epl, args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            return deployment.getStatements()[0];
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void add_listener(EPStatement statement, UpdateListener listener){
        // EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener(listener);
        } else {
            log.error("Statement not found: 'out'");
        }
    }
}
