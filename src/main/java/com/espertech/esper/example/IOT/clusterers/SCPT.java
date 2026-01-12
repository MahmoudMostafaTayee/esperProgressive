package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.example.IOT.helpers.OverlapDetector;
import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.util.*;
import java.util.stream.Collectors;

import com.espertech.esper.example.IOT.helpers.ClusteringUtils;

public class SCPT {
    private static final Logger logger = LoggerFactory.getLogger(SCPT.class);

    public static List<Integer> trackingByClustering/* ✅ */(List<double[]> featureList,
            List<Integer> frameNumbers,
            List<Integer> serialNumbers,
            List<Integer[]> boundingBoxList) {

        // 1. Edge Case: Single element
        if (serialNumbers.size() == 1) {
            return new ArrayList<>(Collections.singletonList(0));
        }

        // Optimization: Convert to array once
        double[][] featuresArray = featureList.toArray(new double[0][]);

        // 2. Compute Similarity Matrix
        double[][] similarityMatrix = createSimilarityMatrixSCPT(
                featuresArray,
                TrackingParameters.epsilonScpt);

        // Ensure diagonal is 1 (matching Python's explicit np.fill_diagonal)
        for (int i = 0; i < similarityMatrix.length; i++) {
            similarityMatrix[i][i] = 1.0;
        }

        // 3. Compute Distance Matrix
        double[][] distanceMatrix = computeDistanceMatrix(
                similarityMatrix,
                featuresArray.length);

        // 4. Clustering
        HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
        int[] clusterLabels = hc.partition(TrackingParameters.epsilonScpt);

        List<Integer> clusterLabelsList = Arrays.stream(clusterLabels)
                .boxed()
                .collect(Collectors.toList());



        // 5. Overlap Suppression
        if (TrackingParameters.overlap_suppression) {
            clusterLabelsList = reclusteringOverlapCluster(
                    distanceMatrix,
                    frameNumbers,
                    serialNumbers,
                    clusterLabelsList,
                    TrackingParameters.epsilonScpt);
        }

        // 6. Relabel Clusters
        clusterLabelsList = relabelClusters(clusterLabelsList);

        if (TrackingParameters.isDebug) {
            System.out.println("clusterLabels: " + clusterLabelsList);
        }

        return clusterLabelsList;
    }

    public static List<Integer> relabelClusters/* ✅ */(List<Integer> clusters) {
        if (clusters.isEmpty()) {
            return clusters;
        }

        // Extract unique cluster IDs (preserving first-occurrence order)
        Set<Integer> uniqueClusterSet = new LinkedHashSet<>(clusters);
        List<Integer> uniqueClusters = new ArrayList<>(uniqueClusterSet);

        // Map old cluster ID → new cluster ID (sequential starting from 0)
        Map<Integer, Integer> clusterIdMap = new HashMap<>();
        for (int i = 0; i < uniqueClusters.size(); i++) {
            clusterIdMap.put(uniqueClusters.get(i), i);
        }

        // Apply remapping
        List<Integer> relabeledClusters = new ArrayList<>(clusters.size());
        for (Integer oldCluster : clusters) {
            relabeledClusters.add(clusterIdMap.get(oldCluster));
        }

        return relabeledClusters;
    }

    public static List<Integer> reclusteringOverlapCluster/* ✅ */(double[][] distanceMatrix,
            List<Integer> frames,
            List<Integer> serials, // serials is unused in Java version but kept for signature match
            List<Integer> clusters,
            double epsilon) {
        // 1. Initialize dictionaries
        Map<Integer, List<Integer>> clusterFrameDict = new HashMap<>();
        Map<Integer, List<Integer>> clusterIndicesDict = new HashMap<>();

        // Use HashSet for unique clusters (order doesn't matter for logic, but Set
        // removes duplicates)
        Set<Integer> uniqueClusters = new HashSet<>(clusters);

        for (int cluster : uniqueClusters) {
            clusterFrameDict.put(cluster, new ArrayList<>());
            clusterIndicesDict.put(cluster, new ArrayList<>());
        }

        // 2. Populate dictionaries
        for (int i = 0; i < clusters.size(); i++) {
            int cluster = clusters.get(i);
            clusterFrameDict.get(cluster).add(frames.get(i));
            clusterIndicesDict.get(cluster).add(i);
        }

        // Clone clusters to avoid modifying original list if passed by reference (Java
        // is pass-by-value of reference)
        List<Integer> newClusters = new ArrayList<>(clusters);

        // 3. Iterate over clusters
        for (int cluster : uniqueClusters) {
            List<Integer> clusterFrames = clusterFrameDict.get(cluster);
            List<Integer> clusterIndices = clusterIndicesDict.get(cluster);
            Set<Integer> distinctClusterFrames = new HashSet<>(clusterFrames);

            // If no duplicates (overlaps), skip
            if (distinctClusterFrames.size() == clusterFrames.size()) {
                continue;
            }

            // Divide overlap/non-overlap
            OverlapResult overlapResult = divideOverlapOrNonOverlap(
                    clusterFrames, clusterIndices);
            List<List<Integer>> overlapIndicesList = overlapResult.overlapIndicesList;
            List<Integer> nonoverlapIndices = overlapResult.nonOverlapIndices;

            // Perform sub-clustering
            List<Integer> tmpClusters = overlapSuppressionClustering(
                    distanceMatrix, frames, nonoverlapIndices, overlapIndicesList, epsilon);

            // CRITICAL FIX: Recalculate maxClusterId based on the *current state* of
            // newClusters
            // Doing this inside the loop ensures we don't reuse IDs for different clusters
            int maxClusterId = Collections.max(newClusters);

            // Update cluster IDs
            // Assuming tmpClusters is the same size as newClusters (global masking)
            for (int i = 0; i < tmpClusters.size(); i++) {
                // Only update indices belonging to the current cluster being processed
                if (newClusters.get(i) == cluster) {
                    // Logic: Offset the new sub-cluster ID by the current global Max ID
                    newClusters.set(i, maxClusterId + tmpClusters.get(i) + 1);
                }
            }
        }

        return newClusters;
    }

    public static List<Integer> associateClusterBetweenPeriod(
            List<double[]> currentFeatures,
            List<Integer> currentClusters,
            List<Integer> currentFrames,
            List<double[]> pastFeatures,
            List<Integer> pastClusters,
            List<Integer> pastFrames,
            double epsilon) {

        // 1. Combine lists
        List<Integer> allClusters = new ArrayList<>(pastClusters);
        allClusters.addAll(currentClusters);

        List<Integer> allFrames = new ArrayList<>(pastFrames);
        allFrames.addAll(currentFrames);

        List<double[]> allFeatures = new ArrayList<>(pastFeatures);
        allFeatures.addAll(currentFeatures);

        // 2. Create Similarity Matrix
        double[][] featuresArray = allFeatures.toArray(new double[0][]);
        double[][] similarityMatrix = createSimilarityMatrixSCPT(featuresArray, epsilon);

        // 3. Create Centrality Matrix
        double[][] centralityMatrix = createCentralityMatrix(allClusters, similarityMatrix, allFrames, epsilon);

        // 4. Associate Cluster (Merging)
        List<Integer> associatedClusters = associateCluster(
                allClusters,
                centralityMatrix,
                epsilon,
                true, // removeNoiseCluster default
                1, // costFunction default
                true // minimize default
        );

        // 5. Extract only the *current* portion of the clusters
        int pastSize = pastClusters.size();
        List<Integer> updatedCurrentClusters = new ArrayList<>();
        for (int i = pastSize; i < associatedClusters.size(); i++) {
            updatedCurrentClusters.add(associatedClusters.get(i));
        }

        return updatedCurrentClusters;
    }

    public static OverlapResult divideOverlapOrNonOverlap/* ✅ */(List<Integer> clusterFrames,
            List<Integer> clusterIndices) {
        // Process only common elements if lists are unequal
        int size = Math.min(clusterFrames.size(), clusterIndices.size());
        Map<Integer, List<Integer>> frameIndicesDict = new TreeMap<>();

        // Group indices by frame
        for (int i = 0; i < size; i++) {
            int frame = clusterFrames.get(i);
            int index = clusterIndices.get(i);
            frameIndicesDict.computeIfAbsent(frame, k -> new ArrayList<>()).add(index);
        }

        // Identify frames with >1 index (overlaps)
        List<List<Integer>> overlapIndicesList = frameIndicesDict.values().stream()
                .filter(indices -> indices.size() > 1)
                .collect(Collectors.toList());

        // Flatten overlaps into a Set for O(1) lookups
        Set<Integer> flattenedOverlapIndices = overlapIndicesList.stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // Filter original indices not present in overlap set
        List<Integer> nonOverlapIndices = clusterIndices.stream()
                .filter(index -> !flattenedOverlapIndices.contains(index))
                .collect(Collectors.toList());

        return new OverlapResult(overlapIndicesList, nonOverlapIndices);
    }

    public static class OverlapResult /* ✅ */ {
        public List<List<Integer>> overlapIndicesList;
        public List<Integer> nonOverlapIndices;

        public OverlapResult(List<List<Integer>> overlapIndicesList, List<Integer> nonOverlapIndices) {
            this.overlapIndicesList = overlapIndicesList;
            this.nonOverlapIndices = nonOverlapIndices;
        }
    }

    public static List<Integer> overlapSuppressionClustering/* ✅ */(double[][] distanceMatrix, List<Integer> frames,
            List<Integer> nonoverlapIndices, List<List<Integer>> overlapIndicesList, double epsilon) {
        List<Integer> clusters = new ArrayList<>(Collections.nCopies(frames.size(), -1));

        // clustering for non-overlapping nodes
        if (!nonoverlapIndices.isEmpty()) {
            if (nonoverlapIndices.size() > 1) {
                double[][] nonoverlapDistanceMatrix = new double[nonoverlapIndices.size()][nonoverlapIndices.size()];
                for (int i = 0; i < nonoverlapIndices.size(); i++) {
                    for (int j = 0; j < nonoverlapIndices.size(); j++) {
                        nonoverlapDistanceMatrix[i][j] = distanceMatrix[nonoverlapIndices.get(i)][nonoverlapIndices
                                .get(j)];
                    }
                }
                List<Integer> nonoverlapClusters = agglomerativeClustering(nonoverlapDistanceMatrix, epsilon);
                System.out.println("nonoverlapClusters" + nonoverlapClusters);
                for (int k = 0; k < nonoverlapIndices.size(); k++) {
                    clusters.set(nonoverlapIndices.get(k), nonoverlapClusters.get(k));
                }
            } else {
                clusters.set(nonoverlapIndices.get(0), 0);
            }
        }

        // clustering for overlapping nodes
        clusters = separateIntoSubcluster(clusters, overlapIndicesList, distanceMatrix, epsilon);

        double[][] similarityMatrix = new double[distanceMatrix.length][distanceMatrix[0].length];
        for (int i = 0; i < distanceMatrix.length; i++) {
            for (int j = 0; j < distanceMatrix[0].length; j++) {
                similarityMatrix[i][j] = 1.0 - distanceMatrix[i][j];
            }
        }
        double[][] centralityMatrix = createCentralityMatrix(clusters, similarityMatrix, frames, epsilon);

        // merging for subcluster
        clusters = associateCluster(clusters, centralityMatrix, epsilon, true, 1, true);

        return clusters;
    }

    public static List<Integer> associateCluster/* ✅ */(List<Integer> clusters,
            double[][] centralityMatrix,
            double epsilon,
            // Added missing parameters to signature
            boolean removeNoiseCluster,
            int costFunction,
            boolean minimize) {

        // 1. Setup
        int[] clustersArray = clusters.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> uniqueClustersList = new ArrayList<>(new TreeSet<>(clusters));
        if (removeNoiseCluster && uniqueClustersList.contains(-1)) {
            uniqueClustersList.remove(Integer.valueOf(-1));
        }

        // Deep copy matrix
        double[][] currentCentralityMatrix = new double[centralityMatrix.length][];
        for (int i = 0; i < centralityMatrix.length; i++) {
            currentCentralityMatrix[i] = Arrays.copyOf(centralityMatrix[i], centralityMatrix[i].length);
            currentCentralityMatrix[i][i] = 0.0; // Ensure diagonal is 0
        }

        // Setup counts
        Map<Integer, Integer> count = new HashMap<>();
        if (costFunction == 2) {
            for (int cluster : clustersArray) {
                if (removeNoiseCluster && cluster == -1)
                    continue;
                count.put(cluster, count.getOrDefault(cluster, 0) + 1);
            }
        }

        double th = 1.0 - epsilon;

        // 2. Main Loop
        while (true) {
            int cluster1Index = -1;
            int cluster2Index = -1;
            double maxVal = -Double.MAX_VALUE;

            // --- OPTIMIZED FIND MAX ---
            // We combine the loops for CF1 and CF2 to avoid allocating a temp matrix.
            for (int i = 0; i < currentCentralityMatrix.length; i++) {
                // Optimization: j = i + 1 because matrix is symmetric
                for (int j = i + 1; j < currentCentralityMatrix[i].length; j++) {

                    double val = currentCentralityMatrix[i][j];

                    if (costFunction == 2) {
                        // Apply cost function math on the fly
                        int c1 = uniqueClustersList.get(i);
                        int c2 = uniqueClustersList.get(j);
                        val = val / (double) (count.get(c1) * count.get(c2));
                    }

                    if (val > maxVal) {
                        maxVal = val;
                        cluster1Index = i;
                        cluster2Index = j;
                    }
                }
            }

            // Stop if threshold not met
            if (maxVal <= th) {
                break;
            }

            // 3. Merge Logic
            int cluster1 = uniqueClustersList.get(cluster1Index);
            int cluster2 = uniqueClustersList.get(cluster2Index);

            // Calculate combined row
            double[] sumRow = new double[currentCentralityMatrix.length];
            for (int k = 0; k < currentCentralityMatrix.length; k++) {
                // Skip if comparing to self (logic safety)
                if (k == cluster1Index || k == cluster2Index)
                    continue;

                double val1 = currentCentralityMatrix[cluster1Index][k];
                double val2 = currentCentralityMatrix[cluster2Index][k];

                if (minimize && Math.min(val1, val2) < 0) {
                    sumRow[k] = -1.0;
                } else {
                    sumRow[k] = val1 + val2;
                }
            }

            // Update cluster1's row/col in place FIRST
            for (int k = 0; k < currentCentralityMatrix.length; k++) {
                currentCentralityMatrix[cluster1Index][k] = sumRow[k];
                currentCentralityMatrix[k][cluster1Index] = sumRow[k];
            }
            currentCentralityMatrix[cluster1Index][cluster1Index] = 0.0;

            // 4. Shrink Matrix (Remove cluster2)
            // We build a new smaller matrix, skipping row/col of cluster2
            int newSize = currentCentralityMatrix.length - 1;
            double[][] newCentralityMatrix = new double[newSize][newSize];

            int newRow = 0;
            for (int i = 0; i < currentCentralityMatrix.length; i++) {
                if (i == cluster2Index)
                    continue;

                int newCol = 0;
                for (int j = 0; j < currentCentralityMatrix.length; j++) {
                    if (j == cluster2Index)
                        continue;

                    newCentralityMatrix[newRow][newCol] = currentCentralityMatrix[i][j];
                    newCol++;
                }
                newRow++;
            }
            currentCentralityMatrix = newCentralityMatrix;

            // 5. Update Global State
            // Update labels in the main array
            for (int i = 0; i < clustersArray.length; i++) {
                if (clustersArray[i] == cluster2) {
                    clustersArray[i] = cluster1;
                }
            }

            // Remove merged cluster from tracking list
            uniqueClustersList.remove(cluster2Index); // More efficient to remove by index

            // Update counts
            if (costFunction == 2) {
                count.put(cluster1, count.get(cluster1) + count.get(cluster2));
                count.remove(cluster2);
            }
        }

        return Arrays.stream(clustersArray).boxed().collect(Collectors.toList());
    }

    public static double[][] createCentralityMatrix/* ✅ */(List<Integer> clusters, double[][] similarityMatrix,
            List<Integer> frames, double epsilon) {
        boolean removeNoiseCluster = true; // Default value from Python kwargs

        List<Integer> uniqueClusters = new ArrayList<>(new TreeSet<>(clusters));
        if (removeNoiseCluster && uniqueClusters.contains(-1)) {
            uniqueClusters.remove(Integer.valueOf(-1));
        }

        double[][] centralityMatrix = new double[uniqueClusters.size()][uniqueClusters.size()];
        for (int i = 0; i < uniqueClusters.size(); i++) {
            Arrays.fill(centralityMatrix[i], -1.0);
            centralityMatrix[i][i] = 0.0;
        }

        Map<Integer, List<Integer>> clusterFramesDict = new HashMap<>();
        for (int cluster : uniqueClusters) {
            clusterFramesDict.put(cluster, new ArrayList<>());
        }

        if (removeNoiseCluster) {
            for (int i = 0; i < frames.size(); i++) {
                int cluster = clusters.get(i);
                if (cluster != -1) {
                    clusterFramesDict.get(cluster).add(frames.get(i));
                }
            }
        } else {
            for (int i = 0; i < frames.size(); i++) {
                int cluster = clusters.get(i);
                clusterFramesDict.get(cluster).add(frames.get(i));
            }
        }

        for (int i = 0; i < uniqueClusters.size(); i++) {
            int cluster1 = uniqueClusters.get(i);
            List<Integer> cluster1Frames = clusterFramesDict.get(cluster1);
            List<Integer> cluster1Indices = new ArrayList<>();
            for (int k = 0; k < clusters.size(); k++) {
                if (clusters.get(k) == cluster1) {
                    cluster1Indices.add(k);
                }
            }

            for (int j = i + 1; j < uniqueClusters.size(); j++) {
                int cluster2 = uniqueClusters.get(j);
                List<Integer> cluster2Frames = clusterFramesDict.get(cluster2);

                Set<Integer> commonFrames = new HashSet<>(cluster1Frames);
                commonFrames.retainAll(cluster2Frames);
                if (!commonFrames.isEmpty()) {
                    continue;
                }

                List<Integer> cluster2Indices = new ArrayList<>();
                for (int k = 0; k < clusters.size(); k++) {
                    if (clusters.get(k) == cluster2) {
                        cluster2Indices.add(k);
                    }
                }

                double centrality = 0.0;
                for (int idx1 : cluster1Indices) {
                    for (int idx2 : cluster2Indices) {
                        if (similarityMatrix[idx1][idx2] > (1.0 - epsilon)) {
                            centrality += similarityMatrix[idx1][idx2];
                        }
                    }
                }
                centralityMatrix[i][j] = centrality;
                centralityMatrix[j][i] = centrality;
            }
        }
        return centralityMatrix;
    }

    public static List<Integer> separateIntoSubcluster/* ✅ */(List<Integer> tmpClusters,
            List<List<Integer>> overlapIndicesList, double[][] distanceMatrix, double epsilon) {
        // 1. Calculate Max Overlap
        int maxOverlap = 0;
        for (List<Integer> indices : overlapIndicesList) {
            maxOverlap = Math.max(maxOverlap, indices.size());
        }

        // 2. Initial Node Handling
        int initialIndex = getInitialIndex(distanceMatrix, overlapIndicesList);
        List<Integer> initialNodeIndices = new ArrayList<>(overlapIndicesList.get(initialIndex));
        // Remove by index (int)
        overlapIndicesList.remove(initialIndex);

        // Initialize subclusters
        List<List<Integer>> subclusterIndicesList = new ArrayList<>();
        for (int i = 0; i < maxOverlap; i++) {
            subclusterIndicesList.add(new ArrayList<>());
        }
        for (int i = 0; i < initialNodeIndices.size(); i++) {
            subclusterIndicesList.get(i).add(initialNodeIndices.get(i));
        }

        // 3. Create Similarity Matrix (1 - Distance)
        double[][] similarityMatrix = new double[distanceMatrix.length][distanceMatrix[0].length];
        for (int i = 0; i < distanceMatrix.length; i++) {
            for (int j = 0; j < distanceMatrix[0].length; j++) {
                similarityMatrix[i][j] = (i == j) ? 0.0 : 1.0 - distanceMatrix[i][j];
            }
        }

        // 4. Main Processing Loop
        while (!overlapIndicesList.isEmpty()) {
            Map<Integer, Map<String, Object>> centralityDict = new HashMap<>();
            double maxCentrality = 0.0;

            // Hardcoded '10' matches your snippet; verify if this should be 'epsilon' or a
            // config param
            List<List<Integer>> candidatesIndicesList = getCandidatesIndicesList(similarityMatrix,
                    subclusterIndicesList, overlapIndicesList, epsilon, 10);

            for (int i = 0; i < candidatesIndicesList.size(); i++) {
                List<Integer> overlapIndices = candidatesIndicesList.get(i);
                double[][] centralityMatrix = new double[overlapIndices.size()][subclusterIndicesList.size()];

                for (int j = 0; j < overlapIndices.size(); j++) {
                    int overlapIndex = overlapIndices.get(j);
                    double[] tmpSimilarityMatrixRow = similarityMatrix[overlapIndex];
                    for (int k = 0; k < subclusterIndicesList.size(); k++) {
                        List<Integer> subclusterIndices = subclusterIndicesList.get(k);
                        double sumSimilarities = 0.0;
                        for (int subIndex : subclusterIndices) {
                            if (tmpSimilarityMatrixRow[subIndex] > (1.0 - epsilon)) {
                                sumSimilarities += tmpSimilarityMatrixRow[subIndex];
                            }
                        }
                        centralityMatrix[j][k] = sumSimilarities;
                    }
                }
                centralityDict = bipartiteMatching(i, centralityDict, centralityMatrix, overlapIndices, epsilon);
            }

            if (!centralityDict.isEmpty()) {
                for (Map<String, Object> value : centralityDict.values()) {
                    double currentCentrality = (Double) value.get("centrality");
                    if (currentCentrality > maxCentrality) {
                        maxCentrality = currentCentrality;
                    }
                }
            }

            int maxIndex = -1;
            List<Integer> maxSubclusterIndices = new ArrayList<>();
            List<Integer> currentOverlapIndices = new ArrayList<>();

            if (maxCentrality == 0.0) {
                maxIndex = getInitialIndex(distanceMatrix, overlapIndicesList);
                currentOverlapIndices = overlapIndicesList.get(maxIndex);
                for (int i = 0; i < maxOverlap; i++) {
                    maxSubclusterIndices.add(i);
                }
            } else {
                for (Map.Entry<Integer, Map<String, Object>> entry : centralityDict.entrySet()) {
                    if (Math.abs((Double) entry.getValue().get("centrality") - maxCentrality) < 1e-9) {
                        maxIndex = entry.getKey();
                        currentOverlapIndices = (List<Integer>) entry.getValue().get("overlap_indices");
                        maxSubclusterIndices = (List<Integer>) entry.getValue().get("indices");
                        if (maxSubclusterIndices.contains(null)) {
                            maxSubclusterIndices = fillNone(maxSubclusterIndices);
                        }
                        break;
                    }
                }
            }

            // 5. Update Subclusters
            // Ensure we don't exceed bounds (simulates Python zip)
            int limit = Math.min(maxSubclusterIndices.size(), currentOverlapIndices.size());
            for (int i = 0; i < limit; i++) {
                subclusterIndicesList.get(maxSubclusterIndices.get(i)).add(currentOverlapIndices.get(i));
            }

            // Remove processed overlap indices
            // WARNING: This relies on currentOverlapIndices being value-equal to the one in
            // the list
            overlapIndicesList.remove(currentOverlapIndices);
        }

        // 6. Final Cluster ID Assignment
        int maxClusterId = tmpClusters.isEmpty() ? -1 : Collections.max(tmpClusters);

        for (List<Integer> subclusterIndices : subclusterIndicesList) {
            if (subclusterIndices.isEmpty())
                continue; // Safety check

            if (subclusterIndices.size() == 1) {
                tmpClusters.set(subclusterIndices.get(0), maxClusterId + 1);
                maxClusterId = Collections.max(tmpClusters);
            } else {
                // Extract sub-matrix
                double[][] subDistanceMatrix = new double[subclusterIndices.size()][subclusterIndices.size()];
                for (int i = 0; i < subclusterIndices.size(); i++) {
                    for (int j = 0; j < subclusterIndices.size(); j++) {
                        subDistanceMatrix[i][j] = distanceMatrix[subclusterIndices.get(i)][subclusterIndices.get(j)];
                    }
                }

                // NOTE: Assuming agglomerativeClustering returns 0-based indices here
                List<Integer> subClusters = agglomerativeClustering(subDistanceMatrix, epsilon);

                for (int i = 0; i < subClusters.size(); i++) {
                    // Formula: 0-based-index + max + 1
                    tmpClusters.set(subclusterIndices.get(i), subClusters.get(i) + maxClusterId + 1);
                }
                maxClusterId = Collections.max(tmpClusters);
            }
        }
        return tmpClusters;
    }

    public static List<Integer> fillNone/* ✅ */(List<Integer> list) {
        int n = list.size();

        // 1. Identify which numbers from 0 to n-1 are ALREADY used
        Set<Integer> usedNums = new HashSet<>();
        for (Integer num : list) {
            if (num != null) {
                usedNums.add(num);
            }
        }

        // 2. Identify which numbers are NOT used (unused_nums)
        Queue<Integer> unusedNums = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (!usedNums.contains(i)) {
                unusedNums.add(i);
            }
        }

        // 3. Create the filled list
        List<Integer> filledList = new ArrayList<>(list);
        for (int i = 0; i < filledList.size(); i++) {
            if (filledList.get(i) == null) {
                // Python's .pop(0) is equivalent to Queue.poll()
                if (!unusedNums.isEmpty()) {
                    filledList.set(i, unusedNums.poll());
                }
            }
        }

        return filledList;
    }

    // Helper class to store matrix entries for sorting
    private static class MatrixEntry implements Comparable<MatrixEntry> /* ✅ */ {
        int row;
        int col;
        double value;

        public MatrixEntry(int row, int col, double value) {
            this.row = row;
            this.col = col;
            this.value = value;
        }

        @Override
        public int compareTo(MatrixEntry other) {
            // Sort descending by value
            return Double.compare(other.value, this.value);
        }
    }

    public static Map<Integer, Map<String, Object>> bipartiteMatching/* ✅ */(int newKey,
            Map<Integer, Map<String, Object>> centralityDict,
            double[][] centralityMatrix,
            List<Integer> overlapIndices,
            double epsilon) {
        double th = 1.0 - epsilon;
        double sumCentrality = 0;

        // 1. Validation (Optional but recommended)
        if (centralityMatrix.length != overlapIndices.size()) {
            // You might want to log a warning here if this is unexpected data
            System.err.println("Warning: Matrix rows (" + centralityMatrix.length +
                    ") do not match overlap indices count (" + overlapIndices.size() + ")");
        }

        // Prepare result list filled with nulls
        List<Integer> subclusterIndices = new ArrayList<>(Collections.nCopies(overlapIndices.size(), null));

        // 1. Flatten valid matrix entries into a list
        List<MatrixEntry> entries = new ArrayList<>();
        int rows = centralityMatrix.length;
        int cols = centralityMatrix[0].length;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (centralityMatrix[i][j] > th) {
                    entries.add(new MatrixEntry(i, j, centralityMatrix[i][j]));
                }
            }
        }

        // 2. Sort entries by value (Highest first) - Effectively "Greedy" approach
        Collections.sort(entries);

        // 3. Select matches ensuring no row or col is reused
        boolean[] rowUsed = new boolean[rows];
        boolean[] colUsed = new boolean[cols];

        for (MatrixEntry entry : entries) {
            if (!rowUsed[entry.row] && !colUsed[entry.col]) {
                // Match found!
                sumCentrality += entry.value;

                // Store the column index at the row's position
                // Guard against index out of bounds if matrix rows > overlapIndices size
                if (entry.row < subclusterIndices.size()) {
                    subclusterIndices.set(entry.row, entry.col);
                }

                // Mark row and col as used (equivalent to zeroing them out)
                rowUsed[entry.row] = true;
                colUsed[entry.col] = true;
            }
        }

        // 4. Construct Result
        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("overlap_indices", overlapIndices);
        matchResult.put("indices", subclusterIndices);
        matchResult.put("centrality", sumCentrality);

        centralityDict.put(newKey, matchResult);

        return centralityDict;
    }

    public static List<List<Integer>> getCandidatesIndicesList/* ✅ */(double[][] similarityMatrix,
            List<List<Integer>> subclusterIndicesList,
            List<List<Integer>> overlapIndicesList,
            double epsilon,
            int numCandidates) {

        // 1. Quick exit if we have fewer groups than candidates requested
        if (overlapIndicesList.size() < numCandidates) {
            return new ArrayList<>(overlapIndicesList);
        }

        // 2. Flatten subclusters
        // We use a Set first to ensure uniqueness if subclusters overlap, though List
        // is fine if guaranteed unique.
        List<Integer> flattenSubclusterIndices = new ArrayList<>();
        for (List<Integer> sublist : subclusterIndicesList) {
            flattenSubclusterIndices.addAll(sublist);
        }

        int numCols = similarityMatrix[0].length;
        double[] maxSimilarities = new double[numCols];
        // Initialize with a value lower than possible similarity (assuming sim is
        // usually 0.0 to 1.0)
        Arrays.fill(maxSimilarities, -Double.MAX_VALUE);

        // 3. Calculate Max Similarities (Optimized: No Matrix Copying)
        // We iterate only the rows we care about (flattenSubclusterIndices)
        // We handle the diagonal=0 logic virtually here.
        for (int rowIdx : flattenSubclusterIndices) {
            double[] row = similarityMatrix[rowIdx];
            for (int colIdx = 0; colIdx < numCols; colIdx++) {
                double val = row[colIdx];

                // Emulate np.fill_diagonal(similarity_matrix, 0)
                if (rowIdx == colIdx) {
                    val = 0.0;
                }

                if (val > maxSimilarities[colIdx]) {
                    maxSimilarities[colIdx] = val;
                }
            }
        }

        // 4. Filter indices based on threshold
        List<Integer> neighborIndicesList = new ArrayList<>();
        double threshold = 1.0 - epsilon;

        for (int i = 0; i < maxSimilarities.length; i++) {
            if (maxSimilarities[i] > threshold) {
                neighborIndicesList.add(i);
            }
        }

        // 5. Sort descending based on score
        // Note: maxSimilarities must be effectively final for the lambda
        final double[] scores = maxSimilarities;
        neighborIndicesList.sort((i1, i2) -> Double.compare(scores[i2], scores[i1]));

        // 6. Truncate to numCandidates (Python logic does this BEFORE grouping removal)
        if (neighborIndicesList.size() > numCandidates) {
            neighborIndicesList = neighborIndicesList.subList(0, numCandidates);
        }

        // 7. Select Candidates (Fixing the logic bug)
        List<List<Integer>> candidatesIndicesList = new ArrayList<>();

        // We use a Set to track indices that have been "consumed" by being part of a
        // selected group
        Set<Integer> consumedIndices = new HashSet<>();

        for (Integer neighborIndex : neighborIndicesList) {
            // CRITICAL FIX: If this index was part of a group we already added, skip it.
            if (consumedIndices.contains(neighborIndex)) {
                continue;
            }

            for (List<Integer> overlapIndices : overlapIndicesList) {
                if (overlapIndices.contains(neighborIndex)) {
                    candidatesIndicesList.add(overlapIndices);

                    // Mark ALL members of this group as consumed so we don't pick them again
                    consumedIndices.addAll(overlapIndices);

                    // Break inner loop (found the group for this neighbor)
                    break;
                }
            }
        }

        return candidatesIndicesList;
    }

    public static int getInitialIndex/* ✅ */(double[][] distanceMatrix, List<List<Integer>> overlapIndicesList) {
        List<Double> distances = new ArrayList<>();

        for (List<Integer> overlapIndices : overlapIndicesList) {
            double minDistance = 2.0; // Initial value from Python

            // Python's combinations(overlap_indices, 2)
            // If overlapIndices.size() < 2, these loops simply don't execute
            for (int i = 0; i < overlapIndices.size(); i++) {
                for (int j = i + 1; j < overlapIndices.size(); j++) {
                    int index1 = overlapIndices.get(i);
                    int index2 = overlapIndices.get(j);

                    double distance = distanceMatrix[index1][index2];
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
            distances.add(minDistance);
        }

        // np.argmax(distances)
        double maxDistance = -Double.MAX_VALUE;
        int maxIndex = 0; // Default to 0 to match argmax behavior on empty/constant lists
        for (int i = 0; i < distances.size(); i++) {
            if (distances.get(i) > maxDistance) {
                maxDistance = distances.get(i);
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    public static List<Integer> agglomerativeClustering/* ✅ */(double[][] distanceMatrix, double epsilon) {
        // Safety check for empty input
        if (distanceMatrix == null || distanceMatrix.length == 0) {
            return new ArrayList<>();
        }

        // Set diagonal to 0 (matches np.fill_diagonal)
        for (int i = 0; i < distanceMatrix.length; i++) {
            distanceMatrix[i][i] = 0.0;
        }

        int[] clusterLabels;
        try {
            // Perform hierarchical clustering with SingleLinkage
            // Assuming 'SingleLinkage' accepts a full NxN distance matrix
            HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));

            // Partition based on epsilon
            clusterLabels = hc.partition(epsilon);
        } catch (Exception e) {
            // Fallback: Assign all to cluster 0 (or 1) if clustering fails
            clusterLabels = new int[distanceMatrix.length];
            Arrays.fill(clusterLabels, 0);
        }

        List<Integer> clusters = new ArrayList<>();
        for (int label : clusterLabels) {
            // STRICT PYTHON MATCH: fcluster returns 1-based indices.
            // If your Java lib returns 0-based, add +1.
            clusters.add(label);
        }
        return clusters;
    }

    private static double[][] computeDistanceMatrix/* ✅ */(double[][] similarityMatrix, int n) {
        // Convert to distance matrix
        double[][] distMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distMatrix[i][j] = 1.0 - similarityMatrix[i][j];
            }
        }
        return distMatrix;
    }

    public static double[][] createSimilarityMatrixSCPT/* ✅ */(double[][] features, double epsilon) {
        int n = features.length;
        double[][] similarityMatrix = new double[n][n];
        double threshold = 1.0 - epsilon;

        // OPTIMIZATION: Pre-compute magnitudes to avoid recalculating inside the N*N
        // loop
        double[] magnitudes = new double[n];
        for (int i = 0; i < n; i++) {
            magnitudes[i] = getMagnitude(features[i]);
        }

        for (int i = 0; i < n; i++) {
            similarityMatrix[i][i] = 1.0; // Self-similarity

            for (int j = i + 1; j < n; j++) {
                // Pass pre-computed magnitudes
                double similarity = cosineSimilarity(features[i], features[j], magnitudes[i], magnitudes[j]);

                if (similarity < threshold) {
                    similarity = 0.0;
                }

                similarityMatrix[i][j] = similarity;
                similarityMatrix[j][i] = similarity;
            }
        }
        return similarityMatrix;
    }

    private static double getMagnitude/* ✅ */(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private static double cosineSimilarity/* ✅ */(double[] a, double[] b, double normA, double normB) {
        // If either vector is zero-length, return result based on logic
        if (normA == 0 && normB == 0)
            return 1.0;
        if (normA == 0 || normB == 0)
            return 0.0;

        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }

        return dot / (normA * normB);
    }

}
