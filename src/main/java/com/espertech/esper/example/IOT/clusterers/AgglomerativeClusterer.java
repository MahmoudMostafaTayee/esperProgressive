package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.ClusteringUtils;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import com.espertech.esper.example.IOT.helpers.debug;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.exit;

public class AgglomerativeClusterer {
    private static final Logger logger = LoggerFactory.getLogger(AgglomerativeClusterer.class);

    private long agglomerative_clustering_time_tracker = 0;
    private final double epsilon;
    private int numberOfClusters = 1;

    public AgglomerativeClusterer(double epsilon){
        this.epsilon = epsilon;
    }
    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime){
        if (newEvents != null) {
            List<double[]> featureList = new ArrayList<>();
            List<Integer> frameNumbers = new ArrayList<>();
            List<Integer> serialNumbers = new ArrayList<>();
            List<Integer> idList = new ArrayList<>();
            List<Integer[]> boundingBoxList = new ArrayList<>();

            long start = System.nanoTime();
            for (EventBean e : newEvents) {
                List<Float> feature = (List<Float>) e.get("features");
                Integer id = (Integer) e.get("UNum");
                Long timestamp = (Long) e.get("timestamp");
                Integer frameNumber = (Integer) e.get("curFrame");

                int x1 = (Integer) e.get("x1");
                int x2 = (Integer) e.get("x2");
                int y1 = (Integer) e.get("y1");
                int y2 = (Integer) e.get("y2");
                boundingBoxList.add(new Integer[]{x1, x2, y1, y2});

                featureList.add(feature.stream().mapToDouble(Float::doubleValue).toArray());
                frameNumbers.add(frameNumber);
                serialNumbers.add(id);
                idList.add(id);

                Long frameRecordCount = (Long) e.get("frameRecordCount");
                if(numberOfClusters < frameRecordCount){
                    numberOfClusters = frameRecordCount.intValue();
                }
            }

            double[][] distanceMatrix = SimilarityUtils.computeCosineDistanceMatrix(featureList.toArray(new double[0][]), this.epsilon);
            HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
            int[] clusterLabels = hc.partition(this.epsilon);
            System.out.println("clusterLabels: " + Arrays.toString(clusterLabels));
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            agglomerative_clustering_time_tracker += durationMs;
            System.out.println("Agglomerative Clustering Time: " + agglomerative_clustering_time_tracker + " ms");

            List<Integer> clusterLabelsList = Arrays.stream(clusterLabels)
                    .boxed()
                    .collect(Collectors.toList());

            if (TrackingParameters.isDebug) {
                // This code snippet saves the distance matrix and frame numbers to CSV files to be compared with original code.
                try {
                    debug.saveDoubleMatrix(TrackingParameters.OUTPUT_DIR + "\\distance_matrix.csv", distanceMatrix);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\frame_numbers.txt", frameNumbers);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\serial_numbers.txt", serialNumbers);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    debug.saveIntList(TrackingParameters.OUTPUT_DIR + "\\cluster_labels.txt", clusterLabelsList);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            List<Integer> newClusterLabels =
            ClusteringUtils.tracking_by_clustering(
                                                        distanceMatrix,
                                                        frameNumbers,
                                                        serialNumbers,
                                                        clusterLabelsList,
                                                        this.epsilon
                                                        );

            System.out.println("newClusterLabels: " + Arrays.toString(newClusterLabels.toArray()));

            Map<Integer, List<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < clusterLabels.length; i++) {
                int label = newClusterLabels.get(i);
                int id    = idList.get(i);
                clusters.computeIfAbsent(label, k -> new ArrayList<>()).add(id);
            }

            // 2. Print each cluster in order
            clusters.keySet().stream()
                    .sorted()
                    .forEach(label -> {
                        List<Integer> members = clusters.get(label);
                        System.out.printf("Cluster %d: %s%n", label, members);
                    });
        }
        exit(0);
    }
    public UpdateListener getListener(){
        return (newEvents, oldEvents,  statement,  runtime) ->
        {
            processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
        };
    }
}