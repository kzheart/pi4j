package com.pi4j.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.ai.types.Tool;
import com.pi4j.ai.types.ToolCallContent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolValidatorTest {

    @Test
    void validateRequiredAndTypeCoercion() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject count = new JsonObject();
        count.addProperty("type", "integer");
        properties.add("count", count);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("count");
        schema.add("required", required);

        Tool tool = new Tool("counter", "counter", schema);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("count", "12");

        Map<String, Object> validated = ToolValidator.validate(
                tool,
                new ToolCallContent("id-1", "counter", args));

        assertEquals(12, validated.get("count"));
    }

    @Test
    void missingRequiredFieldThrows() {
        JsonObject schema = new JsonObject();
        JsonArray required = new JsonArray();
        required.add("name");
        schema.add("required", required);

        Tool tool = new Tool("demo", "demo", schema);

        assertThrows(
                ToolValidationException.class,
                () -> ToolValidator.validate(tool, new ToolCallContent("id", "demo", Collections.<String, Object>emptyMap())));
    }
}
