package com.espertech.esper.example.IOT.utils;

import com.espertech.esper.example.IOT.generators.WildTrackDatasetGenerator;
import com.espertech.esper.example.IOT.generators.EmbeddingFeatureGenerator;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.example.IOT.generators.DeviceCommandStreamer;

public class IotStreamGenerator {

    public void generateEvents(EPRuntime runtime) {
//        DeviceCommandStreamer.streamDeviceCommands(runtime);
//        WildTrackDatasetGenerator.streamWildTrackDataset(runtime);
        EmbeddingFeatureGenerator.streamEmbeddingFeatures(runtime);
    }}
