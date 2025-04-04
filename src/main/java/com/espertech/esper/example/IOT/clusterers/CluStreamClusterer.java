package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import moa.cluster.Cluster;
import moa.cluster.Clustering;
import moa.clusterers.clustream.Clustream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CluStreamClusterer {
    private static final Logger logger = LoggerFactory.getLogger(ClustersUtils.class);

    private final Clustream cluStream = new Clustream();
    private final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
    private InstancesHeader cluStreamHeader;
    private long clustream_clustering_time_tracker = 0;
    private int cluster_number = 0;
    private int numberOfClusters = 1;

    // Buffer for instances during initialization
    private final List<AbstractMap.SimpleEntry<Integer, Instance>> initializationBuffer = new ArrayList<>();
    private boolean isInitialized = false;

    public CluStreamClusterer() {
        cluStream.prepareForUse();
//            cluStream.maxNumKernelsOption.setValue(numClusters);
        cluStream.resetLearningImpl();
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            if (cluStreamHeader == null) {
                List<Float> firstFeature = (List<Float>) newEvents[0].get("features");
                cluStreamHeader = ClustersUtils.createHeader(firstFeature.size());
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

                Instance instance = ClustersUtils.convertFeatureToInstance(featureList, cluStreamHeader);
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
                int assignedCluster = ClustersUtils.getNearestCluster(macroClusters, instance);
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
            int assignedCluster = ClustersUtils.getNearestCluster(clustering, buffered.getValue());
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
