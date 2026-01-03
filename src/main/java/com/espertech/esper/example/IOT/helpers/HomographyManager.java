package com.espertech.esper.example.IOT.helpers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages homography matrices for transforming camera coordinates to world
 * coordinates.
 * Loads calibration data from JSON files and caches matrices for performance.
 */
public class HomographyManager {
    private static final Gson gson = new Gson();
    private static final Map<Integer, double[][]> homographyCache = new HashMap<>();
    private static String calibrationBasePath = "Original/scene_001";

    /**
     * Sets the base path for calibration files.
     * 
     * @param basePath Base directory containing camera folders (e.g.,
     *                 "Original/scene_001")
     */
    public static void setCalibrationBasePath(String basePath) {
        calibrationBasePath = basePath;
        homographyCache.clear(); // Clear cache when path changes
    }

    /**
     * Gets the homography matrix for a specific camera.
     * 
     * @param cameraId Camera ID (e.g., 1 for camera_0001)
     * @return 3x3 homography matrix
     * @throws IOException If calibration file cannot be read
     */
    public static double[][] getHomographyMatrix(int cameraId) throws IOException {
        // Check cache first
        if (homographyCache.containsKey(cameraId)) {
            return homographyCache.get(cameraId);
        }

        // Load from file
        String calibrationPath = String.format("%s/camera_%04d/calibration.json",
                calibrationBasePath, cameraId);

        try (FileReader reader = new FileReader(calibrationPath)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            // Parse homography matrix
            double[][] matrix = gson.fromJson(
                    json.get("homography matrix"),
                    double[][].class);

            // Cache for future use
            homographyCache.put(cameraId, matrix);

            return matrix;
        }
    }

    /**
     * Transforms camera coordinates to world coordinates using homography.
     * 
     * @param cameraId Camera ID
     * @param x        X coordinate in camera space
     * @param y        Y coordinate in camera space
     * @return World coordinates [x, y]
     * @throws IOException If calibration file cannot be read
     */
    public static double[] toWorldCoordinates(int cameraId, double x, double y) throws IOException {
        double[][] H = getHomographyMatrix(cameraId);

        // Apply homography transformation
        // [x', y', z'] = H * [x, y, 1]
        // world_x = x' / z', world_y = y' / z'

        double xPrime = H[0][0] * x + H[0][1] * y + H[0][2];
        double yPrime = H[1][0] * x + H[1][1] * y + H[1][2];
        double zPrime = H[2][0] * x + H[2][1] * y + H[2][2];

        return new double[] { xPrime / zPrime, yPrime / zPrime };
    }

    /**
     * Computes the average world coordinate for a tracklet.
     * 
     * @param cameraId      Camera ID
     * @param boundingBoxes List of bounding boxes [x1, x2, y1, y2]
     * @return Average world coordinates [x, y]
     * @throws IOException If calibration file cannot be read
     */
    public static double[] computeAverageWorldCoordinate(int cameraId,
            java.util.List<Integer[]> boundingBoxes)
            throws IOException {
        double sumX = 0;
        double sumY = 0;
        int count = 0;

        for (Integer[] bbox : boundingBoxes) {
            // Use center-x and bottom-y as representative point
            double centerX = (bbox[0] + bbox[1]) / 2.0;
            double bottomY = bbox[3];

            double[] worldCoord = toWorldCoordinates(cameraId, centerX, bottomY);
            sumX += worldCoord[0];
            sumY += worldCoord[1];
            count++;
        }

        if (count == 0) {
            return new double[] { 0, 0 };
        }

        return new double[] { sumX / count, sumY / count };
    }

    /**
     * Computes Euclidean distance between two world coordinate points.
     * 
     * @param coord1 First world coordinate [x, y]
     * @param coord2 Second world coordinate [x, y]
     * @return Euclidean distance
     */
    public static double computeDistance(double[] coord1, double[] coord2) {
        double dx = coord1[0] - coord2[0];
        double dy = coord1[1] - coord2[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Clears the homography matrix cache.
     */
    public static void clearCache() {
        homographyCache.clear();
    }

    /**
     * Preloads homography matrices for all cameras.
     * 
     * @param cameraIds List of camera IDs to preload
     */
    public static void preloadMatrices(int[] cameraIds) {
        for (int cameraId : cameraIds) {
            try {
                getHomographyMatrix(cameraId);
            } catch (IOException e) {
                System.err.println(
                        "Warning: Could not preload homography for camera " + cameraId + ": " + e.getMessage());
            }
        }
    }
}
