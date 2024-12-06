package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.SensorData.SensorData;
import com.espertech.esper.runtime.client.EPRuntime;
import java.util.LinkedList;

public class IotStreamGenerator {

    public LinkedList<Object> createEventStream() {
        LinkedList<Object> events = new LinkedList<>();

        // Adding sample SensorData objects to the event stream
        events.add(new SensorData(10, "101", "temp_sensor", 18002000L));
        events.add(new SensorData(5, "102", "camera", 18001000L));

        events.add(new SensorData(3, "104", "mic", 18002000L));
        events.add(new SensorData(7, "107", "camera", 18001000L));

        events.add(new SensorData(10, "106", "screen", 18005000L));
        events.add(new SensorData(10, "103", "mobile", 18004000L));

        events.add(new SensorData(10, "109", "wash-machine", 18006000L));
        events.add(new SensorData(10, "105", "mic", 18007000L));

        return events;
    }

    public void generateEvents(EPRuntime runtime) {
        LinkedList<Object> events = createEventStream();
        
        // Send each event and advance time
        long lastEventTime = 0; // Track time of the last event
        
        // Fixed time step (1 second) to advance time after each event
        long timeStep = 1000L;  // 1 second (in milliseconds)

        // Track current system time (or starting time) for advancing time
        long currentTime = System.currentTimeMillis();

        for (Object event : events) {
            SensorData sensorData = (SensorData) event;
            
            // Send the event
            runtime.getEventService().sendEventBean(sensorData, "sensorData");
            
            /**************************************************************************** */
            currentTime += timeStep;

            /*This is to advance time with a specific time step */
            // Advance time by the fixed time step.
            runtime.getEventService().advanceTime(currentTime);

            // Optionally, log the time advancement
            System.out.println("Time advanced to: " + currentTime + " ms");
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
}
