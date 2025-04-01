package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.example.IOT.generators.WildTrackDatasetGenerator;
import com.espertech.esper.example.IOT.helpers.JsonReader;
import com.espertech.esper.example.IOT.streams.SensorData;

import java.util.List;
import java.nio.file.*;

import com.espertech.esper.example.IOT.generators.EmbeddingFeatureGenerator;


import com.espertech.esper.example.IOT.streams.DeviceCommand;
import com.espertech.esper.example.IOT.streams.PersonView;
import com.espertech.esper.example.IOT.streams.EmbeddingFeature;
import com.espertech.esper.runtime.client.EPRuntime;
import java.io.IOException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;

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

    /**
     * Streams a series of predefined sensor data and device command events to the EPRuntime.
     * Each event is followed by a time advancement step to simulate real-time event processing.
     *
     * @param runtime The EPRuntime instance used to send event beans.
     */
    private void streamDeviceCommands(EPRuntime runtime){
        runtime.getEventService().sendEventBean(new SensorData(10, "101", "temp_sensor", 18002000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new PersonView(timeTracker, 122, 0, 456826, List.of(new PersonView.View(0, 1561, 1510, 299, 139))), "personView");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(5, "102", "camera", 18001000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(3, "104", "mic", 18002000L), "sensorData");
        runtime.getEventService().sendEventBean(new DeviceCommand("101", "Set Temperature", "24°C", 18002000L), "deviceCommand");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(7, "107", "camera", 18001000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(10, "106", "screen", 18005000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(10, "103", "mobile", 18004000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(44, "101", "temp_sensor", 18006000L), "sensorData");
        runtime.getEventService().sendEventBean(new SensorData(10, "109", "wash-machine", 18006000L), "sensorData");
        timeTracker = advanceTime(runtime);

        runtime.getEventService().sendEventBean(new SensorData(10, "105", "mic", 18007000L), "sensorData");
        timeTracker = advanceTime(runtime);
    }

    public void generateEvents(EPRuntime runtime) {
//        streamDeviceCommands(runtime);
//        WildTrackDatasetGenerator.streamWildTrackDataset(runtime);
        EmbeddingFeatureGenerator.streamEmbeddingFeatures(runtime);
    }}
