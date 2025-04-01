package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.example.IOT.generators.WildTrackDatasetGenerator;
import com.espertech.esper.example.IOT.generators.EmbeddingFeatureGenerator;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.example.IOT.generators.DeviceCommandStreamer;

public class IotStreamGenerator {
    private static final long ONE_SEC_TIME_STEP = 1000L;  // 1 second (in milliseconds)
    private static long timeTracker = System.currentTimeMillis();  // Shared time tracker

    /**
     * Advances the given runtime's time by the specified time step.
     *
     * @param runtime The EPRuntime instance whose time is to be advanced.
     * @param timeStep The time step in milliseconds by which to advance the time.
     */
    public static long advanceTime(EPRuntime runtime, long timeStep) {
        timeTracker += timeStep;
        runtime.getEventService().advanceTime(timeTracker);
        System.out.println("Time advanced to: " + timeTracker + " ms");
        return timeTracker;
    }

    // Overloaded method for default time step
    public static long advanceTime(EPRuntime runtime) {
        advanceTime(runtime, ONE_SEC_TIME_STEP);
        return timeTracker;

    }

    public void generateEvents(EPRuntime runtime) {
//        DeviceCommandStreamer.streamDeviceCommands(runtime);
//        WildTrackDatasetGenerator.streamWildTrackDataset(runtime);
        EmbeddingFeatureGenerator.streamEmbeddingFeatures(runtime);
    }}
