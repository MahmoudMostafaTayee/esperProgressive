package com.espertech.esper.example.IOT.DeviceCommand;

public class DeviceCommand {
    private String deviceId;
    private String command;
    private String parameters;
    private long timestamp;

    public DeviceCommand(String deviceId, String command, String parameters, long timestamp) {
        this.command = command;
        this.deviceId = deviceId;
        this.parameters = parameters;
        this.timestamp = timestamp;
    }
    
        public String getDeviceId() {
            return deviceId;
        }

    public String getCommand() {
        return command;
    }

    public String getParameters() {
        return parameters;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
