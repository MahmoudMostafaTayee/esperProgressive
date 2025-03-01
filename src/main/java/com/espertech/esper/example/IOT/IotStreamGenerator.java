package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.SensorData.SensorData;

import java.util.List;
import java.nio.file.*;
import com.espertech.esper.example.IOT.helpers.JsonReader;
import com.espertech.esper.example.IOT.DeviceCommand.DeviceCommand;
import com.espertech.esper.example.IOT.PersonView.PersonView;
import com.espertech.esper.example.IOT.EmbeddingFeature.EmbeddingFeature;
import com.espertech.esper.runtime.client.EPRuntime;
import java.io.IOException;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.io.File;

import java.util.Arrays;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;

public class IotStreamGenerator {
    // Fixed time step (1 second) to advance time after each event
    private long oneSecTimeStep = 1000L;  // 1 second (in milliseconds)
    // Track current starting time for advancing time.
    private long timeTracker = System.currentTimeMillis();

    /**
     * Advances the given runtime's time by the specified time step.
     *
     * <p>This method calculates the new current time by adding the time step to the provided
     * time tracker value, and then advances the EPRuntime to this new time.</p>
     *
     * @param runtime The EPRuntime instance whose time is to be advanced.
     * @param timeTracker The current time tracker value in milliseconds.
     * @param timeStep The time step in milliseconds by which to advance the time.
     * @return The new current time after the advancement.
     */
    private long advanceTime(EPRuntime runtime, long timeTracker, long timeStep){
        long currentTime = timeTracker + timeStep;
        
        /*This is to advance time with a specific time step */
        runtime.getEventService().advanceTime(currentTime);

        // Optionally, log the time advancement
        System.out.println("Time advanced to: " + currentTime + " ms");

        return currentTime;
    }
    /**
     * Streams the Wildtrack dataset to the runtime, one event per frame.
     * <p>
     * This method reads all the JSON files in the specified directory, parses them as PersonView objects,
     * and sends them to the runtime as events. The timestamp of each event is set to the current time,
     * and the frame number is set to the number in the filename.
     * <p>
     * The time is advanced by one second after each event.
     * @param runtime the runtime to which the events are sent.
     */
    private void streamWildTrackDataset(EPRuntime runtime){
        String directoryPath = "./Dataset/Wildtrack_dataset/annotations_positions";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(directoryPath), "*.json")) {
            for (Path entry : stream) {
                try {
                    String fileName = entry.getFileName().toString();
                    int frameNumber = Integer.parseInt(fileName.replace(".json", ""));
                    List<PersonView> personViews = JsonReader.readPersonViewsFromJson(entry.toString());
                    for (PersonView personView : personViews) {
                        personView.setFrameNumber(frameNumber);
                        personView.setTimeStamp(timeTracker);
                        runtime.getEventService().sendEventBean(personView, "personView");
                    }
                    timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);
                } catch (IOException e) {
                    System.err.println("Error reading JSON file: " + entry + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Error accessing directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Streams a series of predefined sensor data and device command events to the EPRuntime.
     * Each event is followed by a time advancement step to simulate real-time event processing.
     *
     * @param runtime The EPRuntime instance used to send event beans.
     */
    private void streamDeviceCommands(EPRuntime runtime){
        runtime.getEventService().sendEventBean(new SensorData(10, "101", "temp_sensor", 18002000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new PersonView(timeTracker, 122, 0, 456826, List.of(new PersonView.View(0, 1561, 1510, 299, 139))), "personView");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(5, "102", "camera", 18001000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(3, "104", "mic", 18002000L), "sensorData");
        runtime.getEventService().sendEventBean(new DeviceCommand("101", "Set Temperature", "24°C", 18002000L), "deviceCommand");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(7, "107", "camera", 18001000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(10, "106", "screen", 18005000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(10, "103", "mobile", 18004000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(44, "101", "temp_sensor", 18006000L), "sensorData");
        runtime.getEventService().sendEventBean(new SensorData(10, "109", "wash-machine", 18006000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

        runtime.getEventService().sendEventBean(new SensorData(10, "105", "mic", 18007000L), "sensorData");
        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);
    }

    private void streamEmbeddingFeatures(EPRuntime runtime){

        try {
            INDArray data = Nd4j.createFromNpyFile(new File("/home/mahmoud-tayee/Masters/AIC24_Track1_YACHIYO_RIIPS/EmbedFeature/scene_001/camera_0001/feature_1_1_716_748_239_309_09949760437011719.npy"));

            // Convert float[] to List<Float> manually
            float[] featureArray = data.toFloatVector();
            List<Float> featureList = new ArrayList<>();
            for (float value : featureArray) {
                featureList.add(value);
            }

            // Send event with List<Float>
            runtime.getEventService().sendEventBean(new EmbeddingFeature(timeTracker, featureList), "embeddingFeature");

            timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);

            System.out.println("Loaded .npy file: " + data);
        } catch (Exception e) {
            System.err.println("Error loading .npy file: " + e.getMessage());
            e.printStackTrace();
        }

    }

    public void generateEvents(EPRuntime runtime) {
        streamWildTrackDataset(runtime);
        streamEmbeddingFeatures(runtime);
    }
}
