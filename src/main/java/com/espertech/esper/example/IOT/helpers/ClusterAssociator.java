package com.espertech.esper.example.IOT.helpers;
import java.util.*;

public class ClusterAssociator {
    private final float epsilon;

    public ClusterAssociator(float epsilon) {
        this.epsilon = epsilon;
    }

    public Map<Integer, Integer> associateClusters(
            Map<Integer, List<List<Float>>> prevClusters,
            Map<Integer, List<List<Float>>> currentClusters) {

        // Step 1: Calculate cluster centroids
        Map<Integer, List<Float>> prevCentroids = calculateCentroids(prevClusters);
        Map<Integer, List<Float>> currentCentroids = calculateCentroids(currentClusters);

        // Step 2: Compute similarity matrix
        Map<Integer, Map<Integer, Float>> similarityMatrix =
                computeSimilarityMatrix(prevCentroids, currentCentroids);

        // Step 3: Calculate cluster centralities
        Map<Integer, Float> prevCentralities = calculateCentralities(prevClusters);
        Map<Integer, Float> currentCentralities = calculateCentralities(currentClusters);

        // Step 4: Combine similarity with centrality
        Map<Integer, Map<Integer, Float>> associationScores =
                combineWithCentrality(similarityMatrix, prevCentralities, currentCentralities);

        // Step 5: Find optimal matches
        return findOptimalMatches(associationScores);
    }

    private Map<Integer, List<Float>> calculateCentroids(
            Map<Integer, List<List<Float>>> clusters) {

        Map<Integer, List<Float>> centroids = new HashMap<>();
        for (Map.Entry<Integer, List<List<Float>>> entry : clusters.entrySet()) {
            List<Float> centroid = new ArrayList<>();
            int featureSize = entry.getValue().get(0).size();

            // Initialize centroid vector
            for (int i = 0; i < featureSize; i++) {
                centroid.add(0.0f);
            }

            // Sum all features
            for (List<Float> features : entry.getValue()) {
                for (int i = 0; i < featureSize; i++) {
                    centroid.set(i, centroid.get(i) + features.get(i));
                }
            }

            // Average the features
            int count = entry.getValue().size();
            for (int i = 0; i < featureSize; i++) {
                centroid.set(i, centroid.get(i) / count);
            }

            centroids.put(entry.getKey(), centroid);
        }
        return centroids;
    }

    private Map<Integer, Map<Integer, Float>> computeSimilarityMatrix(
            Map<Integer, List<Float>> prevCentroids,
            Map<Integer, List<Float>> currentCentroids) {

        Map<Integer, Map<Integer, Float>> matrix = new HashMap<>();

        for (Integer prevId : prevCentroids.keySet()) {
            Map<Integer, Float> row = new HashMap<>();
            List<Float> prevVector = prevCentroids.get(prevId);

            for (Integer currentId : currentCentroids.keySet()) {
                List<Float> currentVector = currentCentroids.get(currentId);
                float similarity = cosineSimilarity(prevVector, currentVector);
                row.put(currentId, similarity);
            }

            matrix.put(prevId, row);
        }
        return matrix;
    }

    private float cosineSimilarity(List<Float> v1, List<Float> v2) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += v1.get(i) * v1.get(i);
            normB += v2.get(i) * v2.get(i);
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private Map<Integer, Float> calculateCentralities(
            Map<Integer, List<List<Float>>> clusters) {

        Map<Integer, Float> centralities = new HashMap<>();
        for (Map.Entry<Integer, List<List<Float>>> entry : clusters.entrySet()) {
            List<Float> variances = new ArrayList<>();
            int featureSize = entry.getValue().get(0).size();

            // Calculate variance per feature dimension
            for (int d = 0; d < featureSize; d++) {
                final int dim = d;
                double mean = entry.getValue().stream()
                        .mapToDouble(f -> f.get(dim))
                        .average()
                        .orElse(0.0);

                double variance = entry.getValue().stream()
                        .mapToDouble(f -> Math.pow(f.get(dim) - mean, 2))
                        .average()
                        .orElse(0.0);

                variances.add((float) variance);
            }

            // Centrality: inverse of average variance
            float avgVariance = (float) variances.stream()
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            centralities.put(entry.getKey(), 1.0f / (avgVariance + 1e-6f));
        }
        return centralities;
    }

    private Map<Integer, Map<Integer, Float>> combineWithCentrality(
            Map<Integer, Map<Integer, Float>> similarityMatrix,
            Map<Integer, Float> prevCentralities,
            Map<Integer, Float> currentCentralities) {

        Map<Integer, Map<Integer, Float>> scores = new HashMap<>();
        float maxCentrality = Math.max(
                Collections.max(prevCentralities.values()),
                Collections.max(currentCentralities.values())
        );

        for (Integer prevId : similarityMatrix.keySet()) {
            Map<Integer, Float> row = new HashMap<>();
            float prevCentrality = prevCentralities.get(prevId);

            for (Integer currentId : similarityMatrix.get(prevId).keySet()) {
                float currentCentrality = currentCentralities.get(currentId);
                float similarity = similarityMatrix.get(prevId).get(currentId);

                // Adaptive weighting
                float centralityWeight = 0.7f * (prevCentrality + currentCentrality) / (2 * maxCentrality);
                float combinedScore = similarity * (1.0f - centralityWeight) +
                        (similarity * centralityWeight);

                row.put(currentId, combinedScore);
            }
            scores.put(prevId, row);
        }
        return scores;
    }

    private Map<Integer, Integer> findOptimalMatches(
            Map<Integer, Map<Integer, Float>> associationScores) {

        Map<Integer, Integer> matches = new HashMap<>();
        Set<Integer> matchedCurrent = new HashSet<>();

        // Greedy matching algorithm (replace with Hungarian algorithm for optimal)
        List<Map.Entry<Integer, Map<Integer, Float>>> sorted = associationScores.entrySet()
                .stream()
                .sorted((e1, e2) ->
                        Float.compare(
                                e2.getValue().values().stream().max(Float::compare).orElse(0f),
                                e1.getValue().values().stream().max(Float::compare).orElse(0f))
                )
                .toList();

        for (Map.Entry<Integer, Map<Integer, Float>> entry : sorted) {
            Integer prevId = entry.getKey();

            entry.getValue().entrySet().stream()
                    .filter(e -> !matchedCurrent.contains(e.getKey()))
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(bestMatch -> {
                        System.out.println("Trying match for prev " + prevId + " with curr " + bestMatch.getKey() +
                                " score: " + bestMatch.getValue());

                        if (bestMatch.getValue() > (1 - epsilon)) {
                            matches.put(prevId, bestMatch.getKey());
                            matchedCurrent.add(bestMatch.getKey());
                        }
                    });
        }

        return matches;
    }
}