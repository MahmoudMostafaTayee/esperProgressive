package com.espertech.esper.example.IOT.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.espertech.esper.example.IOT.streams.PersonView;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonReader {

    /**
     * Reads a JSON file into a list of {@link PersonView} objects.
     * @param filePath the path to the JSON file
     * @return a list of PersonView objects
     * @throws IOException if an I/O error occurs
     */
    public static List<PersonView> readPersonViewsFromJson(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), new TypeReference<List<PersonView>>() {});
    }
}
