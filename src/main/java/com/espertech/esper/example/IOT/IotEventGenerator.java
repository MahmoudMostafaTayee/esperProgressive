package com.espertech.esper.example.IOT;

import com.espertech.esper.runtime.client.EPRuntime;
import java.util.LinkedList;

public class IotEventGenerator {

    public LinkedList<Object> createEventStream() {
        LinkedList<Object> events = new LinkedList<>();

        // Adding sample IotEvent objects to the event stream
        events.add(new IotEvent(10, "J1", "null", 18002000L));
        events.add(new IotEvent(5, "J2", "null", 18001000L));

        events.add(new IotEvent(3, "J3", "null", 18002000L));
        events.add(new IotEvent(7, "J4", "null", 18001000L));

        events.add(new IotEvent(10, "I1", "null", 18005000L));
        events.add(new IotEvent(10, "I2", "null", 18004000L));

        events.add(new IotEvent(10, "I3", "null", 18006000L));
        events.add(new IotEvent(10, "I4", "null", 18007000L));

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
            IotEvent iotEvent = (IotEvent) event;
            
            // Send the event
            runtime.getEventService().sendEventBean(iotEvent, "iotEvent");
            
            /**************************************************************************** */
            currentTime += timeStep;

            /*This is to advance time with a specific time step */
            // Advance time by the fixed time step.
            runtime.getEventService().advanceTime(currentTime);

            // Optionally, log the time advancement
            System.out.println("Time advanced to: " + currentTime + " ms");
            /**************************************************************************** */

            /**************************************************************************** */
            // /*This is to advance with a column; here dateCreated. */
            // // Advance time based on the difference between the event time and the last event time
            // long timeToAdvance = iotEvent.getDateCreated() - lastEventTime;
            
            // // If there's a time difference, advance time
            // if (timeToAdvance > 0) {
            //     runtime.getEventService().advanceTime(lastEventTime);
            // }
        
            // lastEventTime = iotEvent.getDateCreated();
            /**************************************************************************** */
        }
    }
}
