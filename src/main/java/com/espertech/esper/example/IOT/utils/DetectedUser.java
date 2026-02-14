package com.espertech.esper.example.IOT.utils;

import java.util.List;

public class DetectedUser {
    private List<Float> features;
    private int uNum;
    private int frameNum;
    private int x1, x2, y1, y2;
    private float conf;
    private List<List<Float>> keypoints;

    public DetectedUser(List<Float> features, int uNum, int frameNum, int x1, int x2, int y1, int y2, float conf,
            List<List<Float>> keypoints) {
        this.keypoints = keypoints;
        this.features = features;
        this.uNum = uNum;
        this.frameNum = frameNum;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.conf = conf;
    }

    public List<List<Float>> getKeypoints() {
        return keypoints;
    }

    public List<Float> getFeatures() {
        return features;
    }

    public int getUNum() {
        return uNum;
    }

    public int getFrameNum() {
        return frameNum;
    }

    public int getX1() {
        return x1;
    }

    public int getX2() {
        return x2;
    }

    public int getY1() {
        return y1;
    }

    public int getY2() {
        return y2;
    }

    public float getConf() {
        return conf;
    }

    @Override
    public String toString() {
        return "DetectedUser{" +
                "uNum=" + uNum +
                ", frameNum=" + frameNum +
                ", bbox=[" + x1 + "," + y1 + "," + x2 + "," + y2 + "]" +
                ", conf=" + conf +
                ", features=" + features.size() + " values" +
                ", keypoints=" + (keypoints != null ? keypoints.size() : 0) +
                '}';
    }
}