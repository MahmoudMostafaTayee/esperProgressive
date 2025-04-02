package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;

import java.util.ArrayList;
import java.util.List;

public class AgglomerativeClusterer {
    private long agglomerative_clustering_time_tracker = 0;
    private final double epsilon;

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
            }

            double[][] distanceMatrix = SimilarityUtils.computeCosineDistanceMatrix(featureList.toArray(new double[0][]), this.epsilon);
            HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
            int[] clusterLabels = hc.partition(this.epsilon);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            agglomerative_clustering_time_tracker += durationMs;
            System.out.println("Agglomerative Clustering Time: " + agglomerative_clustering_time_tracker + " ms");

            for (int i = 0; i < clusterLabels.length; i++) {
                System.out.printf("Agglomerative ID %d => Cluster %d\n", idList.get(i), clusterLabels[i]);
            }
        }
    }
    public UpdateListener getListener(){
        return (newEvents, oldEvents,  statement,  runtime) ->
        {
            processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
        };
    }
}