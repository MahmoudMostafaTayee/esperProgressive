package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.helpers.ClusterAssociator;
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

import com.espertech.esper.example.IOT.helpers.ErrorCode;
import static java.lang.System.exit;

public class CluStreamClusterer {
    private static final Logger logger = LoggerFactory.getLogger(CluStreamClusterer.class);

    private final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
    private InstancesHeader cluStreamHeader;
    private long clustream_clustering_time_tracker = 0;
    private int numberOfClusters = 1;
    private int windowNumber = 0;
    private Map<Integer, List<List<Float>>> PrevClusterIdsToFeatures = null;
    private Map<Integer, List<List<Float>>> CurrentClusterIdsToFeatures = null;

    public CluStreamClusterer() {

    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            List<AbstractMap.SimpleEntry<Integer, AbstractMap.SimpleEntry<Integer, Instance>>> windowInstancesBuffer = new ArrayList<>();

            numberOfClusters = 1;

            /* Initialize CluStream */
            Clustream cluStream = new Clustream();
            cluStream.prepareForUse();
//            cluStream.maxNumKernelsOption.setValue(numClusters);
            cluStream.resetLearningImpl();

            /* Initialize CluStream Header */
            if (cluStreamHeader == null) {
                List<Float> firstFeature = (List<Float>) newEvents[0].get("features");
                cluStreamHeader = ClustersUtils.createHeader(firstFeature.size());
                cluStream.setModelContext(cluStreamHeader);
            }
            else {
                cluStream.setModelContext(cluStreamHeader);
            }

            HashMap<Integer, List<Float>> currentFeaturesPerSerial = new HashMap<>();
            logger.error("New Events: {}", newEvents.length);
            windowNumber++;
            int __windowNumber = windowNumber;
            for (EventBean e : newEvents) {
                boolean isOverlapping = (boolean) e.get("isOverlapping");
                Integer serial = (Integer) e.get("UNum");
                Integer curFrame = (Integer) e.get("curFrame");
                Long timestamp = (Long) e.get("timestamp");
                System.out.println("Serial: " + serial + ", Frame: " + curFrame  + ", windowNumber: " + __windowNumber + ", timestamp: " + timestamp + ", isOverlapping: " + isOverlapping);

                if (isOverlapping) {
                    /* Skip overlapping events
                    (Events that have overlap with previous events
                        - Previously processed events-)*/
                    continue;
//                    break; // I think break could be used in case the events are sorted by timestamp.
                }
                List<Float> featureList = (List<Float>) e.get("features");
                Long frameRecordCount = (Long) e.get("frameRecordCount");
                if(numberOfClusters < frameRecordCount){
                    numberOfClusters = frameRecordCount.intValue();
                }

                currentFeaturesPerSerial.computeIfAbsent(serial, k -> new ArrayList<>()).addAll(featureList);

                Instance instance = ClustersUtils.convertFeatureToInstance(featureList, cluStreamHeader);
                long start = System.nanoTime();
                cluStream.trainOnInstance(instance);
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                clustream_clustering_time_tracker += durationMs;

                // Store all instances for getting their assigned clusters after finishing clustering for each time window.
                windowInstancesBuffer.add(new AbstractMap.SimpleEntry<>(
                        serial,
                        new AbstractMap.SimpleEntry<>(curFrame, instance)
                ));

            }
            // Step 1: Get micro-clustering result
            Clustering microClusters = cluStream.getMicroClusteringResult();
            if(microClusters == null || microClusters.getClustering().isEmpty()){
                // This means the clustering process has failed.
                logger.error("CluStream Clustering Failed");
                exit(ErrorCode.CLUSTERING_HAS_FAILED.getCode());
            }

            // Step 2: Extract list of clusters from micro-clustering
            List<? extends Cluster> microClusterList = microClusters.getClustering();

            // Step 3: Apply k-Means on the extracted list
            Clustering macroClusters = Clustream.kMeans(numberOfClusters+1, microClusterList);
            logger.debug("Actual Clusters Found: {}", macroClusters.size());

            logger.debug("Processing to which clusters each instance has been assigned");
            getAssignedClustersPerWindow(macroClusters, currentFeaturesPerSerial, windowInstancesBuffer);

            logger.info("CluStream Clustering Time: {} ms", clustream_clustering_time_tracker);
        }
    }

    private void getAssignedClustersPerWindow(Clustering clustering,
                                              HashMap<Integer, List<Float>> currentFeaturesPerSerial,
                                              List<AbstractMap.SimpleEntry<Integer, AbstractMap.SimpleEntry<Integer, Instance>>> windowInstancesBuffer) {
        Map<Integer, List<Integer>> localClusterToDataIds           = new HashMap<>();
        Map<Integer, List<Integer>> localClusterToFrames            = new HashMap<>();
        CurrentClusterIdsToFeatures = new HashMap<>();

        for (AbstractMap.SimpleEntry<Integer, AbstractMap.SimpleEntry<Integer, Instance>> buffered : windowInstancesBuffer) {
            Integer serial = buffered.getKey(); // Outer key = ID
            AbstractMap.SimpleEntry<Integer, Instance> innerEntry = buffered.getValue();

            Integer frameNumber = innerEntry.getKey(); // Inner key = frame number
            Instance instance = innerEntry.getValue();

            int assignedCluster = ClustersUtils.getNearestCluster(clustering, instance);
            List<Float> featureVector = currentFeaturesPerSerial.get(serial);

            localClusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(serial);
            localClusterToFrames.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(frameNumber);
            CurrentClusterIdsToFeatures.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(featureVector);

            // Optional debug logging
            logger.debug("ID: {}, Frame: {}, Assigned Cluster: {}", serial, frameNumber, assignedCluster);
        }

        if(PrevClusterIdsToFeatures != null){
            /*Here all the logic for similarity and centrality goes to generate a unique id per user.*/
            clusterAssociator();
        }

        PrevClusterIdsToFeatures = CurrentClusterIdsToFeatures;

        for (Map.Entry<Integer, List<Integer>> entry : localClusterToDataIds.entrySet()) {
            logger.info("Cluster {} has data ids: {}", entry.getKey(), entry.getValue());
        }

        for (Map.Entry<Integer, List<Integer>> entry : localClusterToFrames.entrySet()) {
            logger.info("Cluster {} has frame numbers: {}", entry.getKey(), entry.getValue());
        }

//        tracker.printAllTracks();

//        exit(0);
    }

    private void clusterAssociator(){
        // Initialize with parameters from paper (ε = 0.3)
        ClusterAssociator associator = new ClusterAssociator(0.3f);

        // Get associations
        Map<Integer, Integer> associations =
                associator.associateClusters(PrevClusterIdsToFeatures, CurrentClusterIdsToFeatures);

        System.out.println("Cluster Mappings (Previous):");
        for (Map.Entry<Integer, List<List<Float>>> entry : PrevClusterIdsToFeatures.entrySet()) {
            Integer clusterId = entry.getKey();
            List<List<Float>> featuresList = entry.getValue();

            System.out.println("Cluster ID: " + clusterId);
            System.out.println("featuresList.size(): " + featuresList.size());
        }


        System.out.println("Cluster Mappings (Current):");
        for (Map.Entry<Integer, List<List<Float>>> entry : CurrentClusterIdsToFeatures.entrySet()) {
            Integer clusterId = entry.getKey();
            List<List<Float>> featuresList = entry.getValue();

            System.out.println("Cluster ID: " + clusterId);
            System.out.println("featuresList.size(): " + featuresList.size());

        }


        // Print the mappings
        System.out.println("Cluster Mappings (Previous → Current):");
        for (Map.Entry<Integer, Integer> entry : associations.entrySet()) {
            System.out.printf("  Cluster %d → Cluster %d%n", entry.getKey(), entry.getValue());
        }
    }

    public UpdateListener getListener(){
        return (newEvents, oldEvents,  statement,  runtime) ->
        {
            processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
        };
    }
}
