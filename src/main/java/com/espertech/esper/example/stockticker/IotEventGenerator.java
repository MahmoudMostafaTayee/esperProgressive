package com.espertech.esper.example.stockticker;

import java.util.LinkedList;

public class IotEventGenerator {

    public LinkedList<Object> createEventStream() {
        LinkedList<Object> events = new LinkedList<>();

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
}
