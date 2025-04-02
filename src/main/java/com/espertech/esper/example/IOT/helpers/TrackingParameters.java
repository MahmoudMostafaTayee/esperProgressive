package com.espertech.esper.example.IOT.helpers;

public class TrackingParameters {
    public double epsilonScpt = 0.10;
    public int timePeriod = 2;
    public int fps = 1;
    public double epsilonMcpt = 0.37;
    public int shortTrackTh = 120;
    public int keypointConditionTh = 1;
    public boolean replaceSimilarityByWCoordinate = true;
    public String distanceType = "min";
    public int distanceTh = 10;
    public double simTh = 0.85;
    public int deleteGidTh = 5000;

    @Override
    public String toString() {
        return "TrackingParameters{" +
                "epsilonScpt=" + epsilonScpt +
                ", timePeriod=" + timePeriod +
                ", epsilonMcpt=" + epsilonMcpt +
                ", shortTrackTh=" + shortTrackTh +
                ", keypointConditionTh=" + keypointConditionTh +
                ", replaceSimilarityByWCoordinate=" + replaceSimilarityByWCoordinate +
                ", distanceType='" + distanceType + '\'' +
                ", distanceTh=" + distanceTh +
                ", simTh=" + simTh +
                ", deleteGidTh=" + deleteGidTh +
                '}';
    }
}

