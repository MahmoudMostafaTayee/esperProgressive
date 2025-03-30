package com.espertech.esper.example.IOT.helpers;
import java.util.List;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;

public class SimilarityUtils {
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
        double[][] similarityMatrix = new double[n][n];

        // Compute cosine similarity
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double similarity = cosineSimilarity(features[i], features[j]);
                similarityMatrix[i][j] = similarity;
                similarityMatrix[j][i] = similarity;
            }
        }

        // Apply epsilon threshold
        double threshold = 1 - epsilon;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (similarityMatrix[i][j] < threshold) {
                    similarityMatrix[i][j] = 0;
                }
            }
        }

        // Fill diagonal with 1 (ensuring self-similarity)
        for (int i = 0; i < n; i++) {
            similarityMatrix[i][i] = 1.0;
        }

        // Convert similarity to distance (distance = 1 - similarity)
        double[][] distanceMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distanceMatrix[i][j] = 1.0 - similarityMatrix[i][j];
            }
        }

        return distanceMatrix;
    }

    // Compute cosine similarity between two vectors
    private static double cosineSimilarity(double[] vec1, double[] vec2) {
        RealVector v1 = new ArrayRealVector(vec1);
        RealVector v2 = new ArrayRealVector(vec2);
        double dotProduct = v1.dotProduct(v2);
        double norm1 = v1.getNorm();
        double norm2 = v2.getNorm();
        return dotProduct / (norm1 * norm2);
    }
}
