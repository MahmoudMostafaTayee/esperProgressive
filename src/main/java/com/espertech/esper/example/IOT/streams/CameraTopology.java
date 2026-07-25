package com.espertech.esper.example.IOT.streams;

public class CameraTopology {
    private String cameraId;
    private String neighborId;
    private boolean enabled;

    public CameraTopology(String cameraId, String neighborId, boolean enabled) {
        this.cameraId = cameraId;
        this.neighborId = neighborId;
        this.enabled = enabled;
    }

    public String getCameraId() {
        return cameraId;
    }

    public String getNeighborId() {
        return neighborId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
