package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventEPLUtil {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);
    private static final long ONE_SEC_TIME_STEP = 1000L;  // 1 second (in milliseconds)
    private static long timeTracker = System.currentTimeMillis();  // Shared time tracker
    private static final Configuration configuration = new Configuration();
    private static String runtimeURI;
    private static EPRuntime runtime;

    private EventEPLUtil() {
        /* Prevent instantiation */
    }

    public static void setRuntimeURI(String runtimeURI) {
        EventEPLUtil.runtimeURI = runtimeURI;
    }

    public static void initiateRuntime() {
        runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();
    }

    public static void addEventType(String eventName, Class<?> eventClass) {
        configuration.getCommon().addEventType(eventName, eventClass);
    }

    public static void streamEvent(Object event, String eventName) {
        runtime.getEventService().sendEventBean(event, eventName);
    }

    public static void compileDeployAddListener(String eplQuery, UpdateListener listener){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(eplQuery);
        EventEPLUtil.add_listener(statement, listener);
    }

    public static EPStatement compileDeploy(String epl) {
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

    /**
     * Advances the given runtime's time by the specified time step.
     *
     * @param timeStep The time step in milliseconds by which to advance the time.
     */
    public static long advanceTime(long timeStep) {
        timeTracker += timeStep;
        runtime.getEventService().advanceTime(timeTracker);
        System.out.println("Time advanced to: " + timeTracker + " ms");
        return timeTracker;
    }

    // Overloaded method for default time step
    public static long advanceTime() {
        advanceTime(ONE_SEC_TIME_STEP);
        return timeTracker;

    }
}
