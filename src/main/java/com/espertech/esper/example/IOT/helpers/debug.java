package com.espertech.esper.example.IOT.helpers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class debug {
    public static void saveDoubleMatrix(String filename, double[][] matrix) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        for (double[] row : matrix) {
            String line = Arrays.stream(row)
                    .mapToObj(Double::toString)
                    .collect(Collectors.joining(","));
            writer.write(line);
            writer.newLine();
        }
        writer.close();
    }

    public static void saveIntList(String filename, List<Integer> list) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        for (Integer i : list) {
            writer.write(i.toString());
            writer.newLine();
        }
        writer.close();
    }
}
