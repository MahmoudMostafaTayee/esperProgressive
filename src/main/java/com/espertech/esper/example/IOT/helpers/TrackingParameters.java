package com.espertech.esper.example.IOT.helpers;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackingParameters {
    private static final Logger logger = LoggerFactory.getLogger(TrackingParameters.class);
    public enum exec_level { ALL, SCPT, MCPT };

    public static double epsilonScpt = 0.10;
    public static int timePeriod = 3;
    public static int fps = 30;
    public static double epsilonMcpt = 0.37;
    public static int shortTrackTh = 120;
    public static int keypointConditionTh = 1;
    public static boolean replaceSimilarityByWCoordinate = true;
    public static String distanceType = "min";
    public static int distanceTh = 10;
    public static double simTh = 0.85;
    public static int deleteGidTh = 5000;
    public static exec_level exec_lvl = exec_level.ALL;

    private TrackingParameters() {
        /* Prevent instantiation */
    }

    public static ErrorCode getTrackingParams(String[] args) {
        // Parse arguments
        CommandLine cmd = parseArguments(args);

        if (cmd == null) {
            return ErrorCode.INVALID_INPUT;
        }

        // Extracting values
        int scene = Integer.parseInt(cmd.getOptionValue("scene"));
        String output = cmd.getOptionValue("output", "Tracking");
        if (cmd.hasOption("exec_all")) {
            exec_lvl = exec_level.ALL;
        } else {
            if (cmd.hasOption("exec_scpt")) {
                exec_lvl = exec_level.SCPT;
            } else if (cmd.hasOption("exec_mcpt")) {
                exec_lvl = exec_level.MCPT;
            }
        }

        // Load scene-specific parameters if specified.
        getParametersForScene(scene);

        // Print parsed values (for testing)
        System.out.println("Scene: " + scene);
        System.out.println("Output: " + output);

        return ErrorCode.SUCCESS;
    }

    private static CommandLine parseArguments(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("s")
                .longOpt("scene")
                .desc("Scene ID")
                .hasArg()
                .required()
                .type(Number.class)
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .desc("Output directory")
                .hasArg()
                .type(String.class)
                .build());

        options.addOption("all", "exec_all", false, "Execute all tracking modes");
        options.addOption("scpt", "exec_scpt", false, "Execute SCPT tracking mode");
        options.addOption("mcpt", "exec_mcpt", false, "Execute MCPT tracking mode");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            formatter.printHelp("java ArgumentParser", options);
            return null;
        }
    }

    private static void getParametersForScene(int scene) {
        // TODO: Implement logic to load parameters based on scene
    }

    public static void printArgs() {
        logger.info(   "TrackingParameters{" +
                    "epsilonScpt=" + epsilonScpt +
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

