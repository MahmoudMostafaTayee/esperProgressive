package com.espertech.esper.example.IOT.events;

/**
 * Event emitted when a global track ID is assigned to a local tracklet.
 * This event can be used for visualization, logging, or downstream processing.
 */
public class GlobalTrackAssignmentEvent {
    private int globalTrackId;
    private int cameraId;
    private int localTrackId;
    private long timestamp;
    private double confidence; // Optional: confidence score for the assignment

    public GlobalTrackAssignmentEvent() {
    }

    public GlobalTrackAssignmentEvent(int globalTrackId, int cameraId, int localTrackId, long timestamp) {
        this.globalTrackId = globalTrackId;
        this.cameraId = cameraId;
        this.localTrackId = localTrackId;
        this.timestamp = timestamp;
        this.confidence = 1.0; // Default confidence
    }

    public GlobalTrackAssignmentEvent(int globalTrackId, int cameraId, int localTrackId,
            long timestamp, double confidence) {
        this.globalTrackId = globalTrackId;
        this.cameraId = cameraId;
        this.localTrackId = localTrackId;
        this.timestamp = timestamp;
        this.confidence = confidence;
    }

    // Getters and Setters
    public int getGlobalTrackId() {
        return globalTrackId;
    }

    public void setGlobalTrackId(int globalTrackId) {
        this.globalTrackId = globalTrackId;
    }

    public int getCameraId() {
        return cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    public int getLocalTrackId() {
        return localTrackId;
    }

    public void setLocalTrackId(int localTrackId) {
        this.localTrackId = localTrackId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return String.format("GlobalTrackAssignmentEvent{globalId=%d, camera=%d, localId=%d, confidence=%.2f}",
                globalTrackId, cameraId, localTrackId, confidence);
    }
}
