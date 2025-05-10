package com.espertech.esper.example.IOT.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class ClusterJsonLogger {
    private static final String FILE_PATH = "clusters.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_OF_MAP_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    // Called once to reset the file
    public static void initializeJsonFile() {
        try (FileWriter writer = new FileWriter(FILE_PATH, false)) {
            gson.toJson(new ArrayList<>(), writer);  // Write empty list
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Called repeatedly to update cluster records
    public static void appendClusterSnapshot(Map<Integer, List<Integer>> localClusterToDataIds, Map<Integer, Integer> associations) {
        try {
            // Load existing JSON data
            List<Map<String, Object>> currentClusters;
            try (Reader reader = new FileReader(FILE_PATH)) {
                currentClusters = gson.fromJson(reader, LIST_OF_MAP_TYPE);
                if (currentClusters == null) currentClusters = new ArrayList<>();
            }

            // Merge new cluster data
            for (Map.Entry<Integer, List<Integer>> entry : localClusterToDataIds.entrySet()) {
                int personID = entry.getKey();
                if (associations != null && associations.containsKey(personID)) {
                    personID = associations.get(personID);
                }

                boolean personFound = false;

                // Check if person already exists and add new IDs
                for (Map<String, Object> cluster : currentClusters) {
                    if (cluster.containsKey("persons")) {
                        Map<Integer, List<Integer>> persons = (Map<Integer, List<Integer>>) cluster.get("persons");
                        List<Integer> existingIds = persons.getOrDefault(personID, new ArrayList<>());
                        existingIds.addAll(entry.getValue());
                        persons.put(personID, existingIds);
                        personFound = true;
                        break;
                    }
                }

                // If person doesn't exist, create a new cluster entry with "persons"
                if (!personFound) {
                    Map<String, Object> newCluster = new LinkedHashMap<>();
                    newCluster.put("frames", new ArrayList<>());  // Empty frame list to be updated
                    Map<Integer, List<Integer>> newPersons = new LinkedHashMap<>();
                    newPersons.put(personID, entry.getValue());
                    newCluster.put("persons", newPersons);
                    currentClusters.add(newCluster);
                }
            }

            // Write updated JSON back to file
            try (FileWriter writer = new FileWriter(FILE_PATH, false)) {
                gson.toJson(currentClusters, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Function to update frames list
    public static void updateFrames(int frameNumber) {
        try {
            // Load existing JSON data
            List<Map<String, Object>> currentClusters;
            try (Reader reader = new FileReader(FILE_PATH)) {
                currentClusters = gson.fromJson(reader, LIST_OF_MAP_TYPE);
                if (currentClusters == null) currentClusters = new ArrayList<>();
            }

            // Update or add the frame number to the frames list in all clusters
            boolean frameExists = false;
            for (Map<String, Object> cluster : currentClusters) {
                if (cluster.containsKey("frames")) {
                    List<Integer> frames = (List<Integer>) cluster.get("frames");
                    if (!frames.contains(frameNumber)) {
                        frames.add(frameNumber);
                    }
                    frameExists = true;
                }
            }

            // If no frames were added, create a new cluster with frames
            if (!frameExists) {
                Map<String, Object> newCluster = new LinkedHashMap<>();
                newCluster.put("frames", new ArrayList<>(Arrays.asList(frameNumber)));
                newCluster.put("persons", new LinkedHashMap<>());  // Empty persons map for consistency
                currentClusters.add(newCluster);
            }

            // Write updated JSON back to file
            try (FileWriter writer = new FileWriter(FILE_PATH, false)) {
                gson.toJson(currentClusters, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
