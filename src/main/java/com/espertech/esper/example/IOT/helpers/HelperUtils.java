package com.espertech.esper.example.IOT.helpers;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return files;
    }
}

