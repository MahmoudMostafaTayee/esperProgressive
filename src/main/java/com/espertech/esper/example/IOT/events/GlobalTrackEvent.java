package com.espertech.esper.example.IOT.events;

import java.util.List;

public class GlobalTrackEvent {
    private int globalId;
    private List<LocalTrackEvent> constituentTracks;
    private long timestamp;

    public GlobalTrackEvent(int globalId, List<LocalTrackEvent> constituentTracks, long timestamp) {
        this.globalId = globalId;
        this.constituentTracks = constituentTracks;
        this.timestamp = timestamp;
    }

    public int getGlobalId() {
        return globalId;
    }

    public List<LocalTrackEvent> getConstituentTracks() {
        return constituentTracks;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "GlobalTrackEvent{" +
                "globalId=" + globalId +
                ", constituentTracks=" + (constituentTracks != null ? constituentTracks.size() : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}
