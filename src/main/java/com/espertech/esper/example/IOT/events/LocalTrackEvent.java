package com.espertech.esper.example.IOT.events;

import java.util.List;

public class LocalTrackEvent {
    private String cameraId;
    private int localTrackId;
    private long startTime;
    private long endTime;
    private List<double[]> features;
    private int uniqueId;
    private List<com.espertech.esper.example.IOT.utils.DetectedUser> detectedUsers;

    public LocalTrackEvent(String cameraId, int localTrackId, long startTime, long endTime, List<double[]> features,
            int uniqueId, List<com.espertech.esper.example.IOT.utils.DetectedUser> detectedUsers) {
        this.cameraId = cameraId;
        this.localTrackId = localTrackId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.features = features;
        this.uniqueId = uniqueId;
        this.detectedUsers = detectedUsers;
    }

    public String getCameraId() {
        return cameraId;
    }

    public int getLocalTrackId() {
        return localTrackId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<double[]> getFeatures() {
        return features;
    }

    public int getUniqueId() {
        return uniqueId;
    }

    public List<com.espertech.esper.example.IOT.utils.DetectedUser> getDetectedUsers() {
        return detectedUsers;
    }

    @Override
    public String toString() {
        return "LocalTrackEvent{" +
                "cameraId='" + cameraId + '\'' +
                ", localTrackId=" + localTrackId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", featuresCount=" + (features != null ? features.size() : 0) +
                ", uniqueId=" + uniqueId +
                '}';
    }
}
