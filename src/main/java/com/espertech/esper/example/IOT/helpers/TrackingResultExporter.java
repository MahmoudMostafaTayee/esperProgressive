package com.espertech.esper.example.IOT.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for exporting tracking results to JSON format for
 * visualization.
 */
public class TrackingResultExporter {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Represents a single detection with track ID and bounding box.
     */
    public static class Detection {
        public int track_id;
        public int[] bbox; // [x1, x2, y1, y2]

        public Detection(int trackId, int[] bbox) {
            this.track_id = trackId;
            this.bbox = bbox;
        }
    }

    /**
     * Represents tracking results for a single frame.
     */
    public static class FrameResult {
        public int frame;
        public List<Detection> detections;

        public FrameResult(int frame, List<Detection> detections) {
            this.frame = frame;
            this.detections = detections;
        }
    }

    /**
     * Exports tracking results for a single frame to JSON.
     * 
     * @param frameNumber   The frame number
     * @param trackIds      List of track IDs
     * @param boundingBoxes List of bounding boxes [x1, x2, y1, y2]
     * @param outputDir     Output directory path
     * @throws IOException If file writing fails
     */
    public static void exportFrameToJSON(
            int frameNumber,
            List<Integer> trackIds,
            List<Integer[]> boundingBoxes,
            String outputDir) throws IOException {

        // Create output directory if it doesn't exist
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Build detections list
        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < trackIds.size(); i++) {
            int trackId = trackIds.get(i);
            // Skip noise (-1)
            if (trackId == -1)
                continue;

            Integer[] bbox = boundingBoxes.get(i);
            int[] bboxArray = new int[] { bbox[0], bbox[1], bbox[2], bbox[3] };
            detections.add(new Detection(trackId, bboxArray));
        }

        // Create frame result
        FrameResult frameResult = new FrameResult(frameNumber, detections);

        // Write to JSON file
        String filename = String.format("%s/frame_%06d.json", outputDir, frameNumber);
        try (FileWriter writer = new FileWriter(filename)) {
            gson.toJson(frameResult, writer);
        }
    }

    /**
     * Exports tracking results for multiple frames to a single JSON file.
     * 
     * @param results    Map of frame number to (trackIds, boundingBoxes) pairs
     * @param outputPath Output file path
     * @throws IOException If file writing fails
     */
    public static void exportBatchToJSON(
            Map<Integer, Map<String, Object>> results,
            String outputPath) throws IOException {

        List<FrameResult> allFrames = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, Object>> entry : results.entrySet()) {
            int frameNumber = entry.getKey();
            Map<String, Object> data = entry.getValue();

            @SuppressWarnings("unchecked")
            List<Integer> trackIds = (List<Integer>) data.get("trackIds");
            @SuppressWarnings("unchecked")
            List<Integer[]> boundingBoxes = (List<Integer[]>) data.get("boundingBoxes");

            List<Detection> detections = new ArrayList<>();
            for (int i = 0; i < trackIds.size(); i++) {
                int trackId = trackIds.get(i);
                if (trackId == -1)
                    continue;

                Integer[] bbox = boundingBoxes.get(i);
                int[] bboxArray = new int[] { bbox[0], bbox[1], bbox[2], bbox[3] };
                detections.add(new Detection(trackId, bboxArray));
            }

            allFrames.add(new FrameResult(frameNumber, detections));
        }

        // Write to JSON file
        try (FileWriter writer = new FileWriter(outputPath)) {
            gson.toJson(allFrames, writer);
        }
    }
}
