package com.espertech.esper.example.IOT.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimilarityUtils {
    private static final Logger logger = LoggerFactory.getLogger(SimilarityUtils.class);

    public static double cosineSimilarity(List<Float> features1, List<Float> features2) {
        if (features1 == null || features2 == null || features1.size() != features2.size()) {
            throw new IllegalArgumentException("Feature lists must be non-null and of the same size");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < features1.size(); i++) {
            float f1 = features1.get(i);
            float f2 = features2.get(i);
            dotProduct += f1 * f2;
            normA += f1 * f1;
            normB += f2 * f2;
        }

        return (normA == 0 || normB == 0) ? 0 : (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public static double[][] computeCosineDistanceMatrix(double[][] features, double epsilon) {
        int n = features.length;
        double[][] distMatrix = new double[n][n];
        double threshold = 1 - epsilon;

        for (int i = 0; i < n; i++) {
            distMatrix[i][i] = 0; // Zero diagonal

            for (int j = i + 1; j < n; j++) {
                double similarity = cosineSimilarity(features[i], features[j]);
                double distance = 1.0 - similarity;

                // Apply epsilon threshold
                distMatrix[i][j] = distMatrix[j][i] = (similarity < threshold) ? 1.0 : distance;
            }
        }
        return distMatrix;
    }

    public static double cosineDistance(double[] a, double[] b) {
        return 1.0 - cosineSimilarity(a, b);
    }

    // Compute cosine similarity between two vectors
    public static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 && normB == 0)
            return 1.0; // Both zero vectors
        if (normA == 0 || normB == 0)
            return 0.0; // One zero vector

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Helper for debugging
    private String matrixToString(double[][] matrix) {
        StringBuilder sb = new StringBuilder();
        for (double[] row : matrix) {
            for (double val : row) {
                sb.append(String.format("%.2f ", val));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void logAssignments(List<Integer> ids, int[] labels) {
        Map<Integer, List<Integer>> clusterMap = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            clusterMap.computeIfAbsent(labels[i], k -> new ArrayList<>()).add(ids.get(i));
        }

        clusterMap.forEach((cluster, members) -> logger.info("Cluster {}: {}", cluster, members));
    }
}
