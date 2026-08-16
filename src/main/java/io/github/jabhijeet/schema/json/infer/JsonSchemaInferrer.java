package io.github.jabhijeet.schema.json.infer;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.Schema;

public interface JsonSchemaInferrer {

    Schema infer(String json);

    Schema infer(JsonNode node);

    Schema infer(String json, String rootName);

    Schema infer(JsonNode node, String rootName);
}
