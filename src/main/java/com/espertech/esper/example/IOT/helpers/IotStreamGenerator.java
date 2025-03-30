package com.espertech.esper.example.IOT.helpers;

import com.espertech.esper.example.IOT.SensorData.SensorData;

import java.util.List;
import java.nio.file.*;

import com.espertech.esper.example.IOT.DeviceCommand.DeviceCommand;
import com.espertech.esper.example.IOT.PersonView.PersonView;
import com.espertech.esper.example.IOT.EmbeddingFeature.EmbeddingFeature;
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

    private void streamEmbeddingFeatures(EPRuntime runtime) {
        String basePath = "/home/mahmoud-tayee/Masters/AIC24_Track1_YACHIYO_RIIPS/EmbedFeature";

        // Regex pattern for extracting filename parts
        Pattern pattern = Pattern.compile("feature_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+\\.?\\d*)\\.npy");

        try (DirectoryStream<Path> scenes = Files.newDirectoryStream(Paths.get(basePath))) {
            for (Path scene : scenes) {
                if (!Files.isDirectory(scene)) continue;

                try (DirectoryStream<Path> camerasStream = Files.newDirectoryStream(scene)) {
                    List<Path> cameras = new ArrayList<>();
                    for (Path camera : camerasStream) {
                        if (Files.isDirectory(camera)) {
                            cameras.add(camera);
                        }
                    }

                    // Sort the list (alphabetically by default)
                    cameras.sort(Comparator.naturalOrder());
                    for (Path camera : cameras) {
                        if (!Files.isDirectory(camera)) continue;

                        String sceneCameraPath = scene.getFileName() + "/" + camera.getFileName();
                        System.out.println("Processing: " + sceneCameraPath);

                        // Collect files into a list and sort them
                        List<Path> files = new ArrayList<>();
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(camera, "*.npy")) {
                            for (Path entry : stream) {
                                files.add(entry);
                            }
                        }

                        // Sort files based on filename
                        files.sort(Comparator.comparing(p -> p.getFileName().toString()));

                        int prevFrame = 1;  // Track previous frame number
                        for (Path entry : files) {
                            String fileName = entry.getFileName().toString();
                            Matcher matcher = pattern.matcher(fileName);

                            if (!matcher.matches()) {
                                System.err.println("Skipping file (invalid format): " + fileName);
                                continue;
                            }

                            try {
                                File npyFile = entry.toFile();
                                INDArray data = Nd4j.createFromNpyFile(npyFile);

                                // Convert to List<Float>
                                float[] featureArray = data.toFloatVector();
                                List<Float> featureList = new ArrayList<>();
                                for (float value : featureArray) {
                                    featureList.add(value);
                                }

                                // Extract values from filename
                                int curFrame = Integer.parseInt(matcher.group(1));
                                int uNum = Integer.parseInt(matcher.group(2));
                                int x1 = Integer.parseInt(matcher.group(3));
                                int x2 = Integer.parseInt(matcher.group(4));
                                int y1 = Integer.parseInt(matcher.group(5));
                                int y2 = Integer.parseInt(matcher.group(6));
                                float conf = Float.parseFloat(matcher.group(7));

                                // Advance time only if the frame number changes
                                if (curFrame != prevFrame) {
                                    timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);
                                    prevFrame = curFrame;  // Update previous frame number
                                }

                                // Send event
                                runtime.getEventService().sendEventBean(
                                        new EmbeddingFeature(timeTracker, featureList, curFrame, uNum, x1, x2, y1, y2, conf),
                                        "embeddingFeature"
                                );

//                                System.out.println("Processed: " + sceneCameraPath + "/" + fileName);

                            } catch (Exception e) {
                                System.err.println("Error processing file: " + fileName + " - " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error accessing scene directory: " + scene + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error accessing base directory: " + basePath + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void generateEvents(EPRuntime runtime) {
//        streamWildTrackDataset(runtime);
        streamEmbeddingFeatures(runtime);
    }
}
