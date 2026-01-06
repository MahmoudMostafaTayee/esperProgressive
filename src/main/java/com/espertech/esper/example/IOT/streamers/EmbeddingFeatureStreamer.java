// EmbeddingFeatureStreamer.java (Modified)
package com.espertech.esper.example.IOT.streamers;

import com.espertech.esper.example.IOT.helpers.TrackingParameters;
import com.espertech.esper.example.IOT.utils.DetectedUser;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddingFeatureStreamer {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingFeatureStreamer.class);

    private static final String BASE_PATH = TrackingParameters.FEATURES_BASE_DIR;
    private static final Pattern FILE_PATTERN = Pattern
            .compile("feature_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+\\.?\\d*)\\.npy");
    private static final long ONE_SEC_TIME_STEP = 1000L;
    private static final Map<Path, Integer> cameraOffsets = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void streamEmbeddingFeatures() {
        Map<Path, Map<Path, List<Path>>> sceneData = initScenesData(Paths.get(BASE_PATH));
        long frameIntervalMillis = (long) ((ONE_SEC_TIME_STEP * 1.0) / TrackingParameters.fps);

        scheduler.scheduleWithFixedDelay(() -> {
            boolean allFinished = true;
            for (Map.Entry<Path, Map<Path, List<Path>>> sceneEntry : sceneData.entrySet()) {
                Path scene = sceneEntry.getKey();
                if (!Files.isDirectory(scene))
                    continue;
                boolean sceneHasMore = processScene(sceneEntry);
                if (sceneHasMore)
                    allFinished = false;
            }
            if (allFinished) {
                logger.info("All files in all scenes processed. Shutting down streamer.");
                scheduler.shutdown();
            }
        }, 0, frameIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private static boolean processScene(Map.Entry<Path, Map<Path, List<Path>>> sceneEntry) {
        Path scene = sceneEntry.getKey();
        Map<Path, List<Path>> cameras = sceneEntry.getValue();
        Map<Path, Boolean> processingStatus = new HashMap<>();
        boolean sceneHasMore = false;

        Map<Path, List<Path>> selectedCameras = new HashMap<>();

        String selectedCamera = TrackingParameters.CAMERA_FILTER;

        for (Map.Entry<Path, List<Path>> entry : cameras.entrySet()) {
            Path camera = entry.getKey();

            if (!Files.isDirectory(camera))
                continue;

            if (!selectedCamera.equalsIgnoreCase("all")) {
                if (!camera.getFileName().toString()
                        .equals("camera_" + selectedCamera)) {
                    continue;
                }
            }

            selectedCameras.put(camera, entry.getValue());
        }

        for (Map.Entry<Path, List<Path>> cameraEntry : selectedCameras.entrySet()) {
            Path camera = cameraEntry.getKey();
            processingStatus.put(camera, true);
        }

        for (Map.Entry<Path, List<Path>> cameraEntry : selectedCameras.entrySet()) {
            Path camera = cameraEntry.getKey();
            if (!processingStatus.get(camera))
                continue;
            boolean cameraHasMoreFiles = processCamera(scene, cameraEntry);
            if (cameraHasMoreFiles)
                sceneHasMore = true;
            processingStatus.put(camera, cameraHasMoreFiles);
        }
        return sceneHasMore;
    }

    private static boolean processCamera(Path scene, Map.Entry<Path, List<Path>> cameraEntry) {
        Path camera = cameraEntry.getKey();
        List<Path> files = cameraEntry.getValue();
        int startIndex = cameraOffsets.getOrDefault(camera, 0);

        if (startIndex >= files.size()) {
            System.out.println("All files processed for camera: " + camera);
            return false;
        }

        List<FileData> frameFiles = new ArrayList<>();
        int currentFrame = -1;
        int fileIndex = startIndex;

        while (fileIndex < files.size()) {
            Path entry = files.get(fileIndex);
            String fileName = entry.getFileName().toString();
            ParsedFileInfo parsedFile = parseFileName(fileName);

            if (parsedFile == null) {
                System.err.println("Skipping file (invalid format): " + fileName);
                fileIndex++;
                continue;
            }

            if (currentFrame == -1) {
                currentFrame = parsedFile.curFrame;
            }

            if (parsedFile.curFrame != currentFrame) {
                break;
            }

            frameFiles.add(new FileData(entry, parsedFile));
            fileIndex++;
        }

        if (!frameFiles.isEmpty()) {
            processFrame(scene, camera, frameFiles, currentFrame);
        }

        cameraOffsets.put(camera, fileIndex);
        return fileIndex < files.size();
    }

    private static void processFrame(Path scene, Path camera, List<FileData> frameFiles, int frameNumber) {
        long timestamp = System.currentTimeMillis();
        List<DetectedUser> detectedUsers = new ArrayList<>();

        for (FileData fileData : frameFiles) {
            try {
                File npyFile = fileData.path.toFile();
                INDArray data = Nd4j.createFromNpyFile(npyFile);
                List<Float> featureList = convertToFloatList(data.toFloatVector());

                ParsedFileInfo parsed = fileData.parsedInfo;

                DetectedUser user = new DetectedUser(
                        featureList,
                        parsed.uNum,
                        parsed.x1,
                        parsed.x2,
                        parsed.y1,
                        parsed.y2,
                        parsed.conf);

                detectedUsers.add(user);

                logger.debug("Added user {} to frame {}", parsed.uNum, parsed.curFrame);
            } catch (Exception e) {
                logger.error("Error processing file: {} - {}", fileData.path.getFileName(), e.getMessage());
                e.printStackTrace();
            }
        }

        if (!detectedUsers.isEmpty()) {
            EmbeddingFeature frameFeature = new EmbeddingFeature(timestamp, frameNumber, detectedUsers);
            EventEPLUtil.streamEvent(
                    frameFeature,
                    "embeddingFeature" + "_" + camera.getFileName().toString());
            logger.info("Streamed frame {} with {} users from camera {}",
                    frameNumber, detectedUsers.size(), camera.getFileName());
        }
    }

    public static Map<Path, Map<Path, List<Path>>> initScenesData(Path basePath) {
        Map<Path, Map<Path, List<Path>>> result = new HashMap<>();
        try {
            List<Path> scenes = HelperUtils.getSortedDirectories(basePath);
            for (Path scene : scenes) {
                Map<Path, List<Path>> cameraMap = new HashMap<>();
                List<Path> cameras = HelperUtils.getSortedDirectories(scene);
                for (Path camera : cameras) {
                    List<Path> files = HelperUtils.getSortedFiles(camera, "*.npy");
                    cameraMap.put(camera, files);
                }
                result.put(scene, cameraMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static List<Float> convertToFloatList(float[] featureArray) {
        List<Float> featureList = new ArrayList<>(featureArray.length);
        for (float value : featureArray)
            featureList.add(value);
        return featureList;
    }

    private static ParsedFileInfo parseFileName(String fileName) {
        Matcher matcher = FILE_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            return null;
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

    private static class FileData {
        Path path;
        ParsedFileInfo parsedInfo;

        FileData(Path path, ParsedFileInfo parsedInfo) {
            this.path = path;
            this.parsedInfo = parsedInfo;
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