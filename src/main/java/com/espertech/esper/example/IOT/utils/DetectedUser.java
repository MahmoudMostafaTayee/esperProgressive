package com.espertech.esper.example.IOT.utils;

import java.util.List;

public class DetectedUser {
    private List<Float> features;
    private int uNum;
    private int x1, x2, y1, y2;
    private float conf;
    private int frameNumber;

    public DetectedUser(List<Float> features, int uNum, int x1, int x2, int y1, int y2, float conf) {
        this.features = features;
        this.uNum = uNum;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.conf = conf;
    }

    public List<Float> getFeatures() {
        return features;
    }

    public int getUNum() {
        return uNum;
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

    public int getFrameNumber() {
        return frameNumber;
    }

    public void setFrameNumber(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    @Override
    public String toString() {
        return "DetectedUser{" +
                "uNum=" + uNum +
                ", bbox=[" + x1 + "," + y1 + "," + x2 + "," + y2 + "]" +
                ", conf=" + conf +
                ", features=" + features.size() + " values" +
                '}';
    }
}