package com.espertech.esper.example.IOT.streamers;

import com.espertech.esper.example.IOT.utils.EventEPLUtil;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.example.IOT.streams.EmbeddingFeature;
import com.espertech.esper.example.IOT.helpers.HelperUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddingFeatureStreamer {
    private static final String BASE_PATH = "/home/mahmoud-tayee/Masters/AIC24_Track1_YACHIYO_RIIPS/EmbedFeature";
    private static final Pattern FILE_PATTERN = Pattern.compile("feature_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+)_(\\d+\\.?\\d*)\\.npy");
    private static long timeTracker = System.currentTimeMillis();
    private static final long ONE_SEC_TIME_STEP = 1000L;

    public static void streamEmbeddingFeatures(EPRuntime runtime) {
        try {
            List<Path> scenes = HelperUtils.getSortedDirectories(Paths.get(BASE_PATH));
            for (Path scene : scenes) {
                if (!Files.isDirectory(scene)) continue;
                processScene(runtime, scene);
            }
        } catch (IOException e) {
            System.err.println("Error accessing base directory: " + BASE_PATH + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processScene(EPRuntime runtime, Path scene) {
        try {
            List<Path> cameras = HelperUtils.getSortedDirectories(scene);
            for (Path camera : cameras) {
                if (!Files.isDirectory(camera)) continue;
                processCamera(runtime, scene, camera);
            }
        } catch (IOException e) {
            System.err.println("Error accessing scene directory: " + scene + " - " + e.getMessage());
        }
    }

    private static void processCamera(EPRuntime runtime, Path scene, Path camera) {
        try {
            List<Path> files = HelperUtils.getSortedFiles(camera, "*.npy");
            int prevFrame = 1;
            for (Path entry : files) {
                prevFrame = processFile(runtime, scene, camera, entry, prevFrame);
            }
        } catch (IOException e) {
            System.err.println("Error accessing camera directory: " + camera + " - " + e.getMessage());
        }
    }

    private static int processFile(EPRuntime runtime, Path scene, Path camera, Path entry, int prevFrame) {
        String fileName = entry.getFileName().toString();
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        int curFrame = prevFrame;
        if (!matcher.matches()) {
            System.err.println("Skipping file (invalid format): " + fileName);
            return -1;
        }
        try {
            File npyFile = entry.toFile();
            INDArray data = Nd4j.createFromNpyFile(npyFile);
            List<Float> featureList = convertToFloatList(data.toFloatVector());
            curFrame = Integer.parseInt(matcher.group(1));
            int uNum = Integer.parseInt(matcher.group(2));
            int x1 = Integer.parseInt(matcher.group(3));
            int x2 = Integer.parseInt(matcher.group(4));
            int y1 = Integer.parseInt(matcher.group(5));
            int y2 = Integer.parseInt(matcher.group(6));
            float conf = Float.parseFloat(matcher.group(7));
            if (curFrame != prevFrame) {
                timeTracker = EventEPLUtil.advanceTime(runtime);
            }
//            System.out.println("Prcoessed: " + npyFile);
            runtime.getEventService().sendEventBean(
                    new EmbeddingFeature(timeTracker, featureList, curFrame, uNum, x1, x2, y1, y2, conf),
                    "embeddingFeature"
            );
        } catch (Exception e) {
            System.err.println("Error processing file: " + fileName + " - " + e.getMessage());
            e.printStackTrace();
        }
        return curFrame;
    }

    private static List<Float> convertToFloatList(float[] featureArray) {
        List<Float> featureList = new ArrayList<>(featureArray.length);
        for (float value : featureArray) featureList.add(value);
        return featureList;
    }
}
