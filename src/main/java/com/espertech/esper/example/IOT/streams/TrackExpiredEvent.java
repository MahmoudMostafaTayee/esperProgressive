package com.espertech.esper.example.IOT.streams;

public class TrackExpiredEvent {
    private int globalId;

    public TrackExpiredEvent(int globalId) {
        this.globalId = globalId;
    }

    public int getGlobalId() {
        return globalId;
    }

    public void setGlobalId(int globalId) {
        this.globalId = globalId;
    }
}
