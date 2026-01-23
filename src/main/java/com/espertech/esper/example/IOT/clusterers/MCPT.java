package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Multi-Camera People Tracking (MCPT) implementation.
 * Ported from Python mcpt.py for real-time streaming with Esper CEP.
 */
public class MCPT {
    private static final Logger logger = LoggerFactory.getLogger(MCPT.class);

    /**
     * Helper class to store representative node information
     * For streaming architecture - features are stored directly in memory
     */
    public static class RepresentativeNode {
        public String serial;
        public double[] feature; // Feature vector stored directly (not file path)
        public double score; // For keypoint-based selection
        public List<String> allSerials;

        public RepresentativeNode(String serial, double[] feature, double score, List<String> allSerials) {
            this.serial = serial;
            this.feature = feature;
            this.score = score;
            this.allSerials = allSerials;
        }
    }

    /**
     * Helper class to store camera dictionary information
     */
    public static class CameraDict {
        public List<Integer> indices;
        public List<Integer> uniqueLocalIds;

        public CameraDict() {
            this.indices = new ArrayList<>();
            this.uniqueLocalIds = new ArrayList<>();
        }
    }

    /**
     * Get maximum value of a specific key from nested map structure
     * Python equivalent: get_max_value_of_dict
     */
    public static int getMaxValueOfDict(Map<String, Map<String, Object>> dictionary, String key) {
        int maxValue = Integer.MIN_VALUE;
        for (Map.Entry<String, Map<String, Object>> entry : dictionary.entrySet()) {
            Map<String, Object> value = entry.getValue();
            if (value.containsKey(key)) {
                Object objValue = value.get(key);
                if (objValue instanceof Integer) {
                    maxValue = Math.max(maxValue, (Integer) objValue);
                }
            }
        }
        return maxValue;
    }

    /**
     * Create similarity matrix from representative features for MCPT
     * Python equivalent: create_similarity_matrix_mcpt
     * 
     * @param representativeNodes Map of camera -> (local_id -> RepresentativeNode)
     *                            containing features
     * @param shortTrackTh        Minimum track length threshold
     * @param keypointConditionTh Keypoint quality threshold
     * @return Similarity matrix for MCPT clustering
     */
    public static double[][] createSimilarityMatrixMCPT(
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            int shortTrackTh,
            int keypointConditionTh) {

        List<double[]> featureList = new ArrayList<>();

        // Collect features from representative nodes (features are already in memory)
        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Map<Integer, RepresentativeNode> tmpRepresentativeNodes = cameraEntry.getValue();

            for (Map.Entry<Integer, RepresentativeNode> localEntry : tmpRepresentativeNodes.entrySet()) {
                RepresentativeNode value = localEntry.getValue();
                List<String> serials = value.allSerials;

                // Filter by track length
                if (serials.size() < shortTrackTh) {
                    continue;
                }

                // Filter by keypoint score condition
                if (value.score > keypointConditionTh) {
                    continue;
                }

                // Feature is already loaded in memory from streaming
                if (value.feature != null) {
                    featureList.add(value.feature);
                }
            }
        }

        // Convert to array
        if (featureList.isEmpty()) {
            return new double[0][0];
        }

        double[][] featuresArray = featureList.toArray(new double[0][]);
        return SCPT.createSimilarityMatrixSCPT(featuresArray, TrackingParameters.epsilonMcpt);
    }

    /**
     * Measure intersection area between two bounding boxes
     * Python equivalent: measure_intersect_area
     */
    public static double measureIntersectArea(double[] rectangle1, double[] rectangle2) {
        // rectangle format: [x1, y1, x2, y2]
        double intersectWidth = Math.min(rectangle1[2], rectangle2[2]) - Math.max(rectangle1[0], rectangle2[0]);
        double intersectHeight = Math.min(rectangle1[3], rectangle2[3]) - Math.max(rectangle1[1], rectangle2[1]);
        return Math.max(intersectWidth, 0) * Math.max(intersectHeight, 0);
    }

    /**
     * Find highest centrality node from tracklet using features already in memory
     * Python equivalent: find_highest_centrality_node
     */
    @SuppressWarnings("unchecked")
    public static FindCentalityResult findHighestCentralityNode(
            Map<String, Map<String, Object>> trackingDict,
            List<String> serials,
            double epsilon,
            int stackMaxSize,
            int[] imageSize,
            double aspectTh) {

        List<double[]> posList = new ArrayList<>();
        for (String serial : serials) {
            Map<String, Object> entry = trackingDict.get(serial);
            Map<String, Integer> coordinate = (Map<String, Integer>) entry.get("Coordinate");
            double[] pos = new double[] {
                    coordinate.get("x1"),
                    coordinate.get("y1"),
                    coordinate.get("x2"),
                    coordinate.get("y2")
            };
            posList.add(pos);
        }

        // Filter by aspect ratio only (removed edge distance calculation as it was
        // unused)
        List<String> newSerials = new ArrayList<>();
        for (int i = 0; i < serials.size(); i++) {
            double[] pos = posList.get(i);
            double aspect = (pos[3] - pos[1]) / (pos[2] - pos[0]);

            if (aspect >= aspectTh) {
                newSerials.add(serials.get(i));
            }
        }

        if (newSerials.isEmpty()) {
            return new FindCentalityResult(newSerials, null, null);
        }

        if (newSerials.size() <= 2) {
            String serial = newSerials.get(0);
            // Feature is already in memory from streaming (DetectedUser)
            double[] feature = (double[]) trackingDict.get(serial).get("Feature");
            return new FindCentalityResult(newSerials, serial, feature);
        }

        // For larger sets, compute centrality based on in-memory features
        int freq = 1;
        while (newSerials.size() / freq > stackMaxSize) {
            freq++;
        }

        // Build feature stack from in-memory features (not from files!)
        List<double[]> featureStack = new ArrayList<>();
        List<String> sampledSerials = new ArrayList<>();

        for (int n = 0; n < newSerials.size(); n++) {
            if (n % freq != 0)
                continue;
            String serial = newSerials.get(n);
            double[] feature = (double[]) trackingDict.get(serial).get("Feature");
            if (feature != null) {
                featureStack.add(feature);
                sampledSerials.add(serial);
            }
        }

        if (featureStack.isEmpty()) {
            return new FindCentalityResult(newSerials, newSerials.get(0), null);
        }

        // Compute similarity matrix and find highest centrality
        double[][] featuresArray = featureStack.toArray(new double[0][]);
        double[][] similarityMatrix = SCPT.createSimilarityMatrixSCPT(featuresArray, epsilon);

        // Zero out values below threshold
        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                if (similarityMatrix[i][j] < 1.0 - epsilon) {
                    similarityMatrix[i][j] = 0.0;
                }
            }
        }

        // Calculate centralities (sum of similarities)
        double[] centralities = new double[similarityMatrix.length];
        for (int i = 0; i < similarityMatrix.length; i++) {
            centralities[i] = Arrays.stream(similarityMatrix[i]).sum();
        }

        // Find index of max centrality
        int idxMax = 0;
        double maxCentrality = centralities[0];
        for (int i = 1; i < centralities.length; i++) {
            if (centralities[i] > maxCentrality) {
                maxCentrality = centralities[i];
                idxMax = i;
            }
        }

        String representativeSerial = sampledSerials.get(idxMax);
        double[] representativeFeature = featureStack.get(idxMax);

        return new FindCentalityResult(newSerials, representativeSerial, representativeFeature);
    }

    public static class FindCentalityResult {
        public List<String> newSerials;
        public String serial;
        public double[] feature;

        public FindCentalityResult(List<String> newSerials, String serial, double[] feature) {
            this.newSerials = newSerials;
            this.serial = serial;
            this.feature = feature;
        }
    }

    /**
     * Create camera dictionary mapping cameras to their tracklet indices
     * Python equivalent: create_camera_dict
     */
    public static Map<Integer, CameraDict> createCameraDict(
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            int shortTrackTh,
            int keypointConditionTh) {

        Map<Integer, CameraDict> cameraDict = new HashMap<>();

        for (Integer cameraId : representativeNodes.keySet()) {
            cameraDict.put(cameraId, new CameraDict());
        }

        int maxId = 0;

        // Sort camera IDs for consistent ordering
        List<Integer> sortedCameraIds = new ArrayList<>(representativeNodes.keySet());
        Collections.sort(sortedCameraIds);

        for (Integer cameraId : sortedCameraIds) {
            Map<Integer, RepresentativeNode> tmpRepresentativeNodes = representativeNodes.get(cameraId);
            List<Integer> localIds = new ArrayList<>();

            for (Map.Entry<Integer, RepresentativeNode> entry : tmpRepresentativeNodes.entrySet()) {
                Integer localId = entry.getKey();
                RepresentativeNode node = entry.getValue();
                List<String> serials = node.allSerials;

                if (serials.size() < shortTrackTh) {
                    continue;
                }

                if (node.score > keypointConditionTh) {
                    continue;
                }

                localIds.add(localId);
            }

            Collections.sort(localIds);
            List<Integer> uniqueLocalIds = new ArrayList<>(new LinkedHashSet<>(localIds));

            CameraDict dict = cameraDict.get(cameraId);
            for (int i = 0; i < uniqueLocalIds.size(); i++) {
                dict.indices.add(maxId + i);
            }
            dict.uniqueLocalIds.addAll(uniqueLocalIds);
            maxId += uniqueLocalIds.size();
        }

        return cameraDict;
    }

    /**
     * Measure Euclidean distance between two sets of positions
     * Python equivalent: measure_euclidean_distance
     */
    public static double[] measureEuclideanDistance(List<double[]> id1PosList, List<double[]> id2PosList) {
        if (id1PosList.size() != id2PosList.size()) {
            throw new IllegalArgumentException("Position lists must have the same size");
        }

        double[] distances = new double[id1PosList.size()];
        for (int i = 0; i < id1PosList.size(); i++) {
            double[] pos1 = id1PosList.get(i);
            double[] pos2 = id2PosList.get(i);

            double sumSquares = 0;
            for (int j = 0; j < pos1.length; j++) {
                double diff = pos1[j] - pos2[j];
                sumSquares += diff * diff;
            }
            distances[i] = Math.sqrt(sumSquares);
        }

        return distances;
    }

    /**
     * Create Euclidean distance matrix between tracklets based on world coordinates
     * Python equivalent: create_distance_matrix
     */
    @SuppressWarnings("unchecked")
    public static DistanceMatrices createDistanceMatrix(
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            String distanceType,
            int shortTrackTh,
            int keypointConditionTh) {

        Map<Integer, CameraDict> cameraDict = createCameraDict(
                representativeNodes, shortTrackTh, keypointConditionTh);

        int shape = cameraDict.values().stream()
                .mapToInt(dict -> dict.indices.size())
                .sum();

        double[][] minDistanceMatrix = new double[shape][shape];
        double[][] maxDistanceMatrix = new double[shape][shape];
        double[][] meanDistanceMatrix = new double[shape][shape];

        // Initialize with infinity
        for (int i = 0; i < shape; i++) {
            Arrays.fill(minDistanceMatrix[i], Double.POSITIVE_INFINITY);
            Arrays.fill(maxDistanceMatrix[i], Double.POSITIVE_INFINITY);
            Arrays.fill(meanDistanceMatrix[i], Double.POSITIVE_INFINITY);
        }

        // Build index mappings for world coordinates
        Map<Integer, List<String>> indexSerialsDict = new HashMap<>();
        Map<Integer, List<Integer>> indexFramesDict = new HashMap<>();
        Map<Integer, List<double[]>> indexWposListDict = new HashMap<>();

        for (int i = 0; i < shape; i++) {
            indexSerialsDict.put(i, new ArrayList<>());
            indexFramesDict.put(i, new ArrayList<>());
            indexWposListDict.put(i, new ArrayList<>());
        }

        // Populate mappings
        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);
            CameraDict camDict = cameraDict.get(cameraId);
            List<Integer> indices = camDict.indices;
            List<Integer> uniqueLocalIds = camDict.uniqueLocalIds;

            Map<Integer, List<String>> localIdsSerialsDict = new HashMap<>();
            for (Integer localId : uniqueLocalIds) {
                localIdsSerialsDict.put(localId, new ArrayList<>());
            }

            for (Map.Entry<String, Map<String, Object>> entry : trackingDict.entrySet()) {
                String serial = entry.getKey();
                Map<String, Object> data = entry.getValue();
                Integer offlineId = (Integer) data.get("OfflineID");

                if (uniqueLocalIds.contains(offlineId)) {
                    localIdsSerialsDict.get(offlineId).add(serial);
                }
            }

            for (int tmpIndex = 0; tmpIndex < indices.size(); tmpIndex++) {
                Integer localId = uniqueLocalIds.get(tmpIndex);
                List<String> serials = localIdsSerialsDict.get(localId);
                Integer index = indices.get(tmpIndex);

                for (String serial : serials) {
                    Map<String, Object> data = trackingDict.get(serial);
                    Integer frame = (Integer) data.get("Frame");
                    Map<String, Double> worldCoord = (Map<String, Double>) data.get("WorldCoordinate");

                    indexSerialsDict.get(index).add(serial);
                    indexFramesDict.get(index).add(frame);
                    indexWposListDict.get(index).add(new double[] {
                            worldCoord.get("x"),
                            worldCoord.get("y")
                    });
                }
            }
        }

        // Compute distance matrices
        for (int id1Index = 0; id1Index < shape - 1; id1Index++) {
            List<Integer> id1Frames = indexFramesDict.get(id1Index);
            List<double[]> id1WposList = indexWposListDict.get(id1Index);

            if (id1Frames.isEmpty()) {
                continue;
            }

            for (int id2Index = id1Index + 1; id2Index < shape; id2Index++) {
                List<Integer> id2Frames = indexFramesDict.get(id2Index);

                if (id2Frames.isEmpty()) {
                    continue;
                }

                // Find common frames
                Set<Integer> commonFrames = new HashSet<>(id1Frames);
                commonFrames.retainAll(id2Frames);

                if (commonFrames.size() < 1) {
                    continue;
                }

                List<double[]> id2WposList = indexWposListDict.get(id2Index);

                // Get overlapping positions
                List<double[]> id1LapWposList = new ArrayList<>();
                List<double[]> id2LapWposList = new ArrayList<>();

                for (int i = 0; i < id1Frames.size(); i++) {
                    if (commonFrames.contains(id1Frames.get(i))) {
                        id1LapWposList.add(id1WposList.get(i));
                    }
                }

                for (int i = 0; i < id2Frames.size(); i++) {
                    if (commonFrames.contains(id2Frames.get(i))) {
                        id2LapWposList.add(id2WposList.get(i));
                    }
                }

                // Compute distances
                double[] euclidDistances = measureEuclideanDistance(id1LapWposList, id2LapWposList);
                double minDistance = Arrays.stream(euclidDistances).min().orElse(Double.POSITIVE_INFINITY);
                double meanDistance = Arrays.stream(euclidDistances).average().orElse(Double.POSITIVE_INFINITY);
                double maxDistance = Arrays.stream(euclidDistances).max().orElse(Double.POSITIVE_INFINITY);

                minDistanceMatrix[id1Index][id2Index] = minDistance;
                minDistanceMatrix[id2Index][id1Index] = minDistance;

                if (commonFrames.size() > 120) {
                    meanDistanceMatrix[id1Index][id2Index] = meanDistance;
                    meanDistanceMatrix[id2Index][id1Index] = meanDistance;
                    maxDistanceMatrix[id1Index][id2Index] = maxDistance;
                    maxDistanceMatrix[id2Index][id1Index] = maxDistance;
                }
            }
        }

        return new DistanceMatrices(minDistanceMatrix, maxDistanceMatrix, meanDistanceMatrix);
    }

    public static class DistanceMatrices {
        public double[][] minDistanceMatrix;
        public double[][] maxDistanceMatrix;
        public double[][] meanDistanceMatrix;

        public DistanceMatrices(double[][] min, double[][] max, double[][] mean) {
            this.minDistanceMatrix = min;
            this.maxDistanceMatrix = max;
            this.meanDistanceMatrix = mean;
        }
    }

    /**
     * Minimize similarity if tracklets are overlapping in SCPT results
     * Python equivalent: minimize_similarity_by_sc_overlap
     */
    public static double[][] minimizeSimilarityByScOverlap(
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            double[][] matrix,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            List<Integer> clusters,
            Map<Integer, CameraDict> cameraDict,
            double replaceValue) {

        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);
            CameraDict camDict = cameraDict.get(cameraId);
            List<Integer> indices = camDict.indices;
            List<Integer> uniqueLocalIds = camDict.uniqueLocalIds;

            Map<Integer, List<Integer>> localIdsFrameDict = new HashMap<>();
            for (Integer localId : uniqueLocalIds) {
                localIdsFrameDict.put(localId, new ArrayList<>());
            }

            for (Map.Entry<String, Map<String, Object>> entry : trackingDict.entrySet()) {
                Map<String, Object> data = entry.getValue();
                Integer offlineId = (Integer) data.get("OfflineID");

                if (uniqueLocalIds.contains(offlineId)) {
                    Integer frame = (Integer) data.get("Frame");
                    localIdsFrameDict.get(offlineId).add(frame);
                }
            }

            for (int index1 = 0; index1 < indices.size() - 1; index1++) {
                Integer localId1 = uniqueLocalIds.get(index1);
                List<Integer> id1Frames = localIdsFrameDict.get(localId1);
                Integer id1Index = indices.get(index1);

                for (int index2 = index1 + 1; index2 < indices.size(); index2++) {
                    Integer localId2 = uniqueLocalIds.get(index2);
                    List<Integer> id2Frames = localIdsFrameDict.get(localId2);

                    Set<Integer> commonFrames = new HashSet<>(id1Frames);
                    commonFrames.retainAll(id2Frames);

                    if (commonFrames.isEmpty()) {
                        continue;
                    }

                    Integer id2Index = indices.get(index2);
                    matrix[id1Index][id2Index] = replaceValue;
                    matrix[id2Index][id1Index] = replaceValue;
                }
            }
        }

        return matrix;
    }

    /**
     * Replace negative values in similarity matrix based on world coordinates
     * Python equivalent: replace_negative_value_by_wcoordinate
     */
    public static double[][] replaceNegativeValueByWCoordinate(
            double[][] similarityMatrix,
            double[][] distanceMatrix,
            double distanceTh,
            double replaceValue) {

        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                double dist = distanceMatrix[i][j];
                if (dist != Double.POSITIVE_INFINITY && dist > distanceTh) {
                    similarityMatrix[i][j] = replaceValue;
                }
            }
        }

        return similarityMatrix;
    }

    /**
     * Maximize similarity based on world coordinates
     * Python equivalent: maximize_similarity_by_wcoordinate
     */
    public static double[][] maximizeSimilarityByWCoordinate(
            double[][] similarityMatrix,
            double[][] distanceMatrix,
            double maxDistanceTh) {

        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                double dist = distanceMatrix[i][j];
                if (dist < maxDistanceTh) {
                    similarityMatrix[i][j] = 1.0;
                }
            }
        }

        return similarityMatrix;
    }

    /**
     * Replace similarity matrix values based on spatial constraints
     * Python equivalent: replace_similarity
     */
    public static double[][] replaceSimilarity(
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            double[][] similarityMatrix,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            List<Integer> clusters,
            boolean checkScOverlap,
            boolean replaceSimilarityByWCoordinate,
            String distanceType,
            double distanceTh,
            double replaceValue,
            int shortTrackTh,
            int keypointConditionTh) {

        if (checkScOverlap) {
            Map<Integer, CameraDict> cameraDict = createCameraDict(
                    representativeNodes, shortTrackTh, keypointConditionTh);
            similarityMatrix = minimizeSimilarityByScOverlap(
                    representativeNodes, similarityMatrix, trackingResults,
                    clusters, cameraDict, replaceValue);
        }

        if (replaceSimilarityByWCoordinate) {
            DistanceMatrices matrices = createDistanceMatrix(
                    representativeNodes, trackingResults, distanceType,
                    shortTrackTh, keypointConditionTh);

            similarityMatrix = maximizeSimilarityByWCoordinate(
                    similarityMatrix, matrices.meanDistanceMatrix, 0.5);

            similarityMatrix = replaceNegativeValueByWCoordinate(
                    similarityMatrix, matrices.minDistanceMatrix, distanceTh, replaceValue);
        }

        return similarityMatrix;
    }

    /**
     * Translate camera coordinates to world coordinates using homography matrix
     * Python equivalent: translate_world_coordinate
     */
    public static double[] translateWorldCoordinate(double x, double y, double[][] homographyMatrix) {
        double[] vectorXyz = new double[] { x, y, 1.0 };

        // Compute inverse of homography matrix
        double[][] invMatrix = invertMatrix3x3(homographyMatrix);

        // Multiply
        double[] vectorXyz3d = new double[3];
        for (int i = 0; i < 3; i++) {
            vectorXyz3d[i] = 0;
            for (int j = 0; j < 3; j++) {
                vectorXyz3d[i] += invMatrix[i][j] * vectorXyz[j];
            }
        }

        // Normalize
        return new double[] {
                vectorXyz3d[0] / vectorXyz3d[2],
                vectorXyz3d[1] / vectorXyz3d[2]
        };
    }

    /**
     * Simple 3x3 matrix inversion (for homography)
     */
    private static double[][] invertMatrix3x3(double[][] matrix) {
        // Calculate determinant
        double det = matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);

        if (Math.abs(det) < 1e-10) {
            throw new IllegalArgumentException("Matrix is singular and cannot be inverted");
        }

        double[][] inverse = new double[3][3];

        inverse[0][0] = (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1]) / det;
        inverse[0][1] = (matrix[0][2] * matrix[2][1] - matrix[0][1] * matrix[2][2]) / det;
        inverse[0][2] = (matrix[0][1] * matrix[1][2] - matrix[0][2] * matrix[1][1]) / det;

        inverse[1][0] = (matrix[1][2] * matrix[2][0] - matrix[1][0] * matrix[2][2]) / det;
        inverse[1][1] = (matrix[0][0] * matrix[2][2] - matrix[0][2] * matrix[2][0]) / det;
        inverse[1][2] = (matrix[0][2] * matrix[1][0] - matrix[0][0] * matrix[1][2]) / det;

        inverse[2][0] = (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]) / det;
        inverse[2][1] = (matrix[0][1] * matrix[2][0] - matrix[0][0] * matrix[2][1]) / det;
        inverse[2][2] = (matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]) / det;

        return inverse;
    }
}
