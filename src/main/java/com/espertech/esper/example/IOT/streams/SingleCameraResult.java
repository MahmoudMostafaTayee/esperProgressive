package com.espertech.esper.example.IOT.streams;

import java.util.List;

public class SingleCameraResult {
    private String cameraId;
    private int windowIndex;
    private long timestamp;
    private List<Integer> clusterLabels;
    private List<Integer> idList;
    private List<Integer[]> boundingBoxList;
    private List<double[]> featureList;
    private List<List<List<Float>>> keypointsList;
    private List<Integer> frameNumbers;
    private List<Long> timestamps;

    public SingleCameraResult(String cameraId, int windowIndex, long timestamp, List<Integer> clusterLabels,
            List<Integer> idList, List<Integer[]> boundingBoxList, List<double[]> featureList,
            List<List<List<Float>>> keypointsList, List<Integer> frameNumbers, List<Long> timestamps) {
        this.cameraId = cameraId;
        this.windowIndex = windowIndex;
        this.timestamp = timestamp;
        this.clusterLabels = clusterLabels;
        this.idList = idList;
        this.boundingBoxList = boundingBoxList;
        this.featureList = featureList;
        this.keypointsList = keypointsList;
        this.frameNumbers = frameNumbers;
        this.timestamps = timestamps;
    }

    public String getCameraId() {
        return cameraId;
    }

    public int getWindowIndex() {
        return windowIndex;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Integer> getClusterLabels() {
        return clusterLabels;
    }

    public List<Integer> getIdList() {
        return idList;
    }

    public List<Integer[]> getBoundingBoxList() {
        return boundingBoxList;
    }

    public List<double[]> getFeatureList() {
        return featureList;
    }

    public List<List<List<Float>>> getKeypointsList() {
        return keypointsList;
    }

    public List<Integer> getFrameNumbers() {
        return frameNumbers;
    }

    public List<Long> getTimestamps() {
        return timestamps;
    }

    @Override
    public String toString() {
        return "SingleCameraResult{" +
                "cameraId='" + cameraId + '\'' +
                ", windowIndex=" + windowIndex +
                ", timestamp=" + timestamp +
                ", clusters=" + clusterLabels.size() +
                '}';
    }
}
