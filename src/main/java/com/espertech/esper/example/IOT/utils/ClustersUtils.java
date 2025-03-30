package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.common.client.EventBean;
import com.yahoo.labs.samoa.instances.*;
import moa.cluster.Cluster;
import moa.cluster.Clustering;
import moa.clusterers.clustream.Clustream;
import moa.clusterers.clustree.ClusTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;
import com.espertech.esper.runtime.client.UpdateListener;

import java.util.*;

public class ClustersUtils {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);

    public static class CluStreamListener {
        private final Clustream cluStream = new Clustream();
        private final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
        private InstancesHeader cluStreamHeader;
        private long clustream_clustering_time_tracker = 0;
        private int cluster_number = 0;

        public CluStreamListener(int numClusters) {
            cluStream.prepareForUse();
            cluStream.maxNumKernelsOption.setValue(numClusters);
            cluStream.resetLearningImpl();
        }

        public UpdateListener cluStreamListener() {
            return (newEvents, oldEvents,  statement,  runtime) -> {
                if (newEvents != null) {
                    if (cluStreamHeader == null) {
                        List<Float> firstFeature = (List<Float>) newEvents[0].get("features");
                        cluStreamHeader = createHeader(firstFeature.size());
                        cluStream.setModelContext(cluStreamHeader);
                    }

                    for (EventBean e : newEvents) {
                        List<Float> featureList = (List<Float>) e.get("features");
                        Integer id = (Integer) e.get("UNum");
                        Instance instance = convertFeatureToInstance(featureList, cluStreamHeader);
                        long start = System.nanoTime();
                        cluStream.trainOnInstance(instance);
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        clustream_clustering_time_tracker += durationMs;
                        Clustering clustering = cluStream.getMicroClusteringResult();
                        int assignedCluster = getNearestCluster(clustering, instance);
                        if (assignedCluster == -1) {
                            assignedCluster = cluster_number;
                        }
                        clusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(id);
                        if ((clustering == null) || (clustering.getClustering().isEmpty())) {
                            System.out.println(("CluStream Initializing id number " + id + " as cluster " + cluster_number));
                            cluster_number++;
                            continue;
                        }

                        System.out.println("CluStream Data ID: " + id + " assigned to cluster: " + assignedCluster);
                    }
                    System.out.println("CluStream Clustering Time: " + clustream_clustering_time_tracker + " ms");
                }
            };
        }
    }

    public static class AgglomerativeClusteringListener {
        private long agglomerative_clustering_time_tracker = 0;

        public UpdateListener agglomerativeListener() {
            return (newEvents, oldEvents,  statement,  runtime) ->
            {
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

                    double[][] distanceMatrix = computeCosineDistanceMatrix(featureList.toArray(new double[0][]));
                    HierarchicalClustering hc = HierarchicalClustering.fit(new SingleLinkage(distanceMatrix));
                    int[] clusterLabels = hc.partition(0.1);
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    agglomerative_clustering_time_tracker += durationMs;
                    System.out.println("Agglomerative Clustering Time: " + agglomerative_clustering_time_tracker + " ms");

                    for (int i = 0; i < clusterLabels.length; i++) {
                        System.out.printf("Agglomerative ID %d => Cluster %d\n", idList.get(i), clusterLabels[i]);
                    }
                }
            };
        }
    }

    public static class ClusTreeListener {
        private final ClusTree clusTree = new ClusTree();

        public ClusTreeListener() {
            clusTree.prepareForUse();
        }

        public UpdateListener clusTreeListener() {
            return (newEvents, oldEvents,  statement,  runtime) ->
            {
                if (newEvents != null) {
                    for (EventBean e : newEvents) {
                        List<Float> feature = (List<Float>) e.get("features");
                        Integer id = (Integer) e.get("UNum");
                        Instance instance = convertFeatureToInstance(feature, createHeader(feature.size()));
                        clusTree.trainOnInstance(instance);
                        Clustering microClusters = clusTree.getMicroClusteringResult();
                        int assignedCluster = getNearestCluster(microClusters, instance);
                        System.out.println("ClusTree Data ID: " + id + " -> Cluster: " + assignedCluster);
                    }
                }
            };
        }
    }

    private static double[][] computeCosineDistanceMatrix(double[][] features) {
        int n = features.length;
        double[][] dist = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double sim = cosineDistance(features[i], features[j]);
                dist[i][j] = sim;
                dist[j][i] = sim;
            }
        }
        return dist;
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public static double cosineDistance(double[] a, double[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return 1.0 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static int getNearestCluster(Clustering clustering, Instance instance) {
        int bestClusterIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        double[] instanceVector = instance.toDoubleArray();

        List<Cluster> clusters = clustering.getClustering();
        for (int i = 0; i < clusters.size(); i++) {
            Cluster cluster = clusters.get(i);
            double[] center = cluster.getCenter();
            double distance = euclideanDistance(instanceVector, center);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestClusterIndex = i;
            }
        }
        return bestClusterIndex;
    }

    private static InstancesHeader createHeader(int numAttributes) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("timestamp"));  // Must match position

        for (int i = 0; i < numAttributes; i++) {
            attributes.add(new Attribute("attr" + i));
        }

        Instances dataset = new Instances("feature_stream", attributes, 0);
        return new InstancesHeader(dataset);
    }

    private static Instance convertFeatureToInstance(List<Float> featureVec, InstancesHeader header) {
        double[] values = featureVec.stream().mapToDouble(Float::doubleValue).toArray();
        Instance instance = new DenseInstance(1.0, values);
        instance.setDataset(header);
        return instance;
    }
}
