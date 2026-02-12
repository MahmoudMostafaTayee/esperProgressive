package com.espertech.esper.example.IOT.helpers;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackingParameters {
    private static final Logger logger = LoggerFactory.getLogger(TrackingParameters.class);

    public enum exec_level {
        ALL, SCPT, MCPT
    };

    public static double epsilonScpt = 0.10;
    public static int timePeriod = 3;
    public static int fps = 30;
    public static double epsilonMcpt = 0.4;
    public static int shortTrackTh = 120;
    public static int keypointConditionTh = 2;
    public static boolean replaceSimilarityByWCoordinate = false; // Python default: False
    public static String distanceType = "max"; // Python default: "max"
    public static int distanceTh = 5; // Python default: 5
    public static double simTh = 0.75; // Python default: 0.75
    public static double keypointTh = 0.7;
    public static double aspectTh = 1.6;
    public static double replaceValue = -10.0;
    public static int deleteGidTh = 6000;

    public static int min_samples = 4;
    public static String clustering_method = "agglomerative";
    public static String representativeSelectionMethod = "keypoint";

    public static exec_level exec_lvl = exec_level.ALL;
    public static double iouTh = 0.9;
    public static boolean overlap_suppression = true;
    public static boolean isDebug = true;
    public static int max_number_of_windows_to_process = 1; // This won't work unless in isDebug is ture.

    // SNMS Parameters
    public static boolean sequential_nms = true;
    public static double temporally_snms_th = 0.6;
    public static double spatially_snms_th = 0.6;
    public static boolean merge_nonoverlap = true;

    // Separate Warp Parameters
    public static boolean separate_warp = true;
    public static int warp_th = 40;
    public static double alpha = 0.5;

    public static boolean exclude_short = false; // Python default: False
    public static int short_tracklet_th = 120; // Python default: 120

    public static boolean exclude_motionless = false; // Python default: False
    public static int stop_track_th = 25;

    // ===== Runtime-configurable paths =====
    public static String FEATURES_BASE_DIR;
    public static String OUTPUT_DIR;

    // ===== Camera selection =====
    // "all" OR "0001", "0002", ...
    public static String CAMERA_FILTER;

    public static int scene;

    private TrackingParameters() {
        /* Prevent instantiation */
    }

    public static ErrorCode getTrackingParams(String[] args) {

        CommandLine cmd = parseArguments(args);
        if (cmd == null) {
            return ErrorCode.INVALID_INPUT;
        }

        // ---------- Scene ----------
        scene = Integer.parseInt(
                cmd.getOptionValue("scene"));

        // ---------- Feature directory ----------
        FEATURES_BASE_DIR = cmd.getOptionValue(
                "features_dir");

        // ---------- Output directory ----------
        OUTPUT_DIR = cmd.getOptionValue(
                "output_dir",
                "./output");

        // ---------- Camera filter ----------
        CAMERA_FILTER = cmd.getOptionValue("camera", "all");

        // ---------- Execution level ----------
        if (cmd.hasOption("exec_all")) {
            exec_lvl = exec_level.ALL;
        } else if (cmd.hasOption("exec_scpt")) {
            exec_lvl = exec_level.SCPT;
        } else if (cmd.hasOption("exec_mcpt")) {
            exec_lvl = exec_level.MCPT;
        }

        // ---------- Scene-specific parameters ----------
        getParametersForScene(scene);

        // ---------- Create output directory ----------
        createOutputDirectory();

        // ---------- Log everything ----------
        printArgs();

        return ErrorCode.SUCCESS;
    }

    private static void createOutputDirectory() {
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Paths.get(OUTPUT_DIR));
        } catch (Exception e) {
            logger.error("Failed to create output directory: " + OUTPUT_DIR, e);
        }
    }

    private static CommandLine parseArguments(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder()
                .longOpt("scene")
                .hasArg()
                .required()
                .desc("Scene number (e.g., 1, 2, 3)")
                .build());

        options.addOption(Option.builder()
                .longOpt("features_dir")
                .hasArg()
                .desc("Base directory for embedding features")
                .build());

        options.addOption(Option.builder()
                .longOpt("camera")
                .hasArg()
                .desc("Camera number (e.g., 0001) or 'all'")
                .build());

        options.addOption(Option.builder()
                .longOpt("output_dir")
                .hasArg()
                .desc("Directory to save logs and outputs")
                .build());

        options.addOption("exec_all", false, "Execute all stages");
        options.addOption("exec_scpt", false, "Execute SCPT stage");
        options.addOption("exec_mcpt", false, "Execute MCPT stage");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("MCPT", options);
            return null;
        }
    }

    private static void getParametersForScene(int scene) {
        // TODO: Implement logic to load parameters based on scene
    }

    public static void printArgs() {
        logger.info(
                "TrackingParameters{" +
                        "scene=" + scene +
                        ", FEATURES_BASE_DIR='" + FEATURES_BASE_DIR + '\'' +
                        ", OUTPUT_DIR='" + OUTPUT_DIR + '\'' +
                        ", CAMERA_FILTER='" + CAMERA_FILTER + '\'' +
                        ", epsilonScpt=" + epsilonScpt +
                        ", timePeriod=" + timePeriod +
                        ", epsilonMcpt=" + epsilonMcpt +
                        ", shortTrackTh=" + shortTrackTh +
                        ", keypointConditionTh=" + keypointConditionTh +
                        ", replaceSimilarityByWCoordinate=" + replaceSimilarityByWCoordinate +
                        ", distanceType='" + distanceType + '\'' +
                        ", distanceTh=" + distanceTh +
                        ", simTh=" + simTh +
                        ", deleteGidTh=" + deleteGidTh +
                        ", exec_lvl=" + exec_lvl +
                        '}');
    }

}
