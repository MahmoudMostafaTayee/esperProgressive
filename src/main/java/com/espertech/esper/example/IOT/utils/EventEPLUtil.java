package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EPCompilerPathable;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class EventEPLUtil {
    private static final Logger logger = LoggerFactory.getLogger(EventEPLUtil.class);
    private static final long ONE_SEC_TIME_STEP = 1000L;  // 1 second (in milliseconds)
    private static long timeTracker = System.currentTimeMillis();  // Shared time tracker
    private static final Configuration configuration = new Configuration();
    private static String runtimeURI;
    private static EPRuntime runtime;

    // Buffer to hold EPL statements before deployment
    private static final List<String> pendingEpls = new ArrayList<>();

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

    public static void setConfiguration() {
        configuration.getCompiler().getByteCode().setAccessModifiersPublic();
    }

    public static void addEventType(String eventName, Class<?> eventClass) {
        configuration.getCommon().addEventType(eventName, eventClass);
    }

    public static void streamEvent(Object event, String eventName) {
        runtime.getEventService().sendEventBean(event, eventName);
    }

    /**
     * Collects an EPL statement for later deployment.
     */
    public static void addEpl(String epl) {
        pendingEpls.add(epl.trim());
    }

    /**
     * Deploys all pending EPL statements in one batch.
     */
    public static void compileDeployPendingEpls(UpdateListener listener) {
        if (pendingEpls.isEmpty()) {
            logger.warn("No EPL statements to deploy");
            return;
        }
        StringBuilder batch = new StringBuilder();
        pendingEpls.forEach(stmt -> batch.append(stmt).append("\n"));

        try {
            CompilerArguments args = new CompilerArguments(configuration);
            // Ensure compiler sees registered event types
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(batch.toString(), args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
        } catch (Exception e) {
            logger.error("Failed to batch deploy EPLs", e);
            throw new RuntimeException(e);
        } finally {
            pendingEpls.clear();
        }
    }

    /**
     * Deploys all pending EPL statements in one batch and attaches the listener to the last statement.
     */
    public static void compileDeployPendingEplsAddListener(UpdateListener listener) {
        if (pendingEpls.isEmpty()) {
            logger.warn("No EPL statements to deploy");
            return;
        }
        StringBuilder batch = new StringBuilder();
        pendingEpls.forEach(stmt -> batch.append(stmt).append("\n"));

        try {
            CompilerArguments args = new CompilerArguments(configuration);
            // Ensure compiler sees registered event types
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(batch.toString(), args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            // Attach listener to the last statement in the deployment
            EPStatement[] statements = deployment.getStatements();
            EPStatement last = statements[statements.length - 1];
            last.addListener(listener);
        } catch (Exception e) {
            logger.error("Failed to batch deploy EPLs", e);
            throw new RuntimeException(e);
        } finally {
            pendingEpls.clear();
        }
    }

    public static void compileDeployAddListener(String eplQuery, UpdateListener listener){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(eplQuery);
        EventEPLUtil.add_listener(statement, listener);
    }

    public static EPStatement compileDeploy(String epl) {
        try {
            CompilerArguments args = new CompilerArguments();

            // Capture the runtime path AT THIS MOMENT (includes previous deployments)
            EPCompilerPathable currentRuntimePath = runtime.getRuntimePath();
            if (currentRuntimePath != null) {
                args.getPath().add(currentRuntimePath);
            }

            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(epl, args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            return deployment.getStatements()[0];
        } catch (Exception ex) {
            logger.error("Failed to deploy EPL:\n{}", epl, ex); // Log the exact EPL causing failure
            throw new RuntimeException(ex);
        }
    }

    private static void add_listener(EPStatement statement, UpdateListener listener){
        // EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener(listener);
        } else {
            logger.error("Statement not found: 'out'");
        }
    }

    /**
     * Advances the given runtime's time by the specified time step.
     *
     * @param timeStep The time step in milliseconds by which to advance the time.
     */
    public static void advanceTime(long timeStep) {
        timeTracker += timeStep;
        runtime.getEventService().advanceTime(timeTracker);
        logger.debug("Time advanced to: {} ms", timeTracker);
    }

    public static void advanceTime(double percentage) {
        long timeStep = (long) (ONE_SEC_TIME_STEP * percentage);
//        System.out.println("Time step: " + timeStep);
        timeTracker += timeStep;
        runtime.getEventService().advanceTime(timeTracker);
        logger.debug("Time advanced to: {} ms", timeTracker);
    }

    // Overloaded method for default time step
    public static long advanceTime() {
        advanceTime(ONE_SEC_TIME_STEP);
        return timeTracker;

    }

    public static long getCurrentTime() {
        return timeTracker;
    }
}
