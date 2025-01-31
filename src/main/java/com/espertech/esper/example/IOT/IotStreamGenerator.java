package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.SensorData.SensorData;

import java.util.List;
import java.nio.file.*;
import com.espertech.esper.example.IOT.helpers.JsonReader;
import com.espertech.esper.example.IOT.DeviceCommand.DeviceCommand;
import com.espertech.esper.example.IOT.PersonView.PersonView;
import com.espertech.esper.runtime.client.EPRuntime;
import java.io.IOException;
public class IotStreamGenerator {
    private long advanceTime(EPRuntime runtime, long timeTracker, long timeStep){
        long currentTime = timeTracker + timeStep;
        
        /*This is to advance time with a specific time step */
        runtime.getEventService().advanceTime(currentTime);

        // Optionally, log the time advancement
        System.out.println("Time advanced to: " + currentTime + " ms");

        return currentTime;
    }
    
    public void generateEvents(EPRuntime runtime) {        
        // Fixed time step (1 second) to advance time after each event
        long oneSecTimeStep = 1000L;  // 1 second (in milliseconds)

        // Track current starting time for advancing time.
        long timeTracker = System.currentTimeMillis();

        String directoryPath = "D:\\Moi\\Masters\\DeepCEP\\esperee-9.0.0\\examples\\examples-esper\\esperProgressive\\Dataset\\Wildtrack_dataset\\annotations_positions";
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
                        timeTracker = advanceTime(runtime, timeTracker, oneSecTimeStep);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading JSON file: " + entry + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Error accessing directory: " + e.getMessage());
            e.printStackTrace();
        }


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
        /**************************************************************************** */

        /**************************************************************************** */
        // /*This is to advance with a column; here timestamp. */
        // // Advance time based on the difference between the event time and the last event time
        // long timeToAdvance = sensorData.getTimestamp() - lastEventTime;
        
        // // If there's a time difference, advance time
        // if (timeToAdvance > 0) {
        //     runtime.getEventService().advanceTime(lastEventTime);
        // }
    
        // lastEventTime = sensorData.getTimestamp();
        /**************************************************************************** */
    }
}
