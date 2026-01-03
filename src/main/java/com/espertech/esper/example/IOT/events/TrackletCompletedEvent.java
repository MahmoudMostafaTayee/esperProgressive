package com.espertech.esper.example.IOT.events;

import java.util.List;

/**
 * Event emitted when a tracklet (local track) completes in single-camera
 * tracking.
 * This event triggers cross-camera matching in the MCPT pipeline.
 */
public class TrackletCompletedEvent {
    private int cameraId;
    private int localTrackId;
    private long startTimestamp;
    private long endTimestamp;
    private List<Integer> frameNumbers;
    private List<Integer[]> boundingBoxes;
    private String representativeFeaturePath; // Path to .npy file or feature identifier
    private double[] worldCoordinates; // [x, y] in world space (average or representative)
    private int trackletLength; // Number of frames in tracklet

    public TrackletCompletedEvent() {
    }

    public TrackletCompletedEvent(int cameraId, int localTrackId, long startTimestamp, long endTimestamp,
            List<Integer> frameNumbers, List<Integer[]> boundingBoxes,
            String representativeFeaturePath, double[] worldCoordinates) {
        this.cameraId = cameraId;
        this.localTrackId = localTrackId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.frameNumbers = frameNumbers;
        this.boundingBoxes = boundingBoxes;
        this.representativeFeaturePath = representativeFeaturePath;
        this.worldCoordinates = worldCoordinates;
        this.trackletLength = frameNumbers != null ? frameNumbers.size() : 0;
    }

    // Getters and Setters
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

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public List<Integer> getFrameNumbers() {
        return frameNumbers;
    }

    public void setFrameNumbers(List<Integer> frameNumbers) {
        this.frameNumbers = frameNumbers;
        this.trackletLength = frameNumbers != null ? frameNumbers.size() : 0;
    }

    public List<Integer[]> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<Integer[]> boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }

    public String getRepresentativeFeaturePath() {
        return representativeFeaturePath;
    }

    public void setRepresentativeFeaturePath(String representativeFeaturePath) {
        this.representativeFeaturePath = representativeFeaturePath;
    }

    public double[] getWorldCoordinates() {
        return worldCoordinates;
    }

    public void setWorldCoordinates(double[] worldCoordinates) {
        this.worldCoordinates = worldCoordinates;
    }

    public int getTrackletLength() {
        return trackletLength;
    }

    public void setTrackletLength(int trackletLength) {
        this.trackletLength = trackletLength;
    }

    @Override
    public String toString() {
        return String.format("TrackletCompletedEvent{camera=%d, localId=%d, length=%d, start=%d, end=%d}",
                cameraId, localTrackId, trackletLength, startTimestamp, endTimestamp);
    }
}
