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

import com.espertech.esper.example.IOT.helpers.ErrorCode;
import static java.lang.System.exit;

public class CluStreamClusterer {
    private static final Logger logger = LoggerFactory.getLogger(ClustersUtils.class);

    private final Clustream cluStream = new Clustream();
    private final Map<Integer, List<Integer>> clusterToDataIds = new HashMap<>();
    private InstancesHeader cluStreamHeader;
    private long clustream_clustering_time_tracker = 0;
    private int numberOfClusters = 1;

    public CluStreamClusterer() {
        cluStream.prepareForUse();
//            cluStream.maxNumKernelsOption.setValue(numClusters);
        cluStream.resetLearningImpl();
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            List<AbstractMap.SimpleEntry<Integer, Instance>> windowInstancesBuffer = new ArrayList<>();
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

                Instance instance = ClustersUtils.convertFeatureToInstance(featureList, cluStreamHeader);
                long start = System.nanoTime();
                cluStream.trainOnInstance(instance);
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                clustream_clustering_time_tracker += durationMs;

                // Store all instances for getting their assigned clusters after finishing clustering for each time window.
                windowInstancesBuffer.add(new AbstractMap.SimpleEntry<>(id, instance));

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
            Clustering macroClusters = Clustream.kMeans(numberOfClusters, microClusterList);
            logger.debug("Actual Clusters Found: " + macroClusters.size());

            logger.debug("Processing to which clusters each instance has been assigned");
            getAssignedClustersPerWindow(macroClusters, clusterToDataIds, windowInstancesBuffer);

            logger.info("CluStream Clustering Time: " + clustream_clustering_time_tracker + " ms");
        }
    }

    private void getAssignedClustersPerWindow(Clustering clustering,
                                              Map<Integer, List<Integer>> localClusterToDataIds,
                                              List<AbstractMap.SimpleEntry<Integer, Instance>> windowInstancesBuffer) {
        for (AbstractMap.SimpleEntry<Integer, Instance> buffered : windowInstancesBuffer) {
            int assignedCluster = ClustersUtils.getNearestCluster(clustering, buffered.getValue());
            localClusterToDataIds.computeIfAbsent(assignedCluster, k -> new ArrayList<>()).add(buffered.getKey());
        }
        windowInstancesBuffer.clear();
        for (Map.Entry<Integer, List<Integer>> entry : localClusterToDataIds.entrySet()) {
            logger.info("Cluster " + entry.getKey() + " has data ids: " + entry.getValue());
        }
    }

    public UpdateListener getListener(){
        return (newEvents, oldEvents,  statement,  runtime) ->
        {
            processStreamingClusters(newEvents, oldEvents,  statement,  runtime);
        };
    }
}
