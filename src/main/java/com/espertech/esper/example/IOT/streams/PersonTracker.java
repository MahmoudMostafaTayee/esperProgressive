package com.espertech.esper.example.IOT.streams;

public class PersonTracker {
    private int personId;
//    private float[] features;
    private long timestamp;

    public PersonTracker(int personId, long timestamp) {
        this.personId = personId;
//        this.features = features;
        this.timestamp = timestamp;
    }

    public int getPersonId() {
        return personId;
    }

//    public float[] getFeatures() {
//        return features;
//    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PersonTracker{" +
                "personId=" + personId +
                ", timestamp=" + timestamp +
                '}';
    }
}

