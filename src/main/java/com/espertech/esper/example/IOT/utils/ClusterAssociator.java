package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.example.IOT.clusterers.CluStreamClusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ClusterAssociator {
    private static final Logger logger = LoggerFactory.getLogger(ClusterAssociator.class);

    public static void associateClustersBetweenPeriods(
            Map<Integer, List<TrackEntry>> trackerById,
            List<Integer> currentClusters,
            List<TrackEntry> currentEntries,
            double epsilon,
            List<Integer> currentSerials) {

        // Collect all past TrackEntrys from trackerById
        List<TrackEntry> pastEntries = trackerById.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Combine past and current entries
        List<TrackEntry> allEntries = new ArrayList<>(pastEntries);
        allEntries.addAll(currentEntries);

        // Create list of all cluster IDs (past and current)
        List<Integer> allClustersList = new ArrayList<>();
        for (TrackEntry entry : pastEntries) {
            allClustersList.add(entry.getUniqueId());
        }
        allClustersList.addAll(currentClusters);

        // Compute similarity matrix
        double[][] similarityMatrix = computeSimilarityMatrix(allEntries, epsilon);

        // Create centrality matrix and associate clusters
        List<Integer> updatedClusters = associateClusters(allEntries, allClustersList, similarityMatrix, epsilon);

        // Update current clusters and trackerById
        for (int i = 0; i < currentEntries.size(); i++) {
            int newClusterId = updatedClusters.get(pastEntries.size() + i);
            TrackEntry entry = currentEntries.get(i);
            entry.setUniqueId(newClusterId);
            trackerById.computeIfAbsent(newClusterId, k -> new ArrayList<>()).add(entry);
            logger.error("New cluster ID: " + newClusterId);
            logger.error("entry" + entry);
            logger.error("currentSerials: " + currentSerials.get(i));
        }
    }

    private static double[][] computeSimilarityMatrix(List<TrackEntry> entries, double epsilon) {
        int n = entries.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            List<Float> featuresI = entries.get(i).getFeatureList();
            for (int j = i; j < n; j++) {
                List<Float> featuresJ = entries.get(j).getFeatureList();
                double similarity = cosineSimilarity(featuresI, featuresJ);
                similarity = similarity >= (1 - epsilon) ? similarity : 0.0;
                matrix[i][j] = similarity;
                matrix[j][i] = similarity;
            }
        }
        return matrix;
    }

    private static double cosineSimilarity(List<Float> a, List<Float> b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i);
            float bi = b.get(i);
            dotProduct += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static List<Integer> associateClusters(
            List<TrackEntry> allEntries,
            List<Integer> allClusters,
            double[][] similarityMatrix,
            double epsilon) {

        // Remove noise clusters (-1)
        Set<Integer> uniqueClustersSet = new HashSet<>(allClusters);
        uniqueClustersSet.remove(-1);
        List<Integer> uniqueClusters = new ArrayList<>(uniqueClustersSet);
        Collections.sort(uniqueClusters);

        // Precompute cluster info
        Map<Integer, Set<Integer>> clusterFrames = new HashMap<>();
        Map<Integer, List<Integer>> clusterIndices = new HashMap<>();
        for (int cluster : uniqueClusters) {
            Set<Integer> frames = new HashSet<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < allClusters.size(); i++) {
                if (allClusters.get(i) == cluster) {
                    frames.add(allEntries.get(i).getFrameNumber());
                    indices.add(i);
                }
            }
            clusterFrames.put(cluster, frames);
            clusterIndices.put(cluster, indices);
        }

        // Initialize centrality matrix
        int numClusters = uniqueClusters.size();
        double[][] centralityMatrix = new double[numClusters][numClusters];
        for (double[] row : centralityMatrix) Arrays.fill(row, -1);
        for (int i = 0; i < numClusters; i++) {
            for (int j = i + 1; j < numClusters; j++) {
                int c1 = uniqueClusters.get(i);
                int c2 = uniqueClusters.get(j);
                if (hasFrameOverlap(clusterFrames.get(c1), clusterFrames.get(c2))) continue;
                centralityMatrix[i][j] = calculateCentrality(
                        clusterIndices.get(c1), clusterIndices.get(c2), similarityMatrix);
                centralityMatrix[j][i] = centralityMatrix[i][j];
            }
        }

        // Perform hierarchical clustering
        boolean changed;
        double threshold = 1 - epsilon;
        do {
            changed = false;
            int[] maxIndices = findMaxCentrality(centralityMatrix, threshold);
            if (maxIndices != null) {
                mergeClusters(maxIndices[0], maxIndices[1], uniqueClusters, allClusters, centralityMatrix);
                changed = true;
            }
        } while (changed);

        return allClusters;
    }

    private static boolean hasFrameOverlap(Set<Integer> frames1, Set<Integer> frames2) {
        for (int frame : frames1) {
            if (frames2.contains(frame)) return true;
        }
        return false;
    }

    private static double calculateCentrality(
            List<Integer> indices1, List<Integer> indices2, double[][] similarityMatrix) {
        double sum = 0.0;
        for (int i : indices1) {
            for (int j : indices2) {
                sum += similarityMatrix[i][j];
            }
        }
        return sum;
    }

    private static int[] findMaxCentrality(double[][] matrix, double threshold) {
        double max = -1;
        int[] indices = null;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = i + 1; j < matrix.length; j++) {
                if (matrix[i][j] > max) {
                    max = matrix[i][j];
                    indices = new int[]{i, j};
                }
            }
        }
        return (max >= threshold) ? indices : null;
    }

    private static void mergeClusters(int i, int j, List<Integer> clusters, List<Integer> allClusters,
                                      double[][] centralityMatrix) {
        int clusterI = clusters.get(i);
        int clusterJ = clusters.get(j);
        int newCluster = Math.min(clusterI, clusterJ);

        // Update all cluster assignments
        for (int k = 0; k < allClusters.size(); k++) {
            if (allClusters.get(k) == clusterJ) {
                allClusters.set(k, newCluster);
            }
        }

        // Update clusters list and centrality matrix
        clusters.remove(j);
        double[][] newMatrix = new double[clusters.size()][clusters.size()];
        for (int x = 0; x < newMatrix.length; x++) {
            for (int y = 0; y < newMatrix.length; y++) {
                newMatrix[x][y] = (x == y) ? 0 :
                        Math.max(centralityMatrix[x][y], centralityMatrix[x >= j ? x + 1 : x][y >= j ? y + 1 : y]);
            }
        }
        System.arraycopy(newMatrix, 0, centralityMatrix, 0, newMatrix.length);
    }
}
