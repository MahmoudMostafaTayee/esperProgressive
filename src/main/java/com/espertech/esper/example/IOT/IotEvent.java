package com.espertech.esper.example.IOT;

public class IotEvent {
    private int amount;
    private String id;
    private String type;
    private long dateCreated;

    public IotEvent(int amount, String id, String type, long dateCreated) {
        this.amount = amount;
        this.id = id;
        this.type = type;
        this.dateCreated = dateCreated;
    }

    public int getAmount() {
        return amount;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getDateCreated() {
        return dateCreated;
    }
}
