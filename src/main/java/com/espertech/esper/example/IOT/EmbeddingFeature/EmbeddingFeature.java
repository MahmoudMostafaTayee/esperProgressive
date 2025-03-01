package com.espertech.esper.example.IOT.EmbeddingFeature;

import java.util.List;

public class EmbeddingFeature {
    private long timestamp;
    private List<Float> features;

    public EmbeddingFeature(long timestamp, List<Float> features) {
        this.timestamp = timestamp;
        this.features = features;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Float> getFeatures() {
        return features;
    }

    /**
     * Provides a human-readable string representation of this object.
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "EmbeddingFeature{" +
                "timestamp=" + timestamp +
                ", features=" + features +  // Now prints readable list
                '}';
    }
}
