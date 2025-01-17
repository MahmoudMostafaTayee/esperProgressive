package com.espertech.esper.example.IOT;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericIotEventListener implements UpdateListener {

    private static final Logger log = LoggerFactory.getLogger(GenericIotEventListener.class);
    private final String name;

    public GenericIotEventListener(String name) {
        this.name = name;
    }

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            for (EventBean event : newEvents) {
                logEvent(event);
            }
        }
    }

    private void logEvent(EventBean event) {
        String[] propertyNames = event.getEventType().getPropertyNames();
        StringBuilder eventDetails = new StringBuilder(name).append(": ");

        for (String property : propertyNames) {
            Object value = event.get(property);
            eventDetails.append(property).append("=").append(value).append(", ");
        }

        // Remove trailing comma and space
        if (eventDetails.length() > 0) {
            eventDetails.setLength(eventDetails.length() - 2);
        }

        System.out.println(eventDetails.toString());
    }
}
