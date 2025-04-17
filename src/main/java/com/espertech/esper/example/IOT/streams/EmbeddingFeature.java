package com.espertech.esper.example.IOT.streams;

import java.util.List;

public class EmbeddingFeature {
    private long timestamp;
    private List<Float> features;
    private int curFrame;
    private int uNum;
    private int x1, x2, y1, y2;
    private float conf;
    long numberOfFrames;

    public EmbeddingFeature(List<Float> features, int uNum, int curFrame, long numberOfFrames, long timestamp) {
        this.features = features;
        this.uNum = uNum;
        this.curFrame = curFrame;
        this.numberOfFrames = numberOfFrames;
        this.timestamp = timestamp;
    }

    public EmbeddingFeature(long timestamp, int uNum, int curFrame) {
        this.uNum = uNum;
        this.curFrame = curFrame;
        this.timestamp = timestamp;
    }

    public EmbeddingFeature(long timestamp, int curFrame, int uNum, int x1, int x2, int y1, int y2, float conf) {
        this.timestamp = timestamp;
        this.curFrame = curFrame;
        this.uNum = uNum;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.conf = conf;
    }

    public EmbeddingFeature(long timestamp, List<Float> features, int curFrame, int uNum, int x1, int x2, int y1, int y2, float conf) {
        this.timestamp = timestamp;
        this.features = features;
        this.curFrame = curFrame;
        this.uNum = uNum;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.conf = conf;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Float> getFeatures() {
        return features;
    }

    public int getCurFrame() {
        return curFrame;
    }

    public int getUNum() {
        return uNum;
    }

    public long getFrameRecordCount() {
        return numberOfFrames;
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
        return "EmbeddingFeature{" +
                "timestamp=" + timestamp +
                ", curFrame=" + curFrame +
                ", uNum=" + uNum +
                ", x1=" + x1 +
                ", x2=" + x2 +
                ", y1=" + y1 +
                ", y2=" + y2 +
                ", conf=" + conf +
                ", features=" + features.size() + " values" +
                '}';
    }
}