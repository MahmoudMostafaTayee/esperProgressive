package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    // ==================== KEYPOINT-BASED REPRESENTATIVE NODE SELECTION
    // ====================

    // ==================== WORLD COORDINATE & CALIBRATION ====================

    /**
     * Measure world coordinates in each node
     * Python equivalent: measure_world_coordinate
     */
    public static Map<Integer, Map<String, Map<String, Object>>> measureWorldCoordinate(
            int sceneId,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults) {

        Gson gson = new Gson();

        for (Integer cameraId : trackingResults.keySet()) {
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);
            String calibrationPath = String.format("Original/scene_%03d/camera_%04d/calibration.json", sceneId,
                    cameraId);

            // Check if file exists
            if (!Files.exists(Paths.get(calibrationPath))) {
                logger.warn("Calibration file not found at {}. Skipping world coordinate calculation for camera {}.",
                        calibrationPath, cameraId);
                continue;
            }

            try (FileReader reader = new FileReader(calibrationPath)) {
                java.lang.reflect.Type type = new TypeToken<Map<String, Object>>() {
                }.getType();
                Map<String, Object> calibrationJson = gson.fromJson(reader, type);

                @SuppressWarnings("unchecked")
                ArrayList<ArrayList<Double>> homographyList = (ArrayList<ArrayList<Double>>) calibrationJson
                        .get("homography matrix");

                double[][] homographyMatrix = new double[3][3];
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        homographyMatrix[i][j] = homographyList.get(i).get(j);
                    }
                }

                for (String serial : trackingDict.keySet()) {
                    Map<String, Object> value = trackingDict.get(serial);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> coord = (Map<String, Integer>) value.get("Coordinate");

                    int x1 = coord.get("x1");
                    int x2 = coord.get("x2");
                    int y2 = coord.get("y2");

                    double x = (x1 + x2) / 2.0;
                    double y = (double) y2;

                    double[] bboxWC = translateWorldCoordinate(x, y, homographyMatrix);

                    Map<String, Double> worldCoord = new HashMap<>();
                    worldCoord.put("x", bboxWC[0]);
                    worldCoord.put("y", bboxWC[1]);

                    value.put("WorldCoordinate", worldCoord);
                }

                logger.info("World coordinates calculated for camera {}", cameraId);

            } catch (Exception e) {
                logger.error("Error reading calibration file for camera " + cameraId, e);
            }
        }
        return trackingResults;
    }

    /**
     * Evaluate pose estimation keypoint quality
     * Python equivalent: eval_keypoints
     * 
     * @return EvalKeypointsResult containing condition (1-4), intersect ratio,
     *         score, and area
     */
    public static EvalKeypointsResult evalKeypoints(
            String serial,
            List<String> otherSerials,
            Map<String, List<List<Float>>> keypointsMap,
            Map<String, double[]> bboxMap,
            double keypointTh) {

        if (!keypointsMap.containsKey(serial) || keypointsMap.get(serial) == null) {
            return new EvalKeypointsResult(4, 1.0, 0.0, 0.0);
        }

        double[] bbox = bboxMap.get(serial);
        double x1 = bbox[0], y1 = bbox[1], x2 = bbox[2], y2 = bbox[3];
        List<List<Float>> keypoints = keypointsMap.get(serial);
        double area = (x2 - x1) * (y2 - y1);

        // Extract scores from keypoints
        List<Double> scores = new ArrayList<>();
        for (List<Float> kp : keypoints) {
            if (kp.size() >= 3) {
                scores.add((double) kp.get(2)); // score is 3rd element
            }
        }

        // Calculate intersect area with other serials
        double intersectArea = 0;
        for (String otherSerial : otherSerials) {
            if (!bboxMap.containsKey(otherSerial))
                continue;
            double[] otherBbox = bboxMap.get(otherSerial);
            double tmpIntersectArea = measureIntersectArea(bbox, otherBbox);
            intersectArea = Math.max(intersectArea, tmpIntersectArea);
        }
        double intersectRatio = intersectArea / area;

        // Evaluate keypoint quality
        double minScore = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double meanScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        int condition;
        double score;

        if (minScore >= keypointTh) {
            score = meanScore;
            condition = 1; // All keypoints high confidence
        } else {
            // Separate left/right scores (odd/even indices)
            List<Double> rightScores = new ArrayList<>();
            List<Double> leftScores = new ArrayList<>();

            for (int i = 0; i < scores.size(); i++) {
                if (i == 0)
                    continue; // Skip nose
                if (i % 2 == 0) {
                    rightScores.add(scores.get(i));
                } else {
                    leftScores.add(scores.get(i));
                }
            }

            double minRightScore = rightScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double minLeftScore = leftScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

            List<Double> targetScores = minLeftScore > minRightScore ? leftScores : rightScores;
            double minTargetScore = targetScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            score = targetScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            if (minTargetScore >= keypointTh) {
                condition = 2; // Half body high confidence
            } else {
                long highConfCount = targetScores.stream().filter(s -> s >= keypointTh).count();
                if ((double) highConfCount / targetScores.size() > 0.7) {
                    condition = 3; // Partial high confidence
                } else {
                    condition = 4; // Low confidence
                }
            }
        }

        return new EvalKeypointsResult(condition, intersectRatio, score, area);
    }

    public static class EvalKeypointsResult {
        public int condition;
        public double intersectRatio;
        public double score;
        public double area;

        public EvalKeypointsResult(int condition, double intersectRatio, double score, double area) {
            this.condition = condition;
            this.intersectRatio = intersectRatio;
            this.score = score;
            this.area = area;
        }
    }

    /**
     * Find high confidence keypoint node from tracklet
     * Python equivalent: find_high_confidence_keypoint_node
     */
    public static FindKeypointNodeResult findHighConfidenceKeypointNode(
            Map<String, Map<String, Object>> trackingDict,
            List<String> serials,
            Map<String, List<List<Float>>> keypointsMap,
            Map<Integer, List<String>> frameSerialsDict,
            double keypointTh) {

        List<Integer> conditions = new ArrayList<>();
        List<Double> intersects = new ArrayList<>();
        List<Double> imageScores = new ArrayList<>();
        List<Double> areas = new ArrayList<>();

        // Build bbox map
        Map<String, double[]> bboxMap = new HashMap<>();
        for (String serial : trackingDict.keySet()) {
            Map<String, Object> entry = trackingDict.get(serial);
            @SuppressWarnings("unchecked")
            Map<String, Integer> coord = (Map<String, Integer>) entry.get("Coordinate");
            bboxMap.put(serial, new double[] {
                    coord.get("x1"), coord.get("y1"), coord.get("x2"), coord.get("y2")
            });
        }

        for (String serial : serials) {
            Integer frame = (Integer) trackingDict.get(serial).get("Frame");
            List<String> otherSerials = new ArrayList<>(frameSerialsDict.getOrDefault(frame, new ArrayList<>()));
            otherSerials.remove(serial);

            EvalKeypointsResult result = evalKeypoints(serial, otherSerials, keypointsMap, bboxMap, keypointTh);
            conditions.add(result.condition);
            intersects.add(result.intersectRatio);
            imageScores.add(result.score);
            areas.add(result.area);
        }

        // Find minimum condition
        int minCondition = conditions.stream().mapToInt(Integer::intValue).min().orElse(4);

        // Get indices with minimum condition
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i) == minCondition) {
                indices.add(i);
            }
        }

        // Find index with max area among indices with min condition
        int maxIndex = 0;
        double maxArea = 0;
        for (int idx : indices) {
            if (areas.get(idx) > maxArea) {
                maxArea = areas.get(idx);
                maxIndex = idx;
            }
        }

        String selectedSerial = serials.get(maxIndex);
        double[] feature = (double[]) trackingDict.get(selectedSerial).get("Feature");

        return new FindKeypointNodeResult(selectedSerial, feature, minCondition);
    }

    public static class FindKeypointNodeResult {
        public String serial;
        public double[] feature;
        public int score;

        public FindKeypointNodeResult(String serial, double[] feature, int score) {
            this.serial = serial;
            this.feature = feature;
            this.score = score;
        }
    }

    /**
     * Decide representative nodes from each tracklet
     * Python equivalent: decide_representative_nodes
     * 
     * For streaming: keypoints are already in memory from DetectedUser
     */
    public static Map<Integer, Map<Integer, RepresentativeNode>> decideRepresentativeNodes(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            String representativeSelectionMethod,
            double epsilon,
            int shortTrackTh,
            double keypointTh,
            int[] imageSize,
            double aspectTh,
            int stackMaxSize) {

        Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes = new HashMap<>();

        for (Map.Entry<Integer, Map<String, Map<String, Object>>> cameraEntry : trackingResults.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = cameraEntry.getValue();

            representativeNodes.put(cameraId, new HashMap<>());

            // Build keypoints map if using keypoint-based selection
            Map<String, List<List<Float>>> keypointsMap = new HashMap<>();
            Map<Integer, List<String>> frameSerialsDict = new HashMap<>();

            if (representativeSelectionMethod.equals("keypoint")) {
                for (Map.Entry<String, Map<String, Object>> entry : trackingDict.entrySet()) {
                    String serial = entry.getKey();
                    Map<String, Object> data = entry.getValue();
                    @SuppressWarnings("unchecked")
                    List<List<Float>> keypoints = (List<List<Float>>) data.get("Keypoints");
                    keypointsMap.put(serial, keypoints);

                    Integer frame = (Integer) data.get("Frame");
                    Integer offlineId = (Integer) data.get("OfflineID");

                    if (offlineId != -1) {
                        frameSerialsDict.computeIfAbsent(frame, k -> new ArrayList<>()).add(serial);
                    }
                }
            }

            // Get unique local IDs
            List<Integer> localIds = new ArrayList<>();
            for (Map<String, Object> data : trackingDict.values()) {
                localIds.add((Integer) data.get("OfflineID"));
            }

            Set<Integer> uniqueLocalIdsSet = new TreeSet<>(localIds);
            uniqueLocalIdsSet.remove(-1);
            List<Integer> uniqueLocalIds = new ArrayList<>(uniqueLocalIdsSet);

            // Group serials by local ID
            Map<Integer, List<String>> localIdSerialsDict = new HashMap<>();
            for (Integer localId : uniqueLocalIds) {
                localIdSerialsDict.put(localId, new ArrayList<>());
            }

            for (Map.Entry<String, Map<String, Object>> entry : trackingDict.entrySet()) {
                String serial = entry.getKey();
                Integer localId = (Integer) entry.getValue().get("OfflineID");
                if (localId >= 0) {
                    localIdSerialsDict.get(localId).add(serial);
                }
            }

            // Get representative node for each cluster
            for (Integer localId : localIdSerialsDict.keySet()) {
                List<String> serials = localIdSerialsDict.get(localId);

                String representativeSerial;
                double[] representativeFeature;
                double score = 0;

                if (representativeSelectionMethod.equals("centrality")) {
                    FindCentalityResult result = findHighestCentralityNode(
                            trackingDict, serials, epsilon, stackMaxSize, imageSize, aspectTh);
                    if (result.serial != null) {
                        representativeSerial = result.serial;
                        representativeFeature = result.feature;
                        serials = result.newSerials;
                    } else {
                        continue;
                    }
                } else if (representativeSelectionMethod.equals("keypoint")) {
                    FindKeypointNodeResult result = findHighConfidenceKeypointNode(
                            trackingDict, serials, keypointsMap, frameSerialsDict, keypointTh);
                    representativeSerial = result.serial;
                    representativeFeature = result.feature;
                    score = result.score;
                } else {
                    logger.error("Invalid representative_selection_method: " + representativeSelectionMethod);
                    continue;
                }

                if (!serials.isEmpty()) {
                    RepresentativeNode node = new RepresentativeNode(
                            representativeSerial, representativeFeature, score, serials);
                    representativeNodes.get(cameraId).put(localId, node);
                }
            }
        }

        return representativeNodes;
    }

    // ==================== GLOBAL ID MANAGEMENT ====================

    /**
     * Get unique global IDs from tracking results
     * Python equivalent: get_unique_global_ids
     */
    public static List<Integer> getUniqueGlobalIds(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes) {

        Set<Integer> globalIds = new TreeSet<>();

        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);

            for (RepresentativeNode node : cameraEntry.getValue().values()) {
                String serial = node.serial;
                if (trackingDict.containsKey(serial)) {
                    Object gidObj = trackingDict.get(serial).get("GlobalOfflineID");
                    if (gidObj != null) {
                        globalIds.add((Integer) gidObj);
                    }
                }
            }
        }

        return new ArrayList<>(globalIds);
    }

    /**
     * Get serials assigned to each global ID
     * Python equivalent: get_serials_each_global_id
     */
    public static Map<Integer, Map<Integer, List<Pair<Integer, String>>>> getSerialsEachGlobalId(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            List<Integer> uniqueGlobalIds) {

        Map<Integer, Map<Integer, List<Pair<Integer, String>>>> globalSerialDict = new HashMap<>();

        for (Integer globalId : uniqueGlobalIds) {
            Map<Integer, List<Pair<Integer, String>>> cameraMap = new HashMap<>();
            for (Integer cameraId : representativeNodes.keySet()) {
                cameraMap.put(cameraId, new ArrayList<>());
            }
            globalSerialDict.put(globalId, cameraMap);
        }

        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);

            for (Map.Entry<Integer, RepresentativeNode> localEntry : cameraEntry.getValue().entrySet()) {
                Integer localId = localEntry.getKey();
                String serial = localEntry.getValue().serial;

                if (trackingDict.containsKey(serial)) {
                    Object gidObj = trackingDict.get(serial).get("GlobalOfflineID");
                    if (gidObj != null) {
                        Integer globalId = (Integer) gidObj;
                        globalSerialDict.get(globalId).get(cameraId).add(new Pair<>(localId, serial));
                    }
                }
            }
        }

        return globalSerialDict;
    }

    // Simple Pair class
    public static class Pair<L, R> {
        public L left;
        public R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }

    /**
     * Create feature stack for MCPT from target list
     * Python equivalent: create_mcpt_feature_stack
     */
    public static double[][] createMcptFeatureStack(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            List<Pair<Integer, String>> targetList) {

        List<double[]> featureList = new ArrayList<>();

        for (Pair<Integer, String> target : targetList) {
            Integer cameraId = target.left;
            String serial = target.right;

            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);
            if (trackingDict != null && trackingDict.containsKey(serial)) {
                double[] feature = (double[]) trackingDict.get(serial).get("Feature");
                if (feature != null) {
                    featureList.add(feature);
                }
            }
        }

        return featureList.toArray(new double[0][]);
    }

    /**
     * Delete global IDs with too few serials
     * Python equivalent: delete_small_global_id
     */
    public static DeleteSmallGlobalIdResult deleteSmallGlobalId(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            Map<Integer, Map<Integer, List<Pair<Integer, String>>>> globalSerialDict,
            int deleteGidTh,
            boolean deleteFewCameraCluster) {

        List<Integer> deleteGlobalIds = new ArrayList<>();
        List<Integer> saveGlobalIds = new ArrayList<>();

        for (Map.Entry<Integer, Map<Integer, List<Pair<Integer, String>>>> entry : globalSerialDict.entrySet()) {
            Integer globalId = entry.getKey();
            int serialCounter = 0;
            Set<Integer> cameraIds = new HashSet<>();

            for (Map.Entry<Integer, List<Pair<Integer, String>>> cameraEntry : entry.getValue().entrySet()) {
                Integer cameraId = cameraEntry.getKey();
                if (!cameraEntry.getValue().isEmpty()) {
                    cameraIds.add(cameraId);
                }

                for (Pair<Integer, String> pair : cameraEntry.getValue()) {
                    Integer localId = pair.left;
                    if (representativeNodes.get(cameraId).containsKey(localId)) {
                        List<String> allSerials = representativeNodes.get(cameraId).get(localId).allSerials;
                        serialCounter += allSerials.size();
                    }
                }
            }

            if (serialCounter < deleteGidTh) {
                deleteGlobalIds.add(globalId);
                continue;
            }

            if (deleteFewCameraCluster && cameraIds.size() < 3) {
                deleteGlobalIds.add(globalId);
                continue;
            }

            saveGlobalIds.add(globalId);
        }

        // Remove GlobalOfflineID from tracking results
        for (Map<String, Map<String, Object>> trackingDict : trackingResults.values()) {
            for (Map<String, Object> data : trackingDict.values()) {
                Object gidObj = data.get("GlobalOfflineID");
                if (gidObj != null && deleteGlobalIds.contains(gidObj)) {
                    data.remove("GlobalOfflineID");
                }
            }
        }

        return new DeleteSmallGlobalIdResult(trackingResults, saveGlobalIds);
    }

    public static class DeleteSmallGlobalIdResult {
        public Map<Integer, Map<String, Map<String, Object>>> trackingResults;
        public List<Integer> uniqueGlobalIds;

        public DeleteSmallGlobalIdResult(
                Map<Integer, Map<String, Map<String, Object>>> trackingResults,
                List<Integer> uniqueGlobalIds) {
            this.trackingResults = trackingResults;
            this.uniqueGlobalIds = uniqueGlobalIds;
        }
    }

    /**
     * Assign unclustered tracklets to existing global IDs
     * Python equivalent: assign_global_id
     */
    @SuppressWarnings("unchecked")
    public static Map<Integer, Map<String, Map<String, Object>>> assignGlobalId(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            double simTh,
            boolean assignAllTracklet) {

        int counter = 0;
        List<Pair<Integer, Pair<Integer, Pair<Integer, String>>>> assignedTracks = new ArrayList<>();
        List<Pair<Integer, Integer>> unassignedTracks = new ArrayList<>();

        // Collect assigned and unassigned tracks
        for (Map.Entry<Integer, Map<Integer, RepresentativeNode>> cameraEntry : representativeNodes.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);

            for (Map.Entry<Integer, RepresentativeNode> localEntry : cameraEntry.getValue().entrySet()) {
                Integer localId = localEntry.getKey();
                String serial = localEntry.getValue().serial;

                Object gidObj = trackingDict.get(serial).get("GlobalOfflineID");
                if (gidObj != null) {
                    Integer globalId = (Integer) gidObj;
                    assignedTracks.add(new Pair<>(globalId, new Pair<>(cameraId, new Pair<>(localId, serial))));
                } else {
                    unassignedTracks.add(new Pair<>(cameraId, localId));
                }
            }
        }

        // Create feature stack from assigned tracks
        List<Pair<Integer, String>> targetList = new ArrayList<>();
        List<Integer> globalIds = new ArrayList<>();

        for (Pair<Integer, Pair<Integer, Pair<Integer, String>>> track : assignedTracks) {
            Integer globalId = track.left;
            Integer cameraId = track.right.left;
            String serial = track.right.right.right;

            targetList.add(new Pair<>(cameraId, serial));
            globalIds.add(globalId);
        }

        double[][] featureStack = createMcptFeatureStack(trackingResults, targetList);

        // Compute norms for cosine similarity
        double[] featureNorms = new double[featureStack.length];
        for (int i = 0; i < featureStack.length; i++) {
            double norm = 0;
            for (double v : featureStack[i]) {
                norm += v * v;
            }
            featureNorms[i] = Math.sqrt(norm);
        }

        // Assign unassigned tracks
        for (Pair<Integer, Integer> unassigned : unassignedTracks) {
            Integer cameraId = unassigned.left;
            Integer localId = unassigned.right;

            RepresentativeNode node = representativeNodes.get(cameraId).get(localId);
            double[] feature = node.feature;

            if (feature == null)
                continue;

            // Compute cosine similarities
            double featureNorm = 0;
            for (double v : feature) {
                featureNorm += v * v;
            }
            featureNorm = Math.sqrt(featureNorm);

            double[] cosSims = new double[featureStack.length];
            for (int i = 0; i < featureStack.length; i++) {
                double dot = 0;
                for (int j = 0; j < feature.length && j < featureStack[i].length; j++) {
                    dot += feature[j] * featureStack[i][j];
                }
                cosSims[i] = dot / (featureNorm * featureNorms[i]);
            }

            if (!assignAllTracklet) {
                double maxSim = Arrays.stream(cosSims).max().orElse(0);
                if (maxSim < simTh) {
                    continue;
                }
            }

            // Find similar indices
            List<Integer> similarIndices = new ArrayList<>();
            for (int i = 0; i < cosSims.length; i++) {
                if (cosSims[i] >= simTh) {
                    similarIndices.add(i);
                }
            }

            if (similarIndices.isEmpty()) {
                continue;
            }

            // Get mode of global IDs
            List<Integer> tmpGlobalIds = new ArrayList<>();
            for (int idx : similarIndices) {
                tmpGlobalIds.add(globalIds.get(idx));
            }

            Integer assignedGlobalId = findMode(tmpGlobalIds);

            // Assign global ID to all serials in the tracklet
            counter++;
            for (String serial : node.allSerials) {
                trackingResults.get(cameraId).get(serial).put("GlobalOfflineID", assignedGlobalId);
            }
        }

        logger.info("{} tracklets were reassigned", counter);
        return trackingResults;
    }

    private static Integer findMode(List<Integer> list) {
        Map<Integer, Integer> frequency = new HashMap<>();
        for (Integer val : list) {
            frequency.put(val, frequency.getOrDefault(val, 0) + 1);
        }
        return frequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Perform global ID reassignment
     * Python equivalent: global_id_reassignment
     */
    public static Map<Integer, Map<String, Map<String, Object>>> globalIdReassignment(
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes,
            int deleteGidTh,
            double simTh,
            boolean assignAllTracklet,
            boolean deleteFewCameraCluster) {

        List<Integer> uniqueGlobalIds = getUniqueGlobalIds(trackingResults, representativeNodes);

        Map<Integer, Map<Integer, List<Pair<Integer, String>>>> globalSerialDict = getSerialsEachGlobalId(
                trackingResults, representativeNodes, uniqueGlobalIds);

        DeleteSmallGlobalIdResult deleteResult = deleteSmallGlobalId(
                trackingResults, representativeNodes, globalSerialDict, deleteGidTh, deleteFewCameraCluster);

        trackingResults = assignGlobalId(
                deleteResult.trackingResults, representativeNodes, simTh, assignAllTracklet);

        return trackingResults;
    }

    // ==================== MAIN MCPT FUNCTION ====================

    /**
     * Perform multi-camera people tracking
     * Python equivalent: multi_camera_people_tracking
     * 
     * For streaming: This processes a window of tracking results from multiple
     * cameras
     */
    public static Map<Integer, Map<String, Map<String, Object>>> multiCameraPeopleTracking(
            Map<Integer, double[][]> calibrationMap,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            String representativeSelectionMethod,
            double epsilon,
            int shortTrackTh,
            double keypointTh,
            int keypointConditionTh,
            boolean replaceSimilarityByWCoordinate,
            String distanceType,
            double distanceTh,
            double replaceValue,
            int[] imageSize,
            double aspectTh,
            int stackMaxSize,
            int winIdx) {

        logger.info("Running multi_camera_people_tracking");
        logger.info("representative_selection_method: {}", representativeSelectionMethod);
        logger.info("short_track_th: {}", shortTrackTh);
        logger.info("epsilon: {}", epsilon);

        // Measure World Coordinates (using streamed calibration data)
        trackingResults = measureWorldCoordinate(calibrationMap, trackingResults);

        // Determine availability (check first element of first camera)
        boolean worldCoordAvailable = false;
        if ((!trackingResults.isEmpty())) {
            Map<String, Map<String, Object>> firstCam = trackingResults.values().iterator().next();
            if (!firstCam.isEmpty()) {
                Map<String, Object> firstTrack = firstCam.values().iterator().next();
                if (firstTrack.containsKey("WorldCoordinate")) {
                    worldCoordAvailable = true;
                }
            }
        }

        if (replaceSimilarityByWCoordinate && !worldCoordAvailable) {
            logger.warn(
                    "replaceSimilarityByWCoordinate requested but World Coordinates not available (no calibration data?). Disabling.");
            replaceSimilarityByWCoordinate = false;
        }

        // Get representative nodes
        Map<Integer, Map<Integer, RepresentativeNode>> representativeNodes = decideRepresentativeNodes(
                trackingResults, representativeSelectionMethod, epsilon, shortTrackTh,
                keypointTh, imageSize, aspectTh, stackMaxSize);

        logger.info("Representative features selected");

        // Create similarity matrix
        double[][] similarityMatrix = createSimilarityMatrixMCPT(
                representativeNodes, shortTrackTh, keypointConditionTh);

        // DUMP: MCPT similarity matrix (before zero-out)
        if (TrackingParameters.isDebug) {
            dumpMcptMatrix(similarityMatrix, "mcpt-similarity-matrix-raw_" + winIdx);
        }

        // Zero out low similarity values
        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                if (similarityMatrix[i][j] < 1.0 - epsilon) {
                    similarityMatrix[i][j] = 0.0;
                }
            }
        }

        // DUMP: MCPT similarity matrix (after zero-out)
        if (TrackingParameters.isDebug) {
            dumpMcptMatrix(similarityMatrix, "mcpt-similarity-matrix-zeroed_" + winIdx);
        }

        List<Integer> clusters = new ArrayList<>();
        for (int i = 0; i < similarityMatrix.length; i++) {
            clusters.add(i);
        }

        logger.info("Number of tracklets: {}", clusters.size());
        logger.info("Unique clusters: {}", new HashSet<>(clusters).size()); // Incorrect usage of current list but for
                                                                            // log symmetry

        // Replace similarity based on constraints
        similarityMatrix = replaceSimilarity(
                representativeNodes, similarityMatrix, trackingResults, clusters,
                false, replaceSimilarityByWCoordinate, distanceType, distanceTh,
                replaceValue, shortTrackTh, keypointConditionTh);

        // DUMP: MCPT similarity matrix (after replaceSimilarity)
        if (TrackingParameters.isDebug) {
            dumpMcptMatrix(similarityMatrix, "mcpt-similarity-matrix-replaced_" + winIdx);
        }

        // Perform Re-identification using hierarchical clustering
        clusters = SCPT.associateCluster(clusters,
                convertSimilarityToDistanceMatrix(similarityMatrix),
                epsilon, true, 2, false);

        // DUMP: Clusters after hierarchical clustering
        if (TrackingParameters.isDebug) {
            dumpMcptClusters(clusters, "mcpt-clusters-after-hc_" + winIdx);
        }

        logger.info("Unique clusters after HC: {}", new HashSet<>(clusters).size());

        // Create camera dictionary
        Map<Integer, CameraDict> cameraDict = createCameraDict(
                representativeNodes, shortTrackTh, keypointConditionTh);

        // DUMP: Camera dictionary mapping
        if (TrackingParameters.isDebug) {
            dumpMcptCameraDict(cameraDict, "mcpt-camera-dict_" + winIdx);
        }

        // Assign global IDs
        for (Map.Entry<Integer, CameraDict> cameraEntry : cameraDict.entrySet()) {
            Integer cameraId = cameraEntry.getKey();
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);
            CameraDict dict = cameraEntry.getValue();

            List<Integer> indices = dict.indices;
            List<Integer> localIds = dict.uniqueLocalIds;

            Map<Integer, Integer> localIdClusterDict = new HashMap<>();
            for (int i = 0; i < indices.size(); i++) {
                localIdClusterDict.put(localIds.get(i), clusters.get(indices.get(i)));
            }

            // Assign global IDs to tracking dict
            for (Map.Entry<String, Map<String, Object>> entry : trackingDict.entrySet()) {
                Integer localId = (Integer) entry.getValue().get("OfflineID");
                if (localIdClusterDict.containsKey(localId)) {
                    entry.getValue().put("GlobalOfflineID", localIdClusterDict.get(localId));
                }
            }
        }

        // DUMP: Final global ID assignments
        if (TrackingParameters.isDebug) {
            dumpMcptGlobalIds(trackingResults, "mcpt-global-ids_" + winIdx);
        }

        return trackingResults;
    }

    // ==================== MCPT DUMP HELPERS ====================

    private static void dumpMcptMatrix(double[][] matrix, String filename) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(TrackingParameters.OUTPUT_DIR, "mcpt-dumps");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path filePath = dir.resolve(filename + ".txt");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(filePath.toFile()))) {
                for (double[] row : matrix) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < row.length; j++) {
                        sb.append(String.format("%.6f", row[j]));
                        if (j < row.length - 1)
                            sb.append(", ");
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }
            logger.info("Dumped MCPT matrix to: {}", filePath);
        } catch (java.io.IOException e) {
            logger.error("Failed to dump MCPT matrix: " + filename, e);
        }
    }

    private static void dumpMcptClusters(List<Integer> clusters, String filename) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(TrackingParameters.OUTPUT_DIR, "mcpt-dumps");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path filePath = dir.resolve(filename + ".txt");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(filePath.toFile()))) {
                writer.write(clusters.toString());
            }
            logger.info("Dumped MCPT clusters to: {}", filePath);
        } catch (java.io.IOException e) {
            logger.error("Failed to dump MCPT clusters: " + filename, e);
        }
    }

    private static void dumpMcptCameraDict(Map<Integer, CameraDict> cameraDict, String filename) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(TrackingParameters.OUTPUT_DIR, "mcpt-dumps");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path filePath = dir.resolve(filename + ".txt");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(filePath.toFile()))) {
                for (Map.Entry<Integer, CameraDict> entry : cameraDict.entrySet()) {
                    writer.write("Camera " + entry.getKey() + ":");
                    writer.newLine();
                    writer.write("  indices: " + entry.getValue().indices);
                    writer.newLine();
                    writer.write("  uniqueLocalIds: " + entry.getValue().uniqueLocalIds);
                    writer.newLine();
                }
            }
            logger.info("Dumped MCPT camera dict to: {}", filePath);
        } catch (java.io.IOException e) {
            logger.error("Failed to dump MCPT camera dict: " + filename, e);
        }
    }

    private static void dumpMcptGlobalIds(Map<Integer, Map<String, Map<String, Object>>> trackingResults,
            String filename) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(TrackingParameters.OUTPUT_DIR, "mcpt-dumps");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path filePath = dir.resolve(filename + ".txt");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(filePath.toFile()))) {
                for (Map.Entry<Integer, Map<String, Map<String, Object>>> camEntry : trackingResults.entrySet()) {
                    writer.write("Camera " + camEntry.getKey() + ":");
                    writer.newLine();
                    for (Map.Entry<String, Map<String, Object>> entry : camEntry.getValue().entrySet()) {
                        Object globalId = entry.getValue().get("GlobalOfflineID");
                        Object localId = entry.getValue().get("OfflineID");
                        if (globalId != null) {
                            writer.write("  " + entry.getKey() + ": localId=" + localId + " -> globalId=" + globalId);
                            writer.newLine();
                        }
                    }
                }
            }
            logger.info("Dumped MCPT global IDs to: {}", filePath);
        } catch (java.io.IOException e) {
            logger.error("Failed to dump MCPT global IDs: " + filename, e);
        }
    }

    private static double[][] convertSimilarityToDistanceMatrix(double[][] similarityMatrix) {
        if (similarityMatrix.length == 0) {
            return new double[0][0];
        }
        double[][] distanceMatrix = new double[similarityMatrix.length][similarityMatrix[0].length];
        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[i].length; j++) {
                distanceMatrix[i][j] = 1.0 - similarityMatrix[i][j];
            }
        }
        return distanceMatrix;
    }

    /**
     * Measure world coordinates using cached calibration data
     */
    public static Map<Integer, Map<String, Map<String, Object>>> measureWorldCoordinate(
            Map<Integer, double[][]> calibrationMap,
            Map<Integer, Map<String, Map<String, Object>>> trackingResults) {

        for (Integer cameraId : trackingResults.keySet()) {
            Map<String, Map<String, Object>> trackingDict = trackingResults.get(cameraId);

            if (!calibrationMap.containsKey(cameraId)) {
                logger.warn("No calibration data found for camera {}. Skipping WC calculation.", cameraId);
                continue;
            }

            double[][] homographyMatrix = calibrationMap.get(cameraId);

            try {
                for (String serial : trackingDict.keySet()) {
                    Map<String, Object> value = trackingDict.get(serial);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> coord = (Map<String, Integer>) value.get("Coordinate");

                    int x1 = coord.get("x1");
                    int x2 = coord.get("x2");
                    int y2 = coord.get("y2");

                    double x = (x1 + x2) / 2.0;
                    double y = (double) y2;

                    double[] bboxWC = translateWorldCoordinate(x, y, homographyMatrix);

                    Map<String, Double> worldCoord = new HashMap<>();
                    worldCoord.put("x", bboxWC[0]);
                    worldCoord.put("y", bboxWC[1]);

                    value.put("WorldCoordinate", worldCoord);
                }

                logger.info("World coordinates calculated for camera {}", cameraId);

            } catch (Exception e) {
                logger.error("Error calculating world coordinates for camera " + cameraId, e);
            }
        }
        return trackingResults;
    }
}
