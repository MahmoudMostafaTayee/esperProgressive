package com.espertech.esper.example.IOT.clusterers;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.example.IOT.utils.ClustersUtils;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;
import com.yahoo.labs.samoa.instances.Instance;
import moa.cluster.Clustering;

import java.util.List;

public class ClusTreeClusterer {
    private final moa.clusterers.clustree.ClusTree clusTree = new moa.clusterers.clustree.ClusTree();

    public ClusTreeClusterer() {
        clusTree.prepareForUse();
    }

    private void processStreamingClusters(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement, EPRuntime runtime) {
        if (newEvents != null) {
            for (EventBean e : newEvents) {
                List<Float> feature = (List<Float>) e.get("features");
                Integer id = (Integer) e.get("UNum");
                Instance instance = ClustersUtils.convertFeatureToInstance(feature, ClustersUtils.createHeader(feature.size()));
                clusTree.trainOnInstance(instance);
                Clustering microClusters = clusTree.getMicroClusteringResult();
                int assignedCluster = ClustersUtils.getNearestCluster(microClusters, instance);
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