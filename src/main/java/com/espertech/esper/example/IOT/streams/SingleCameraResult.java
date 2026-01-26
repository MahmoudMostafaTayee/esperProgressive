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

    public SingleCameraResult(String cameraId, int windowIndex, long timestamp, List<Integer> clusterLabels,
            List<Integer> idList, List<Integer[]> boundingBoxList, List<double[]> featureList) {
        this.cameraId = cameraId;
        this.windowIndex = windowIndex;
        this.timestamp = timestamp;
        this.clusterLabels = clusterLabels;
        this.idList = idList;
        this.boundingBoxList = boundingBoxList;
        this.featureList = featureList;
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
