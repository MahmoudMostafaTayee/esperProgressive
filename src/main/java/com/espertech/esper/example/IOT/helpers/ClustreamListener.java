package com.espertech.esper.example.IOT.helpers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.EventEPLUtil;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import com.yahoo.labs.samoa.instances.*;
import moa.cluster.Cluster;
import moa.cluster.Clustering;
import moa.clusterers.clustream.Clustream;
import moa.clusterers.clustree.ClusTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.Linkage;
import smile.clustering.linkage.SingleLinkage;
import java.util.AbstractMap.SimpleEntry;

import java.util.*;

public class ClustreamListener {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);
    static Clustream cluStream;
    static int cluster_number;
    static long agglomerative_clustering_time_tracker;
    static long clustream_clustering_time_tracker;
    static InstancesHeader cluStreamHeader;
    private static final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
    private static final List<AbstractMap.SimpleEntry<Integer, Instance>> unassignedBuffer = new ArrayList<>();

    private static ClusTree ClusTree;

    static {
        cluStream = new Clustream();
        cluster_number = 0;
        clustream_clustering_time_tracker = 0;
        agglomerative_clustering_time_tracker = 0;
        cluStream.prepareForUse(); // 🔥
        cluStream.maxNumKernelsOption.setValue(7);
        cluStream.resetLearningImpl();

        ClusTree = new ClusTree();
        ClusTree.prepareForUse(); // Initialize with defaults or tune parameters

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


    public static void add_listener_with_Agglomerative_clustering(EPStatement statement, UpdateListener listener){
        // EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener((newData, oldData, stmt1, rt) -> {
                if (newData != null) {
                    List<double[]> featureList = new ArrayList<>();
                    List<Integer> idList = new ArrayList<>();
                    long start = System.nanoTime();
                    for (EventBean e : newData) {
                        List<Float> feature = (List<Float>) e.get("features");
                        Integer id = (Integer) e.get("UNum");

                        featureList.add(feature.stream().mapToDouble(Float::doubleValue).toArray());
                        idList.add(id);
                    }

                    double[][] features = featureList.toArray(new double[0][]);
                    double[][] distanceMatrix = computeCosineDistanceMatrix(features);

                    Linkage linkage = new SingleLinkage(distanceMatrix);
                    // Perform hierarchical clustering
                    HierarchicalClustering hc = HierarchicalClustering.fit(linkage);
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    agglomerative_clustering_time_tracker += durationMs;
                    System.out.println("Agglomerative Clustering Time: " + agglomerative_clustering_time_tracker + " ms");
                    int[] clusterLabels = hc.partition(0.1); // threshold distance

                    // Output the cluster assignments
                    for (int i = 0; i < clusterLabels.length; i++) {
                        System.out.printf("ID %d => Cluster %d\n", idList.get(i), clusterLabels[i]);
                    }
                }
            });
        } else {
            log.error("Statement not found: 'out'");
        }
    }


    public static void add_listener_with_clu_clustering(EPStatement statement, UpdateListener listener){
        // EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener((newData, oldData, stat, rt) -> {
                if (newData != null) {
                    // Initialize header once
                    if (cluStreamHeader == null) {
                        List<Float> firstFeature = (List<Float>) newData[0].get("features");
                        cluStreamHeader = createHeader(firstFeature.size());
                        cluStream.setModelContext(cluStreamHeader);
                    }
                    for (EventBean e : newData) {

                        List<Float> featureList = (List<Float>) e.get("features");
                        Integer id = (Integer) e.get("UNum");  // or whatever your ID field is

//                        System.out.println("CluStream Data ID: " + id);
                        // Convert to DenseInstance
                        Instance instance = convertFeatureToInstance(featureList, cluStreamHeader);

                        // Optional: set a timestamp if you have one
                        // instance.setDataset(dataset);  // If needed by your Clustream setup

                        long start = System.nanoTime();
                        // Pass to Clustream
                        cluStream.trainOnInstance(instance);
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        clustream_clustering_time_tracker += durationMs;

                        Clustering clustering = cluStream.getMicroClusteringResult();
                        int assignedCluster = getNearestCluster(clustering, instance);

                        // Try to get clusters (might be null if not enough data)
                        String name = cluStream.getName();
                        System.out.println("name" + name);

                        if (assignedCluster == -1) {
                            unassignedBuffer.add(new SimpleEntry<>(id, instance));
                        } else {
                            // Track assigned
                            clusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(id);
                        }

                        if ((clustering == null) || (clustering.getClustering().isEmpty())) {
                            System.out.println(("Initializing id number " + id + " as cluster " + cluster_number));
                            cluster_number++;
//                            System.out.println("Clustering not yet available. Waiting for more data...");
                            continue;
                        }

                        if (cluStream.getMicroClusteringResult().size() > 0 && !unassignedBuffer.isEmpty()) {
                            List<SimpleEntry<Integer, Instance>> reassigned = new ArrayList<>();

                            for (SimpleEntry<Integer, Instance> entry : unassignedBuffer) {
                                int reassignedCluster = getNearestCluster(clustering, entry.getValue());
                                if (reassignedCluster != -1) {
                                    clusterToDataIds
                                            .computeIfAbsent(reassignedCluster, k -> new ArrayList<>())
                                            .add(entry.getKey());
                                    reassigned.add(entry);
                                }
                            }

                            // Remove reassigned ones from buffer
                            unassignedBuffer.removeAll(reassigned);
                        }
//                        printClusters();

                        System.out.println("Data ID: " + id + " assigned to cluster: " + assignedCluster);

                        // 🔽 Print clusters
                        for (moa.cluster.Cluster cluster : clustering.getClustering()) {
                            double[] center = cluster.getCenter();
                            double weight = cluster.getWeight();

                            System.out.println("Cluster center: " + Arrays.toString(center));
                            System.out.println("Cluster weight: " + weight);
                        }

                    }
                    System.out.println("CluStream Clustering Time: " + clustream_clustering_time_tracker + " ms");
                }
            });
        } else {
            log.error("Statement not found: 'out'");
        }
    }

    public static void add_listener_with_ClusTree(EPStatement statement, UpdateListener listener) {
        if (statement != null) {
            statement.addListener((newData, oldData, stmt1, rt) -> {
                if (newData != null) {
                    for (EventBean e : newData) {
                        List<Float> firstFeature = (List<Float>) newData[0].get("features");
                        InstancesHeader header = createHeader(firstFeature.size());
                        ClusTree.setModelContext(header);

                        List<Float> feature = (List<Float>) e.get("features");
                        Integer id = (Integer) e.get("UNum");

                        Instance instance = convertFeatureToInstance(feature, header);

                        // Feed to ClusTree
                        ClusTree.trainOnInstance(instance);

                        // Optional: get microclusters and assign point
                        Clustering microClusters = ClusTree.getMicroClusteringResult();
                        int assignedCluster = getNearestCluster(microClusters, instance);

                        System.out.println("ClusTree Data ID: " + id + " -> Cluster: " + assignedCluster);
                    }
                }
            });
        }
    }

    private static double[] normalize(double[] vec) {
        double norm = Math.sqrt(Arrays.stream(vec).map(x -> x * x).sum());
        return Arrays.stream(vec).map(x -> x / norm).toArray();
    }


    public static void printClusters() {
        System.out.println("==== Current Clusters ====");
        for (Map.Entry<Integer, List<Integer>> entry : clusterToDataIds.entrySet()) {
            int clusterId = entry.getKey();
            List<Integer> dataIds = entry.getValue();
            System.out.println("Cluster " + clusterId + ": " + dataIds);
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
