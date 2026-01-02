// EmbeddingFeature.java (Modified)
package com.espertech.esper.example.IOT.streams;

import com.espertech.esper.example.IOT.utils.DetectedUser;

import java.util.List;

public class EmbeddingFeature {
    private long timestamp;
    private int curFrame;
    private List<DetectedUser> detectedUsers;

    public EmbeddingFeature(long timestamp, int curFrame, List<DetectedUser> detectedUsers) {
        this.timestamp = timestamp;
        this.curFrame = curFrame;
        this.detectedUsers = detectedUsers;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getCurFrame() {
        return curFrame;
    }

    public List<DetectedUser> getDetectedUsers() {
        return detectedUsers;
    }

    public int getUserCount() {
        return detectedUsers != null ? detectedUsers.size() : 0;
    }

    @Override
    public String toString() {
        return "EmbeddingFeature{" +
                "timestamp=" + timestamp +
                ", curFrame=" + curFrame +
                ", userCount=" + getUserCount() +
                ", users=" + detectedUsers +
                '}';
    }
}