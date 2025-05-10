package com.espertech.esper.example.IOT.streamers;

import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.example.IOT.streams.PersonView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class WildTrackDatasetStreamer {
    private static final String DIRECTORY_PATH = "/mnt/hdd1/Masters/Dataset/Wildtrack_dataset/annotations_positions";
    private static long timeTracker = System.currentTimeMillis();

    public static void streamWildTrackDataset() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(DIRECTORY_PATH), "*.json")) {
            for (Path entry : stream) {
                processFile(entry);
            }
        } catch (IOException e) {
            System.err.println("Error accessing directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processFile(Path entry) {
        try {
            String fileName = entry.getFileName().toString();
            int frameNumber = Integer.parseInt(fileName.replace(".json", ""));
            List<PersonView> personViews = readPersonViewsFromJson(entry.toString());
            for (PersonView personView : personViews) {
                personView.setFrameNumber(frameNumber);
                personView.setTimeStamp(timeTracker);
                EventEPLUtil.streamEvent(personView, "personView");
            }
            timeTracker = EventEPLUtil.advanceTime();
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + entry + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<PersonView> readPersonViewsFromJson(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), new TypeReference<List<PersonView>>() {});
    }
}

