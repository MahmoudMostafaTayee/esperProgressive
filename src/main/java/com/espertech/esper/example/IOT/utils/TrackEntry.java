package com.espertech.esper.example.IOT.utils;

import java.util.List;

public class TrackEntry {
    private int frameNumber;
    private int uniqueId;
    private WorldCoordinate worldCoordinate;
    private Coordinate imageCoordinate; // optional
    List<Float> featureList;

    public TrackEntry(int frameNumber, int uniqueId, WorldCoordinate worldCoordinate, Coordinate imageCoordinate) {
        this.frameNumber = frameNumber;
        this.uniqueId = uniqueId;
        this.worldCoordinate = worldCoordinate;
        this.imageCoordinate = imageCoordinate;
    }

    public TrackEntry(int uniqueId, int frameNumber, List<Float> featureList) {
        this.frameNumber = frameNumber;
        this.uniqueId = uniqueId;
        this.featureList = featureList;
        this.worldCoordinate = null;
        this.imageCoordinate = null;
    }

    // Inner class
    public static class WorldCoordinate {
        private double x;
        private double y;

        public WorldCoordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }

        // Getters
        public double getX() { return x; }
        public double getY() { return y; }

        @Override
        public String toString() {
            return "WorldCoordinate{x=" + x + ", y=" + y + "}";
        }
    }

    public static class Coordinate {
        private int x;
        private int y;

        public Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y; }

        @Override
        public String toString() {
            return "Coordinate{x=" + x + ", y=" + y + "}";
        }
    }
    public void setUniqueId(int id) { this.uniqueId = id; }

    // Getters
    public int getFrameNumber() { return frameNumber; }
    public int getUniqueId() { return uniqueId; }
    public WorldCoordinate getWorldCoordinate() { return worldCoordinate; }
    public Coordinate getImageCoordinate() { return imageCoordinate; }
    public List<Float> getFeatureList() { return featureList; }

    @Override
    public String toString() {
        return "TrackEntry{" +
                "frameNumber=" + frameNumber +
                ", uniqueId=" + uniqueId +
                ", worldCoordinate=" + (worldCoordinate != null ? worldCoordinate : "null") +
                ", imageCoordinate=" + (imageCoordinate != null ? imageCoordinate : "null") +
                '}';
    }
}

