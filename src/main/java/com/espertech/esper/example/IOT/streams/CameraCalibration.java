package com.espertech.esper.example.IOT.streams;

public class CameraCalibration {
    private int cameraId;
    private double[][] homographyMatrix;

    public CameraCalibration(int cameraId, double[][] homographyMatrix) {
        this.cameraId = cameraId;
        this.homographyMatrix = homographyMatrix;
    }

    public int getCameraId() {
        return cameraId;
    }

    public double[][] getHomographyMatrix() {
        return homographyMatrix;
    }

    @Override
    public String toString() {
        return "CameraCalibration{" +
                "cameraId=" + cameraId +
                ", homographyMatrix=" + (homographyMatrix != null ? "present" : "null") +
                '}';
    }
}
