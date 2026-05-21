package com.ollama.mobile.model;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class OllamaModelTest {

    private final Gson gson = new Gson();

    @Test
    public void testDeserializeNameField() {
        OllamaModel model = gson.fromJson("{\"name\":\"llama3:latest\"}", OllamaModel.class);
        assertEquals("llama3:latest", model.name);
    }

    @Test
    public void testDeserializeTagsResponse() {
        String json = "{\"models\":[{\"name\":\"llama3\"},{\"name\":\"mistral\"}]}";
        OllamaModel.TagsResponse response = gson.fromJson(json, OllamaModel.TagsResponse.class);
        assertNotNull(response.models);
        assertEquals(2, response.models.size());
        assertEquals("llama3", response.models.get(0).name);
    }
}
