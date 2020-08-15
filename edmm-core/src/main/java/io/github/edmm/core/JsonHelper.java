package io.github.edmm.core;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;

public abstract class JsonHelper {

    private static ObjectMapper mapper;

    public static String writeValue(Object value) {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void writeValue(Object value, File file) {
        try {
            FileUtils.writeStringToFile(file, writeValue(value), "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> T readValue(File file, Class<T> clazz) {
        try {
            String json = FileUtils.readFileToString(file, "UTF-8");
            if (mapper == null) {
                mapper = new ObjectMapper();
                mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            }
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
