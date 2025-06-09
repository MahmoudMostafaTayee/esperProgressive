package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.util.*;

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
            List<Integer> idList = new ArrayList<>();

            long start = System.nanoTime();
            for (EventBean e : newEvents) {
                List<Float> feature = (List<Float>) e.get("features");
                Integer id = (Integer) e.get("UNum");
                featureList.add(feature.stream().mapToDouble(Float::doubleValue).toArray());
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

            // 1. Build the cluster buckets
            Map<Integer, List<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < clusterLabels.length; i++) {
                int label = clusterLabels[i];
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
    }
    public UpdateListener getListener(){
        return (newEvents, oldEvents,  statement,  runtime) ->
        {
            processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
        };
    }
}