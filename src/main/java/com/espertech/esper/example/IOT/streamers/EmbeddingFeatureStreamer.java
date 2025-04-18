package com.espertech.esper.example.IOT.streamers;

import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.example.IOT.streams.EmbeddingFeature;
import com.espertech.esper.example.IOT.helpers.HelperUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddingFeatureStreamer {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingFeatureStreamer.class);

    private static final String BASE_PATH = "/home/mahmoud-tayee/Masters/AIC24_Track1_YACHIYO_RIIPS/EmbedFeature";
    private static final Pattern FILE_PATTERN = Pattern.compile("feature_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+\\.?\\d*)\\.npy");
    private static final long ONE_SEC_TIME_STEP = 1000L;
    private static final Map<Path, Integer> cameraOffsets = new HashMap<>();

    public static void streamEmbeddingFeatures() {
        try {
            List<Path> scenes = HelperUtils.getSortedDirectories(Paths.get(BASE_PATH));
            for (Path scene : scenes) {
                if (!Files.isDirectory(scene)) continue;
                processScene(scene);
            }
        } catch (IOException e) {
            System.err.println("Error accessing base directory: " + BASE_PATH + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processScene(Path scene) {
        int framesPerWindow = TrackingParameters.timePeriod * TrackingParameters.fps;
        try {
            List<Path> cameras = HelperUtils.getSortedDirectories(scene);
            Map<Path, Boolean> processingStatus = new HashMap<>();

            // Initialize processing status for each camera
            for (Path camera : cameras) {
                processingStatus.put(camera, true);
            }

            boolean hasMoreFiles;
            do{
                for (Path camera : cameras) {
                    if (!camera.toString().equals("/home/mahmoud-tayee/Masters/AIC24_Track1_YACHIYO_RIIPS/EmbedFeature/scene_001/camera_0001"))
                        continue;
                    if (!Files.isDirectory(camera)) continue;
                    if (!processingStatus.get(camera)) continue; // Skip if already processed.
                    boolean cameraHasMoreFiles = processCamera(scene, camera, framesPerWindow);

                    // Update processing status
                    processingStatus.put(camera, cameraHasMoreFiles);
                }
//                EventEPLUtil.advanceTime(TrackingParameters.timePeriod * ONE_SEC_TIME_STEP);

                // Check if any camera still has files left to process
                hasMoreFiles = processingStatus.values().stream().anyMatch(status -> status);
            } while (hasMoreFiles); // Continue until all cameras are fully processed
        } catch (IOException e) {
            System.err.println("Error accessing scene directory: " + scene + " - " + e.getMessage());
        }
    }

    private static boolean processCamera(Path scene, Path camera, int framesPerWindow) {
        try {
            List<Path> files = HelperUtils.getSortedFiles(camera, "*.npy");

            // Get the last processed index for this camera, or start at 0
            int startIndex = cameraOffsets.getOrDefault(camera, 0); // 10704 is the index to start from the 1000th frame.

            int curFrame = -1;
            int fileIndex = startIndex;
            ParsedFileInfo parsedFile;
            for (int i = 0; i < framesPerWindow; i++) {
                do{
                    if (fileIndex >= files.size()) {
                        cameraOffsets.put(camera, fileIndex + startIndex);
                        System.out.println("All files processed for camera: " + camera);
                        return false; // No more files to process
                    }

                    Path entry = files.get(fileIndex++);
                    String fileName = entry.getFileName().toString();
                    parsedFile = parseFileName(fileName);

                    if (parsedFile == null) {
                        System.err.println("Skipping file (invalid format): " + fileName);
                        continue;
                    }

                    if (curFrame == -1) {
                        curFrame = parsedFile.curFrame;
                    }

                    if(curFrame == parsedFile.curFrame) {
                        processFile(scene, camera, entry, parsedFile, curFrame);
                    }
                    else {
//                        logger.info("curFrame: {}", curFrame);
//                        logger.info("Old time: {}", EventEPLUtil.getCurrentTime());
                        EventEPLUtil.advanceTime((1.0)/TrackingParameters.fps);
//                        logger.info("New time: {}", EventEPLUtil.getCurrentTime());
                        logger.debug("End of frame");
                        break;
                    }
                }while(true);
                fileIndex--;
                curFrame = -1;
            }
            // Update offset for next batch
            cameraOffsets.put(camera, fileIndex);
            return (fileIndex) < files.size(); // Returns true if more files are left

        } catch (IOException e) {
            logger.error("Error accessing camera directory: " + camera + " - " + e.getMessage());
            return false;
        }
    }

    private static void processFile(Path scene, Path camera, Path entry, ParsedFileInfo parsedFile, int prevFrame) {
        String fileName = entry.getFileName().toString();

        try {
            File npyFile = entry.toFile();
            INDArray data = Nd4j.createFromNpyFile(npyFile);
            List<Float> featureList = convertToFloatList(data.toFloatVector());

            logger.debug("Processed: {}", npyFile);
            EventEPLUtil.streamEvent(
                    new EmbeddingFeature(EventEPLUtil.getCurrentTime(),
                            featureList,
                            parsedFile.curFrame,
                            parsedFile.uNum,
                            parsedFile.x1,
                            parsedFile.x2,
                            parsedFile.y1,
                            parsedFile.y2,
                            parsedFile.conf
                    ),
                    "embeddingFeature" + "_" + camera.getFileName().toString()
            );
        } catch (Exception e) {
            logger.error("Error processing file: {} - {}", fileName, e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Float> convertToFloatList(float[] featureArray) {
        List<Float> featureList = new ArrayList<>(featureArray.length);
        for (float value : featureArray) featureList.add(value);
        return featureList;
    }

    private static ParsedFileInfo parseFileName(String fileName) {
        Matcher matcher = FILE_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            return null; // Invalid format
        }

        try {
            int curFrame = Integer.parseInt(matcher.group(1));
            int uNum = Integer.parseInt(matcher.group(2));
            int x1 = Integer.parseInt(matcher.group(3));
            int x2 = Integer.parseInt(matcher.group(4));
            int y1 = Integer.parseInt(matcher.group(5));
            int y2 = Integer.parseInt(matcher.group(6));
            float conf = Float.parseFloat(matcher.group(7));

            return new ParsedFileInfo(curFrame, uNum, x1, x2, y1, y2, conf);
        } catch (NumberFormatException e) {
            logger.error("Error parsing filename: {} - {}", fileName, e.getMessage());
            return null;
        }
    }

    private static class ParsedFileInfo {
        int curFrame, uNum, x1, x2, y1, y2;
        float conf;

        ParsedFileInfo(int curFrame, int uNum, int x1, int x2, int y1, int y2, float conf) {
            this.curFrame = curFrame;
            this.uNum = uNum;
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.conf = conf;
        }
    }

}
