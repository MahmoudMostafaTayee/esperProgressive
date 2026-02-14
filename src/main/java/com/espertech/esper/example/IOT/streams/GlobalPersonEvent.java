package com.espertech.esper.example.IOT.streams;

import java.util.Map;

public class GlobalPersonEvent {
    private int globalId;
    private int cameraId;
    private String serial;
    private int localId;
    private int x;
    private int y;
    private long timestamp;
    private Map<String, Object> attributes;

    public GlobalPersonEvent(int globalId, int cameraId, String serial, int localId, int x, int y, long timestamp,
            Map<String, Object> attributes) {
        this.globalId = globalId;
        this.cameraId = cameraId;
        this.serial = serial;
        this.localId = localId;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.attributes = attributes;
    }

    public int getGlobalId() {
        return globalId;
    }

    public int getCameraId() {
        return cameraId;
    }

    public String getSerial() {
        return serial;
    }

    public int getLocalId() {
        return localId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "GlobalPersonEvent{" +
                "globalId=" + globalId +
                ", cameraId=" + cameraId +
                ", serial='" + serial + '\'' +
                ", localId=" + localId +
                ", x=" + x +
                ", y=" + y +
                ", timestamp=" + timestamp +
                ", attributes=" + attributes +
                '}';
    }
}
