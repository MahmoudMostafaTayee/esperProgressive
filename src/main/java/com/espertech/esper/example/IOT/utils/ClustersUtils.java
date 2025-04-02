package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.yahoo.labs.samoa.instances.*;
import moa.cluster.Cluster;
import moa.cluster.Clustering;
import moa.clusterers.clustream.Clustream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.SingleLinkage;
import com.espertech.esper.runtime.client.UpdateListener;
import com.espertech.esper.example.IOT.helpers.SimilarityUtils;
import java.util.AbstractMap;

import java.util.*;

import static java.lang.System.exit;

public class ClustersUtils {
    private static final Logger logger = LoggerFactory.getLogger(ClustersUtils.class);

    public static class CluStream {
        private final Clustream cluStream = new Clustream();
        private final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
        private InstancesHeader cluStreamHeader;
        private long clustream_clustering_time_tracker = 0;
        private int cluster_number = 0;
        private int numberOfClusters = 1;

        // Buffer for instances during initialization
        private final List<AbstractMap.SimpleEntry<Integer, Instance>> initializationBuffer = new ArrayList<>();
        private boolean isInitialized = false;

        public CluStream() {
            cluStream.prepareForUse();
//            cluStream.maxNumKernelsOption.setValue(numClusters);
            cluStream.resetLearningImpl();
        }

        private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
                if (newEvents != null) {
                    if (cluStreamHeader == null) {
                        List<Float> firstFeature = (List<Float>) newEvents[0].get("features");
                        cluStreamHeader = createHeader(firstFeature.size());
                        cluStream.setModelContext(cluStreamHeader);
                    }

                    for (EventBean e : newEvents) {
                        List<Float> featureList = (List<Float>) e.get("features");
                        Long frameRecordCount = (Long) e.get("frameRecordCount");
                        if(numberOfClusters < frameRecordCount){
                            numberOfClusters = frameRecordCount.intValue();
                        }
                        Integer id = (Integer) e.get("UNum");
                        Integer curFrame = (Integer) e.get("curFrame");
                        logger.info("Current Frame: " + curFrame + " -> Frame Record Count: " + frameRecordCount + " -> Number of Clusters: " + numberOfClusters);

                        Instance instance = convertFeatureToInstance(featureList, cluStreamHeader);
                        long start = System.nanoTime();
                        cluStream.trainOnInstance(instance);
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        clustream_clustering_time_tracker += durationMs;

                        // Step 1: Get micro-clustering result
                        Clustering microClusters = cluStream.getMicroClusteringResult();
                        if(!isInitialized && (microClusters == null || microClusters.getClustering().isEmpty())){
                            // Store using SimpleEntry
                            initializationBuffer.add(new AbstractMap.SimpleEntry<>(id, instance));

                            System.out.println(("CluStream Initializing cluster number " + cluster_number));
                            cluster_number++;
                            continue;
                        }

                        // Step 2: Extract list of clusters from micro-clustering
                        List<? extends Cluster> microClusterList = microClusters.getClustering();

                        // Step 3: Apply k-Means on the extracted list
                        Clustering macroClusters = Clustream.kMeans(numberOfClusters+2, microClusterList);
                        logger.info("Actual Clusters Found: " + macroClusters.size());

                        if (!isInitialized) {
                            isInitialized = true;
                            System.out.println("Initialization complete - processing buffered instances");
                            processBufferedInstances(macroClusters);
                        }

                        // Step 4: Assign instance to the nearest macro-cluster
                        int assignedCluster = getNearestCluster(macroClusters, instance);
                        if (assignedCluster == -1) {
                            assignedCluster = cluster_number;
                        }
                        clusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(id);

                        System.out.println("CluStream Data ID: " + id + " assigned to cluster: " + assignedCluster);
                    }
                    System.out.println("CluStream Clustering Time: " + clustream_clustering_time_tracker + " ms");
                }
        }

        private void processBufferedInstances(Clustering clustering) {
            for (AbstractMap.SimpleEntry<Integer, Instance> buffered : initializationBuffer) {

                int assignedCluster = getNearestCluster(clustering, buffered.getValue());
                clusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(buffered.getKey());
                System.out.println("Processed buffered instance " + buffered.getKey() + " as cluster " + assignedCluster);
            }
            initializationBuffer.clear();
        }

        public UpdateListener getListener(){
            return (newEvents, oldEvents,  statement,  runtime) ->
            {
                processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
            };
        }
    }

    public static class AgglomerativeClustering {
        private long agglomerative_clustering_time_tracker = 0;
        private final double epsilon;

        public AgglomerativeClustering(double epsilon){
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

    public static class ClusTree {
        private final moa.clusterers.clustree.ClusTree clusTree = new moa.clusterers.clustree.ClusTree();

        public ClusTree() {
            clusTree.prepareForUse();
        }

        private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
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
        }

        public UpdateListener getListener(){
            return (newEvents, oldEvents,  statement,  runtime) ->
            {
                processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
            };
        }
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private static int getNearestCluster(Clustering clustering, Instance instance) {
        if (clustering == null || clustering.getClustering().isEmpty()) {
            return -1;
        }

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
