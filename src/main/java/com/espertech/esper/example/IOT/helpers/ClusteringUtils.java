package com.espertech.esper.example.IOT.helpers;

import java.util.*;
import java.util.stream.Collectors;

import com.espertech.esper.example.IOT.clusterers.AgglomerativeClusterer;
import com.espertech.esper.example.IOT.helpers.OverlapDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import static java.lang.System.exit;

/**
 * A utility class for various clustering operations.
 */
public class ClusteringUtils {
    private static final Logger logger = LoggerFactory.getLogger(ClusteringUtils.class);

    /**
     * Performs agglomerative hierarchical clustering on a given distance matrix.
     *
     * @param distanceMatrix The distance matrix representing the distances between data points.
     * @param epsilon The maximum distance to consider for forming clusters.
     * @return A list of cluster labels for each data point.
     */
    public static List<Integer> agglomerativeClustering(double[][] distanceMatrix, double epsilon) {
        // Set diagonal to 0 as in numpy.fill_diagonal
        for (int i = 0; i < distanceMatrix.length; i++) {
            distanceMatrix[i][i] = 0.0;
        }

        // Perform hierarchical clustering with SingleLinkage
        HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));

        // Partition the clusters based on epsilon (distance criterion)
        int[] clusterLabels;
        try {
            System.out.println("distanceMatrix" + Arrays.deepToString(distanceMatrix));
            clusterLabels = hc.partition(epsilon);
        } catch (IllegalArgumentException e) {
            // Fallback: Assign all to one cluster, like SciPy
            clusterLabels = new int[distanceMatrix.length];
            Arrays.fill(clusterLabels, 0);

//            // If epsilon is too large, try a smaller value or handle as a single cluster
//            // For now, let's try a slightly smaller epsilon
//            double adjustedEpsilon = epsilon / 2.0; // Or some other strategy
//            System.err.println("Warning: Epsilon " + epsilon + " was too large for HierarchicalClustering. Adjusting to " + adjustedEpsilon);
//            clusterLabels = hc.partition(adjustedEpsilon);
        }

        // Convert int[] to List<Integer>
        List<Integer> clusters = new ArrayList<>();
        for (int label : clusterLabels) {
            clusters.add(label);
        }
        return clusters;
    }

    /**
     * Determines the initial index for the assignment problem based on minimum distances within overlap groups.
     *
     * @param distanceMatrix The distance matrix between all data points.
     * @param overlapIndicesList A list of lists, where each inner list contains indices of overlapping nodes.
     * @return The index of the overlap group with the maximum minimum distance.
     */
    public static int getInitialIndex(double[][] distanceMatrix, List<List<Integer>> overlapIndicesList) {
        List<Double> distances = new ArrayList<>();
        for (List<Integer> overlapIndices : overlapIndicesList) {
            double minDistance = 2.0; // Distance is between 0 and 1 for cosine distance
            if (overlapIndices.size() > 1) {
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
            } else {
                minDistance = 0.0; // Single element, no distance to compare
            }
            distances.add(minDistance);
        }

        double maxDistance = -1.0;
        int maxIndex = -1;
        for (int i = 0; i < distances.size(); i++) {
            if (distances.get(i) > maxDistance) {
                maxDistance = distances.get(i);
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * Performs bipartite matching between unclustered overlap nodes and clustered overlap nodes.
     *
     * @param newKey A new key for the centrality dictionary.
     * @param centralityDict The dictionary to store centrality results.
     * @param centralityMatrix The centrality matrix representing similarities between overlap nodes and subclusters.
     * @param overlapIndices A list of indices of overlapping nodes.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return The updated centrality dictionary.
     */
    public static Map<Integer, Map<String, Object>> bipartiteMatching(int newKey, Map<Integer, Map<String, Object>> centralityDict, double[][] centralityMatrix, List<Integer> overlapIndices, double epsilon) {
        double th = 1 - epsilon;
        double sumCentrality = 0;
        List<Integer> subclusterIndices = new ArrayList<>(Collections.nCopies(overlapIndices.size(), null));

        // Create a mutable copy of the centralityMatrix
        double[][] currentCentralityMatrix = new double[centralityMatrix.length][centralityMatrix[0].length];
        for (int i = 0; i < centralityMatrix.length; i++) {
            System.arraycopy(centralityMatrix[i], 0, currentCentralityMatrix[i], 0, centralityMatrix[i].length);
        }

        while (true) {
            double maxVal = -1.0;
            int rowIndex = -1;
            int colIndex = -1;

            // Find the maximum value and its indices
            for (int i = 0; i < currentCentralityMatrix.length; i++) {
                for (int j = 0; j < currentCentralityMatrix[i].length; j++) {
                    if (currentCentralityMatrix[i][j] > maxVal) {
                        maxVal = currentCentralityMatrix[i][j];
                        rowIndex = i;
                        colIndex = j;
                    }
                }
            }

            if (maxVal <= th) {
                break;
            }

            sumCentrality += maxVal;
            subclusterIndices.set(rowIndex, colIndex);

            // Set row and column to zero
            for (int j = 0; j < currentCentralityMatrix[0].length; j++) {
                currentCentralityMatrix[rowIndex][j] = 0.0;
            }
            for (int i = 0; i < currentCentralityMatrix.length; i++) {
                currentCentralityMatrix[i][colIndex] = 0.0;
            }
        }

        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("overlap_indices", overlapIndices);
        matchResult.put("indices", subclusterIndices);
        matchResult.put("centrality", sumCentrality);
        centralityDict.put(newKey, matchResult);

        return centralityDict;
    }

    /**
     * Gets candidates for the assignment problem based on similarity.
     *
     * @param similarityMatrix The similarity matrix between all data points.
     * @param subclusterIndicesList A list of lists, where each inner list contains indices of subclusters.
     * @param overlapIndicesList A list of lists, where each inner list contains indices of overlapping nodes.
     * @param epsilon The epsilon value for thresholding similarities.
     * @param numCandidates The maximum number of candidates to return.
     * @return A list of lists, where each inner list contains indices of candidate overlapping nodes.
     */
    public static List<List<Integer>> getCandidatesIndicesList(double[][] similarityMatrix, List<List<Integer>> subclusterIndicesList, List<List<Integer>> overlapIndicesList, double epsilon, int numCandidates) {
        if (overlapIndicesList.size() < numCandidates) {
            return overlapIndicesList;
        } else {
            // Create a copy to avoid modifying the original similarityMatrix
            double[][] currentSimilarityMatrix = new double[similarityMatrix.length][similarityMatrix[0].length];
            for (int i = 0; i < similarityMatrix.length; i++) {
                System.arraycopy(similarityMatrix[i], 0, currentSimilarityMatrix[i], 0, similarityMatrix[i].length);
            }

            // np.fill_diagonal(similarity_matrix, 0)
            for (int i = 0; i < currentSimilarityMatrix.length; i++) {
                currentSimilarityMatrix[i][i] = 0.0;
            }

            // flatten_subcluster_indices = list(chain.from_iterable(subcluster_indices_list))
            List<Integer> flattenSubclusterIndices = new ArrayList<>();
            for (List<Integer> sublist : subclusterIndicesList) {
                flattenSubclusterIndices.addAll(sublist);
            }

            // tmp_similarity_matrix = similarity_matrix[flatten_subcluster_indices]
            double[][] tmpSimilarityMatrix = new double[flattenSubclusterIndices.size()][currentSimilarityMatrix[0].length];
            for (int i = 0; i < flattenSubclusterIndices.size(); i++) {
                tmpSimilarityMatrix[i] = currentSimilarityMatrix[flattenSubclusterIndices.get(i)];
            }

            // max_similarities = np.max(tmp_similarity_matrix,axis=0)
            double[] maxSimilarities = new double[tmpSimilarityMatrix[0].length];
            Arrays.fill(maxSimilarities, -1.0); // Initialize with a very small value
            for (int j = 0; j < tmpSimilarityMatrix[0].length; j++) {
                for (int i = 0; i < tmpSimilarityMatrix.length; i++) {
                    if (tmpSimilarityMatrix[i][j] > maxSimilarities[j]) {
                        maxSimilarities[j] = tmpSimilarityMatrix[i][j];
                    }
                }
            }

            // neighbor_indices = np.where(max_similarities > (1-epsilon))[0]
            List<Integer> neighborIndicesList = new ArrayList<>();
            double threshold = 1 - epsilon;
            for (int i = 0; i < maxSimilarities.length; i++) {
                if (maxSimilarities[i] > threshold) {
                    neighborIndicesList.add(i);
                }
            }

            // sorted_indices = np.argsort(max_similarities[neighbor_indices])[::-1]
            // Sort neighborIndicesList based on maxSimilarities in descending order
            neighborIndicesList.sort((idx1, idx2) -> Double.compare(maxSimilarities[idx2], maxSimilarities[idx1]));

            if (neighborIndicesList.size() > numCandidates) {
                neighborIndicesList = neighborIndicesList.subList(0, numCandidates);
            }

            List<List<Integer>> candidatesIndicesList = new ArrayList<>();
            List<Integer> remainingNeighborIndices = new ArrayList<>(neighborIndicesList);

            for (int neighborIndex : neighborIndicesList) {
                for (List<Integer> overlapIndices : overlapIndicesList) {
                    if (overlapIndices.contains(neighborIndex)) {
                        candidatesIndicesList.add(overlapIndices);
                        // Remove elements from remainingNeighborIndices that are in overlapIndices
                        for (int olIndex : overlapIndices) {
                            remainingNeighborIndices.remove(Integer.valueOf(olIndex));
                        }
                        break; // Move to the next neighborIndex
                    }
                }
            }
            return candidatesIndicesList;
        }
    }

    /**
     * Helper function to fill null values in a list with sequential integers.
     *
     * @param list The list potentially containing null values.
     * @return A new list with null values replaced by sequential integers.
     */
    private static List<Integer> fillNone(List<Integer> list) {
        List<Integer> filledList = new ArrayList<>(list);
        int nextVal = 0;
        for (int i = 0; i < filledList.size(); i++) {
            if (filledList.get(i) == null) {
                filledList.set(i, nextVal++);
            }
        }
        return filledList;
    }

    /**
     * Separates overlapping nodes into subclusters.
     *
     * @param tmpClusters A list of temporary cluster labels.
     * @param overlapIndicesList A list of lists, where each inner list contains indices of overlapping nodes.
     * @param distanceMatrix The distance matrix between all data points.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return An updated list of cluster labels.
     */
    public static List<Integer> separateIntoSubcluster(List<Integer> tmpClusters, List<List<Integer>> overlapIndicesList, double[][] distanceMatrix, double epsilon) {
        int maxOverlap = 0;
        for (List<Integer> indices : overlapIndicesList) {
            if (indices.size() > maxOverlap) {
                maxOverlap = indices.size();
            }
        }

        int initialIndex = getInitialIndex(distanceMatrix, overlapIndicesList);
        List<Integer> initialNodeIndices = new ArrayList<>(overlapIndicesList.get(initialIndex));
        overlapIndicesList.remove(initialIndex);

        List<List<Integer>> subclusterIndicesList = new ArrayList<>();
        for (int i = 0; i < maxOverlap; i++) {
            subclusterIndicesList.add(new ArrayList<>());
        }
        for (int i = 0; i < initialNodeIndices.size(); i++) {
            subclusterIndicesList.get(i).add(initialNodeIndices.get(i));
        }

        double[][] similarityMatrix = new double[distanceMatrix.length][distanceMatrix[0].length];
        for (int i = 0; i < distanceMatrix.length; i++) {
            for (int j = 0; j < distanceMatrix[0].length; j++) {
                similarityMatrix[i][j] = 1.0 - distanceMatrix[i][j];
            }
        }
        for (int i = 0; i < similarityMatrix.length; i++) {
            similarityMatrix[i][i] = 0.0;
        }

        while (!overlapIndicesList.isEmpty()) {
            Map<Integer, Map<String, Object>> centralityDict = new HashMap<>();
            double maxCentrality = 0.0;

            List<List<Integer>> candidatesIndicesList = getCandidatesIndicesList(similarityMatrix, subclusterIndicesList, overlapIndicesList, epsilon, 10);

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
                    if ((Double) entry.getValue().get("centrality") == maxCentrality) {
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

            int limit = Math.min(maxSubclusterIndices.size(), currentOverlapIndices.size());
            for (int i = 0; i < limit; i++) {
                int subIndex = maxSubclusterIndices.get(i);
                int overlapNodeIndex = currentOverlapIndices.get(i);
                subclusterIndicesList.get(subIndex).add(overlapNodeIndex);
            }

            overlapIndicesList.remove(currentOverlapIndices);
        }

        int maxClusterId = -1;
        if (!tmpClusters.isEmpty()) {
            maxClusterId = Collections.max(tmpClusters);
        }

        for (List<Integer> subclusterIndices : subclusterIndicesList) {
            if (subclusterIndices.size() == 1) {
                tmpClusters.set(subclusterIndices.get(0), maxClusterId + 1);
                maxClusterId = Collections.max(tmpClusters);
            } else if (subclusterIndices.size() > 1) {
                double[][] subDistanceMatrix = new double[subclusterIndices.size()][subclusterIndices.size()];
                for (int i = 0; i < subclusterIndices.size(); i++) {
                    for (int j = 0; j < subclusterIndices.size(); j++) {
                        subDistanceMatrix[i][j] = distanceMatrix[subclusterIndices.get(i)][subclusterIndices.get(j)];
                    }
                }
                List<Integer> subClusters = agglomerativeClustering(subDistanceMatrix, epsilon);
                System.out.println("subClusters" + subClusters);
                for (int i = 0; i < subClusters.size(); i++) {
                    tmpClusters.set(subclusterIndices.get(i), subClusters.get(i) + maxClusterId + 1);
                }
                maxClusterId = Collections.max(tmpClusters);
            }
        }
        return tmpClusters;
    }

    /**
     * Translates the similarity matrix between each node into the centrality matrix between each cluster.
     *
     * @param clusters A list of cluster labels for each data point.
     * @param similarityMatrix The similarity matrix between all data points.
     * @param frames A list of frame numbers corresponding to each data point.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return The centrality matrix.
     */
    public static double[][] createCentralityMatrix(List<Integer> clusters, double[][] similarityMatrix, List<Integer> frames, double epsilon) {
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

    /**
     * Performs hierarchical clustering that targets clusters.
     *
     * @param clusters A list of cluster labels for each data point.
     * @param centralityMatrix The centrality matrix between clusters.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return An updated list of cluster labels.
     */
    public static List<Integer> associateCluster(List<Integer> clusters, double[][] centralityMatrix, double epsilon) {
        boolean removeNoiseCluster = true; // Default value from Python kwargs
        int costFunction = 1; // Default value from Python kwargs
        boolean minimize = true; // Default value from Python kwargs

        // Convert List<Integer> to int[] for easier manipulation
        int[] clustersArray = clusters.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> uniqueClustersList = new ArrayList<>();
        for (int cluster : clustersArray) {
            if (!uniqueClustersList.contains(cluster)) {
                uniqueClustersList.add(cluster);
            }
        }
        Collections.sort(uniqueClustersList);

        if (removeNoiseCluster && uniqueClustersList.contains(-1)) {
            uniqueClustersList.remove(Integer.valueOf(-1));
        }

        // Create a mutable copy of the centralityMatrix
        double[][] currentCentralityMatrix = new double[centralityMatrix.length][centralityMatrix[0].length];
        for (int i = 0; i < centralityMatrix.length; i++) {
            System.arraycopy(centralityMatrix[i], 0, currentCentralityMatrix[i], 0, centralityMatrix[i].length);
        }

        // np.fill_diagonal(centrality_matrix, 0)
        for (int i = 0; i < currentCentralityMatrix.length; i++) {
            currentCentralityMatrix[i][i] = 0.0;
        }

        Map<Integer, Integer> count = new HashMap<>();
        if (costFunction == 2) {
            for (int cluster : clustersArray) {
                count.put(cluster, count.getOrDefault(cluster, 0) + 1);
            }
            if (removeNoiseCluster && count.containsKey(-1)) {
                count.remove(-1);
            }
        }

        double centrality = -1.0;
        if (currentCentralityMatrix.length > 0 && currentCentralityMatrix[0].length > 0) {
            centrality = currentCentralityMatrix[0][0]; // Initialize with first element
            for (int i = 0; i < currentCentralityMatrix.length; i++) {
                for (int j = 0; j < currentCentralityMatrix[i].length; j++) {
                    if (currentCentralityMatrix[i][j] > centrality) {
                        centrality = currentCentralityMatrix[i][j];
                    }
                }
            }
        }

        double th = 1.0 - epsilon;

        while (centrality > th) {
            int cluster1Index = -1;
            int cluster2Index = -1;
            double maxVal = -1.0;

            if (costFunction == 1) {
                for (int i = 0; i < currentCentralityMatrix.length; i++) {
                    for (int j = 0; j < currentCentralityMatrix[i].length; j++) {
                        if (currentCentralityMatrix[i][j] > maxVal) {
                            maxVal = currentCentralityMatrix[i][j];
                            cluster1Index = i;
                            cluster2Index = j;
                        }
                    }
                }
            } else if (costFunction == 2) {
                double[][] averagedCentralityMatrix = new double[currentCentralityMatrix.length][currentCentralityMatrix[0].length];
                List<Integer> countsList = new ArrayList<>(count.values());
                for (int i = 0; i < currentCentralityMatrix.length; i++) {
                    for (int j = 0; j < currentCentralityMatrix[i].length; j++) {
                        averagedCentralityMatrix[i][j] = currentCentralityMatrix[i][j] / (double)(countsList.get(i) * countsList.get(j));
                    }
                }
                for (int i = 0; i < averagedCentralityMatrix.length; i++) {
                    averagedCentralityMatrix[i][i] = 0.0;
                }

                for (int i = 0; i < averagedCentralityMatrix.length; i++) {
                    for (int j = 0; j < averagedCentralityMatrix[i].length; j++) {
                        if (averagedCentralityMatrix[i][j] > maxVal) {
                            maxVal = averagedCentralityMatrix[i][j];
                            cluster1Index = i;
                            cluster2Index = j;
                        }
                    }
                }
            }

            centrality = maxVal;

            if (centrality > th) {
                int cluster1 = uniqueClustersList.get(cluster1Index);
                int cluster2 = uniqueClustersList.get(cluster2Index);

                double[] targetRow1 = currentCentralityMatrix[cluster1Index];
                double[] targetRow2 = currentCentralityMatrix[cluster2Index];
                double[] sumRow = new double[targetRow1.length];

                for (int k = 0; k < targetRow1.length; k++) {
                    sumRow[k] = targetRow1[k] + targetRow2[k];
                }

                if (minimize) {
                    for (int k = 0; k < targetRow1.length; k++) {
                        if (Math.min(targetRow1[k], targetRow2[k]) < 0) {
                            sumRow[k] = -1.0; // This is a simplified interpretation of the Python logic
                        }
                    }
                }

                // Update centrality_matrix
                double[][] newCentralityMatrix = new double[currentCentralityMatrix.length - 1][currentCentralityMatrix.length - 1];
                List<Integer> nextIndices = new ArrayList<>();
                for (int i = 0; i < currentCentralityMatrix.length; i++) {
                    if (i != cluster2Index) {
                        nextIndices.add(i);
                    }
                }

                for (int i = 0; i < nextIndices.size(); i++) {
                    for (int j = 0; j < nextIndices.size(); j++) {
                        if (nextIndices.get(i) == cluster1Index) {
                            newCentralityMatrix[i][j] = sumRow[nextIndices.get(j)];
                        } else if (nextIndices.get(j) == cluster1Index) {
                            newCentralityMatrix[i][j] = sumRow[nextIndices.get(i)];
                        } else {
                            newCentralityMatrix[i][j] = currentCentralityMatrix[nextIndices.get(i)][nextIndices.get(j)];
                        }
                    }
                }
                currentCentralityMatrix = newCentralityMatrix;

                for (int i = 0; i < currentCentralityMatrix.length; i++) {
                    currentCentralityMatrix[i][i] = 0.0;
                }

                // Update clusters
                for (int i = 0; i < clustersArray.length; i++) {
                    if (clustersArray[i] == cluster2) {
                        clustersArray[i] = cluster1;
                    }
                }

                // Update unique_clusters
                uniqueClustersList.remove(Integer.valueOf(cluster2));

                if (costFunction == 2) {
                    count.put(cluster1, count.get(cluster1) + count.get(cluster2));
                    count.remove(cluster2);
                }
            } else {
                break;
            }
        }

        return Arrays.stream(clustersArray).boxed().collect(Collectors.toList());
    }

    /**
     * Performs overlap suppression clustering.
     *
     * @param distanceMatrix The distance matrix between all data points.
     * @param frames A list of frame numbers corresponding to each data point.
     * @param nonoverlapIndices A list of indices of non-overlapping nodes.
     * @param overlapIndicesList A list of lists, where each inner list contains indices of overlapping nodes.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return A list of cluster labels after overlap suppression.
     */
    public static List<Integer> overlapSuppressionClustering(double[][] distanceMatrix, List<Integer> frames, List<Integer> nonoverlapIndices, List<List<Integer>> overlapIndicesList, double epsilon) {
        List<Integer> clusters = new ArrayList<>(Collections.nCopies(frames.size(), -1));

        // clustering for non-overlapping nodes
        if (!nonoverlapIndices.isEmpty()) {
            if (nonoverlapIndices.size() > 1) {
                double[][] nonoverlapDistanceMatrix = new double[nonoverlapIndices.size()][nonoverlapIndices.size()];
                for (int i = 0; i < nonoverlapIndices.size(); i++) {
                    for (int j = 0; j < nonoverlapIndices.size(); j++) {
                        nonoverlapDistanceMatrix[i][j] = distanceMatrix[nonoverlapIndices.get(i)][nonoverlapIndices.get(j)];
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
        clusters = associateCluster(clusters, centralityMatrix, epsilon);

        return clusters;
    }

    /**
     * Reclusters overlapping clusters to resolve ambiguities.
     *
     * @param distanceMatrix The distance matrix between all data points.
     * @param trackingDict A map containing tracking information for each serial, including frame numbers.
     * @param serials A list of serial numbers corresponding to the data points.
     * @param clusters A list of initial cluster labels for each data point.
     * @param epsilon The epsilon value for thresholding similarities.
     * @return A list of updated cluster labels after reclustering.
     */
    public static List<Integer> reclusteringOverlapCluster(double[][] distanceMatrix,
//                                                           Map<Integer, Map<String, Object>> trackingDict,
                                                           List<Integer> frames,
                                                           List<Integer> serials,
                                                           List<Integer> clusters,
                                                           double epsilon) {
//        List<Integer> frames = new ArrayList<>();
//        for (int serial : serials) {
//            frames.add((Integer) trackingDict.get(serial).get("Frame"));
//        }

        Map<Integer, List<Integer>> clusterFrameDict = new HashMap<>();
        Map<Integer, List<Integer>> clusterIndicesDict = new HashMap<>();

        Set<Integer> uniqueClusters = new HashSet<>(clusters);
        for (int cluster : uniqueClusters) {
            clusterFrameDict.put(cluster, new ArrayList<>());
            clusterIndicesDict.put(cluster, new ArrayList<>());
        }

        for (int i = 0; i < clusters.size(); i++) {
            int cluster = clusters.get(i);
            clusterFrameDict.get(cluster).add(frames.get(i));
            clusterIndicesDict.get(cluster).add(i);
        }

        List<Integer> newClusters = new ArrayList<>(clusters);

        for (int cluster : uniqueClusters) {
            List<Integer> clusterFrames = clusterFrameDict.get(cluster);
            List<Integer> clusterIndices = clusterIndicesDict.get(cluster);

            Set<Integer> distinctClusterFrames = new HashSet<>(clusterFrames);
            if (distinctClusterFrames.size() == clusterFrames.size()) {
                continue;
            }

            OverlapDetector.OverlapResult overlapResult = OverlapDetector.divideOverlapOrNonOverlap(clusterFrames, clusterIndices);
            List<List<Integer>> overlapIndicesList = overlapResult.overlapIndicesList;
            List<Integer> nonoverlapIndices = overlapResult.nonOverlapIndices;

            List<Integer> tmpClusters = overlapSuppressionClustering(distanceMatrix, frames, nonoverlapIndices, overlapIndicesList, epsilon);

            int maxClusterId = Collections.max(newClusters);
            for (int i = 0; i < tmpClusters.size(); i++) {
                if (newClusters.get(i) == cluster) {
                    newClusters.set(i, maxClusterId + tmpClusters.get(i) + 1);
                }
            }
        }
        return newClusters;
    }

    public static List<Integer> relabelClusters(List<Integer> clusters) {
        // Step 1: Extract unique cluster IDs (preserving insertion order)
        Set<Integer> uniqueClusterSet = new LinkedHashSet<>(clusters);  // Keeps insertion order
        List<Integer> uniqueClusters = new ArrayList<>(uniqueClusterSet);

        // Step 2: Map old cluster ID → new cluster ID (sequential starting from 0)
        Map<Integer, Integer> clusterIdMap = new HashMap<>();
        for (int i = 0; i < uniqueClusters.size(); i++) {
            clusterIdMap.put(uniqueClusters.get(i), i);
        }

        // Step 3: Apply the remapping
        List<Integer> relabeledClusters = new ArrayList<>();
        for (Integer oldCluster : clusters) {
            relabeledClusters.add(clusterIdMap.get(oldCluster));
        }

        return relabeledClusters;
    }


    public static List<Integer> tracking_by_clustering(double[][] distanceMatrix,
//                                                           Map<Integer, Map<String, Object>> trackingDict,
                                                       List<Integer> frames,
                                                       List<Integer> serials,
                                                       List<Integer> clusters,
                                                       double epsilon) {
        if (TrackingParameters.overlap_suppression) {
            logger.info("Overlap suppression");
            clusters = reclusteringOverlapCluster(
                    distanceMatrix,
                    frames,
                    serials,
                    clusters,
                    epsilon
            );
        }

        clusters = relabelClusters(clusters);

        return clusters;
    }
}


