package com.espertech.esper.example.IOT;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotEventListener implements UpdateListener {

    private static final Logger log = LoggerFactory.getLogger(IotEventListener.class);

    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            for (EventBean event : newEvents) {
                logEvent(event);
            }
        }
    }

    private void logEvent(EventBean event) {
        // log.info("------ Matched Event: " + event.get("count_num")+ 
        //  ", total: " + event.get("total"));
        log.info("Matched Event: deviceId=" + event.get("deviceId") +
                ", value=" + event.get("value") +
                ", timestamp=" + event.get("timestamp"));
    }
}
