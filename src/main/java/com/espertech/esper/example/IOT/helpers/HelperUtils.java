package com.espertech.esper.example.IOT.helpers;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.*;

public class HelperUtils {
    private HelperUtils() {
        /* Prevent instantiation */
    }

    public static List<Path> getSortedDirectories(Path parentDir) throws IOException {
        List<Path> directories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) directories.add(entry);
            }
        }
        directories.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return directories;
    }

    public static List<Path> getSortedFiles(Path directory, String globPattern) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, globPattern)) {
            for (Path entry : stream) files.add(entry);
        }
        // Sort by only the first two numerical parts
        files.sort(Comparator.comparing(p -> extractFirstTwoNumbers(p.getFileName().toString()), new NumberListComparator()));

        return files;
    }

    private static List<Integer> extractFirstTwoNumbers(String filename) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(filename);

        while (matcher.find() && numbers.size() < 2) { // Extract at most two numbers
            numbers.add(Integer.parseInt(matcher.group()));
        }

        // Ensure list has exactly two elements for consistent sorting
        while (numbers.size() < 2) numbers.add(0);

        return numbers;
    }

    // Custom comparator to compare the first two numbers
    static class NumberListComparator implements Comparator<List<Integer>> {
        @Override
        public int compare(List<Integer> list1, List<Integer> list2) {
            int cmp = Integer.compare(list1.get(0), list2.get(0)); // Compare first number
            if (cmp != 0) return cmp;
            return Integer.compare(list1.get(1), list2.get(1)); // Compare second number if needed
        }
    }
}

