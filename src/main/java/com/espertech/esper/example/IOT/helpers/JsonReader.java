package com.espertech.esper.example.IOT.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.espertech.esper.example.IOT.PersonView.PersonView;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonReader {

    public static List<PersonView> readPersonViewsFromJson(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), new TypeReference<List<PersonView>>() {});
    }
}
