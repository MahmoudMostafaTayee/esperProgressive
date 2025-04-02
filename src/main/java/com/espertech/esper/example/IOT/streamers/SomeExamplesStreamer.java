package com.espertech.esper.example.IOT.streamers;

import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.runtime.client.EPRuntime;
import java.util.List;
import com.espertech.esper.example.IOT.streams.SensorData;
import com.espertech.esper.example.IOT.streams.DeviceCommand;
import com.espertech.esper.example.IOT.streams.PersonView;

public class SomeExamplesStreamer {
    private static long timeTracker = System.currentTimeMillis();

    public static void streamSomeExamples() {
        sendEvent(new SensorData(10, "101", "temp_sensor", 18002000L), "sensorData");
        sendEvent(new PersonView(timeTracker, 122, 0, 456826, List.of(new PersonView.View(0, 1561, 1510, 299, 139))), "personView");
        sendEvent(new SensorData(5, "102", "camera", 18001000L), "sensorData");
        sendEvent(new SensorData(3, "104", "mic", 18002000L), "sensorData");
        sendEvent(new DeviceCommand("101", "Set Temperature", "24°C", 18002000L), "deviceCommand");
        sendEvent(new SensorData(7, "107", "camera", 18001000L), "sensorData");
        sendEvent(new SensorData(10, "106", "screen", 18005000L), "sensorData");
        sendEvent(new SensorData(10, "103", "mobile", 18004000L), "sensorData");
        sendEvent(new SensorData(44, "101", "temp_sensor", 18006000L), "sensorData");
        sendEvent(new SensorData(10, "109", "wash-machine", 18006000L), "sensorData");
        sendEvent(new SensorData(10, "105", "mic", 18007000L), "sensorData");
    }

    private static void sendEvent(Object event, String eventType) {
        EventEPLUtil.streamEvent(event, eventType);
        timeTracker = EventEPLUtil.advanceTime();
    }
}

