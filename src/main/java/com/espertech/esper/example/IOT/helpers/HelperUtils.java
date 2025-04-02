package com.espertech.esper.example.IOT.helpers;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.*;

public class HelperUtils {
    // Regex to split strings into numeric and non-numeric tokens
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

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
        files.sort(HelperUtils::comparePathsNatural);
        return files;
    }

    private static int comparePathsNatural(Path p1, Path p2) {
        String s1 = p1.getFileName().toString();
        String s2 = p2.getFileName().toString();
        return compareNatural(s1, s2);
    }

    private static int compareNatural(String a, String b) {
        String[] tokensA = splitIntoTokenArray(a);
        String[] tokensB = splitIntoTokenArray(b);

        int length = Math.min(tokensA.length, tokensB.length);
        for (int i = 0; i < length; i++) {
            String tokenA = tokensA[i];
            String tokenB = tokensB[i];

            // Compare numeric tokens as numbers
            if (Character.isDigit(tokenA.charAt(0))) {
                if (!Character.isDigit(tokenB.charAt(0))) {
                    // Numbers come before non-numbers
                    return -1;
                }
                long numA = Long.parseLong(tokenA);
                long numB = Long.parseLong(tokenB);
                int cmp = Long.compare(numA, numB);
                if (cmp != 0) return cmp;
            } else {
                if (Character.isDigit(tokenB.charAt(0))) {
                    // Non-numbers come after numbers
                    return 1;
                }
                int cmp = tokenA.compareToIgnoreCase(tokenB);
                if (cmp != 0) return cmp;
            }
        }

        // If common parts are equal, shorter string comes first
        return Integer.compare(tokensA.length, tokensB.length);
    }

    private static String[] splitIntoTokenArray(String s) {
        return NUMBER_PATTERN.split(s);
    }
}

