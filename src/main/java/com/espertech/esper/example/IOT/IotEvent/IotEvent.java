package com.espertech.esper.example.IOT.IotEvent;

public class IotEvent {
    private int value;
    private String deviceId;
    private String type;
    private long timestamp;

    public IotEvent(int value, String deviceId, String type, long timestamp) {
        this.value = value;
        this.deviceId = deviceId;
        this.type = type;
        this.timestamp = timestamp;
    }

    public int getValue() {
        return value;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
