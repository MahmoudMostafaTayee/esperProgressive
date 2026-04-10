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
    private static final long ONE_SEC_TIME_STEP = 1000L; // 1 second (in milliseconds)
    private static long timeTracker = System.currentTimeMillis(); // Shared time tracker
    private static final Configuration configuration = new Configuration();
    private static String runtimeURI;
    private static EPRuntime runtime;

    // Holds each EPL with an optional listener
    private static class EplEntry {
        final String epl;
        final UpdateListener listener;

        EplEntry(String epl, UpdateListener listener) {
            this.epl = epl;
            this.listener = listener;
        }
    }

    private static final List<EplEntry> pendingEpls = new ArrayList<>();

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
     * Collects an EPL statement for later deployment, with an optional listener.
     */
    public static void addEpl(String epl) {
        pendingEpls.add(new EplEntry(epl.trim(), null));
    }

    public static void addEpl(String epl, UpdateListener listener) {
        pendingEpls.add(new EplEntry(epl.trim(), listener));
    }

    /**
     * Deploys all buffered EPL statements in one batch,
     * and attaches each listener to its corresponding statement.
     */
    public static void deployAll() {
        if (pendingEpls.isEmpty()) {
            logger.warn("No EPL statements to deploy");
            return;
        }
        // Build batch EPL
        StringBuilder batch = new StringBuilder();
        for (EplEntry entry : pendingEpls) {
            batch.append(entry.epl).append("\n");
        }
        try {
            CompilerArguments args = new CompilerArguments(configuration);
            // ensure visibility of types and tables
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(batch.toString(), args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            EPStatement[] statements = deployment.getStatements();

            // Attach listeners in order
            for (int i = 0; i < statements.length && i < pendingEpls.size(); i++) {
                UpdateListener listener = pendingEpls.get(i).listener;
                if (listener != null) {
                    statements[i].addListener(listener);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to batch deploy EPLs", e);
            throw new RuntimeException(e);
        } finally {
            pendingEpls.clear();
        }
    }

    public static void compileDeployAddListener(String eplQuery, UpdateListener listener) {
        EventEPLUtil.compileDeployAddListenerReturnsId(eplQuery, listener);
    }

    public static String compileDeployAddListenerReturnsId(String eplQuery, UpdateListener listener) {
        EPDeployment deployment = EventEPLUtil.compileDeployReturnDeployment(eplQuery);
        EventEPLUtil.add_listener(deployment.getStatements()[0], listener);
        return deployment.getDeploymentId();
    }

    public static void undeploy(String deploymentId) {
        if (runtime != null && deploymentId != null) {
            try {
                runtime.getDeploymentService().undeploy(deploymentId);
                logger.info("Undeployed statement with ID: " + deploymentId);
            } catch (Exception e) {
                logger.error("Failed to undeploy ID: " + deploymentId, e);
            }
        }
    }

    public static EPStatement compileDeploy(String epl) {
        return compileDeployReturnDeployment(epl).getStatements()[0];
    }

    public static EPDeployment compileDeployReturnDeployment(String epl) {
        try {
            CompilerArguments args = new CompilerArguments();

            // Capture the runtime path AT THIS MOMENT (includes previous deployments)
            EPCompilerPathable currentRuntimePath = runtime.getRuntimePath();
            if (currentRuntimePath != null) {
                args.getPath().add(currentRuntimePath);
            }

            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(epl, args);
            return runtime.getDeploymentService().deploy(compiled);
        } catch (Exception ex) {
            logger.error("Failed to deploy EPL:\n{}", epl, ex); // Log the exact EPL causing failure
            throw new RuntimeException(ex);
        }
    }

    private static void add_listener(EPStatement statement, UpdateListener listener) {
        // EPStatement statement =
        // runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
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
        // System.out.println("Time step: " + timeStep);
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

    public static void destroyRuntime() {
        if (runtime != null) {
            logger.info("Destroying Esper runtime...");
            runtime.destroy();
            runtime = null;
        }
    }
}
