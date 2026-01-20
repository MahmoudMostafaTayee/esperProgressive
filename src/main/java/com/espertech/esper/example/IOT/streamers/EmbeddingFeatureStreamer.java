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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;

public class EmbeddingFeatureStreamer {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingFeatureStreamer.class);

    private static final String BASE_PATH = TrackingParameters.FEATURES_BASE_DIR;
    private static final Pattern FILE_PATTERN = Pattern
            .compile("feature_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+\\.?\\d*)\\.npy");
    private static final long ONE_SEC_TIME_STEP = 1000L;
    private static final Map<Path, Integer> cameraOffsets = new HashMap<>();
    private static final Map<Path, Map<Integer, List<PoseData>>> cameraPoseCache = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void streamEmbeddingFeatures() {
        Map<Path, Map<Path, List<Path>>> sceneData = initScenesData(Paths.get(BASE_PATH));
        long frameIntervalMillis = (long) ((ONE_SEC_TIME_STEP * 1.0) / TrackingParameters.fps);

        scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Path, Map<Path, List<Path>>> sceneEntry : sceneData.entrySet()) {
                Path scene = sceneEntry.getKey();
                if (!Files.isDirectory(scene))
                    continue;
                processScene(sceneEntry);
            }
        }, 0, frameIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private static void processScene(Map.Entry<Path, Map<Path, List<Path>>> sceneEntry) {
        Path scene = sceneEntry.getKey();
        Map<Path, List<Path>> cameras = sceneEntry.getValue();
        Map<Path, Boolean> processingStatus = new HashMap<>();

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

            processingStatus.put(camera, cameraHasMoreFiles);
        }
    }

    private static boolean processCamera(Path scene, Map.Entry<Path, List<Path>> cameraEntry) {
        Path camera = cameraEntry.getKey();
        List<Path> files = cameraEntry.getValue();

        // Load pose data for this camera if not already loaded
        if (!cameraPoseCache.containsKey(camera)) {
            loadPoseData(camera);
        }

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
                        parsed.conf,
                        getMatchingKeypoints(camera, parsed.curFrame, parsed.x1, parsed.y1, parsed.x2, parsed.y2));

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
            int uNum = Integer.parseInt(matcher.group(2)) - 1;
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

    private static void loadPoseData(Path cameraDir) {
        try {
            String cameraName = cameraDir.getFileName().toString();
            String sceneName = cameraDir.getParent().getFileName().toString();

            Path poseDir = Paths.get(BASE_PATH).getParent().resolve("Pose").resolve(sceneName).resolve(cameraName);
            Path jsonFile = poseDir.resolve(cameraName + "_out_keypoint.json");

            if (!Files.exists(jsonFile)) {
                logger.warn("Pose file not found: " + jsonFile);
                cameraPoseCache.put(cameraDir, new HashMap<>());
                return;
            }

            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, List<PoseData>>>() {
            }.getType();
            Map<String, List<PoseData>> rawData = gson.fromJson(new FileReader(jsonFile.toFile()), type);

            Map<Integer, List<PoseData>> frameData = new HashMap<>();
            for (Map.Entry<String, List<PoseData>> entry : rawData.entrySet()) {
                try {
                    int frame = Integer.parseInt(entry.getKey());
                    frameData.put(frame, entry.getValue());
                } catch (NumberFormatException e) {
                    // Ignore non-integer keys
                }
            }

            cameraPoseCache.put(cameraDir, frameData);
            logger.info("Loaded pose data for camera: " + cameraName + ", frames: " + frameData.size());

        } catch (Exception e) {
            logger.error("Error loading pose data for " + cameraDir, e);
            cameraPoseCache.put(cameraDir, new HashMap<>()); // Avoid retry loop
        }
    }

    private static List<List<Float>> getMatchingKeypoints(Path camera, int frame, int x1, int y1, int x2, int y2) {
        Map<Integer, List<PoseData>> framePoses = cameraPoseCache.get(camera);
        if (framePoses == null)
            return null;

        List<PoseData> poses = framePoses.get(frame);
        if (poses == null)
            return null;

        for (PoseData pose : poses) {
            if (isBBoxMatch(pose.bbox, x1, y1, x2, y2)) {
                return pose.keypoints;
            }
        }
        return null;
    }

    private static boolean isBBoxMatch(List<Float> bbox, int x1, int y1, int x2, int y2) {
        if (bbox == null || bbox.size() < 4)
            return false;
        // BBox format in JSON: [x1, y1, x2, y2, score] (floats)
        int bx1 = bbox.get(0).intValue();
        int by1 = bbox.get(1).intValue();
        int bx2 = bbox.get(2).intValue();
        int by2 = bbox.get(3).intValue();

        // Exact match as per integer casting
        return bx1 == x1 && by1 == y1 && bx2 == x2 && by2 == y2;
    }

    private static class PoseData {
        List<Float> bbox;
        List<List<Float>> keypoints;
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