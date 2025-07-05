package com.espertech.esper.example.IOT.helpers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class for detecting overlaps in clustering results.
 */
public class OverlapDetector {

    /**
     * Represents the result of the overlap detection, containing lists of overlap and non-overlap indices.
     */
    public static class OverlapResult {
        public List<List<Integer>> overlapIndicesList;
        public List<Integer> nonOverlapIndices;

        public OverlapResult(List<List<Integer>> overlapIndicesList, List<Integer> nonOverlapIndices) {
            this.overlapIndicesList = overlapIndicesList;
            this.nonOverlapIndices = nonOverlapIndices;
        }
    }

    /**
     * Divides the given cluster frames and indices into overlapping and non-overlapping groups.
     * An overlap occurs when multiple indices share the same frame number within a cluster.
     *
     * @param clusterFrames A list of frame numbers corresponding to each item in the cluster.
     * @param clusterIndices A list of original indices corresponding to each item in the cluster.
     * @return An {@code OverlapResult} object containing two lists: {@code overlapIndicesList} (a list of lists, where each inner list contains indices that share the same frame) and {@code nonOverlapIndices} (a list of indices that do not have any frame overlaps).
     */
    public static OverlapResult divideOverlapOrNonOverlap(List<Integer> clusterFrames, List<Integer> clusterIndices) {
        // Process only common elements if lists are unequal
        int size = Math.min(clusterFrames.size(), clusterIndices.size());
        Map<Integer, List<Integer>> frameIndicesDict = new TreeMap<>();

        // Group indices by frame
        for (int i = 0; i < size; i++) {
            int frame = clusterFrames.get(i);
            int index = clusterIndices.get(i);
            frameIndicesDict.computeIfAbsent(frame, k -> new ArrayList<>()).add(index);
        }

        // Identify frames with >1 index (overlaps)
        List<List<Integer>> overlapIndicesList = frameIndicesDict.values().stream()
                .filter(indices -> indices.size() > 1)
                .collect(Collectors.toList());

        // Flatten overlaps into a Set for O(1) lookups
        Set<Integer> flattenedOverlapIndices = overlapIndicesList.stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // Filter original indices not present in overlap set
        List<Integer> nonOverlapIndices = clusterIndices.stream()
                .filter(index -> !flattenedOverlapIndices.contains(index))
                .collect(Collectors.toList());

        return new OverlapResult(overlapIndicesList, nonOverlapIndices);
    }
}


