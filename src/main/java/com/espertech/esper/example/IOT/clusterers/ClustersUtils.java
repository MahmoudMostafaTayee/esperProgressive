package com.espertech.esper.example.IOT.clusterers;

import com.yahoo.labs.samoa.instances.*;
import moa.cluster.Cluster;
import moa.cluster.Clustering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class ClustersUtils {
    private static final Logger logger = LoggerFactory.getLogger(ClustersUtils.class);

    private ClustersUtils() {
        /* Prevent instantiation */
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public static int getNearestCluster(Clustering clustering, Instance instance) {
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

    public static InstancesHeader createHeader(int numAttributes) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("timestamp"));  // Must match position

        for (int i = 0; i < numAttributes; i++) {
            attributes.add(new Attribute("attr" + i));
        }

        Instances dataset = new Instances("feature_stream", attributes, 0);
        return new InstancesHeader(dataset);
    }

    public static Instance convertFeatureToInstance(List<Float> featureVec, InstancesHeader header) {
        double[] values = featureVec.stream().mapToDouble(Float::doubleValue).toArray();
        Instance instance = new DenseInstance(1.0, values);
        instance.setDataset(header);
        return instance;
    }

    public static Instance convertFeatureToInstance(double[] values, InstancesHeader header) {
        Instance instance = new DenseInstance(1.0, values);
        instance.setDataset(header);
        return instance;
    }
}
