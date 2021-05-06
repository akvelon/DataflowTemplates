package com.google.cloud.teleport.v2.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.Schema.Field;
import org.apache.beam.sdk.schemas.Schema.FieldType;
import org.apache.beam.sdk.schemas.Schema.TypeName;

public class BeamSchemaUtils {
  static JsonFactory FACTORY = new JsonFactory();
  static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

  public static Schema fromJson(String jsonString) throws SchemaParseException {
    try {
      try (JsonParser jsonParser = FACTORY.createParser(new StringReader(jsonString))) {
        return fromJson(jsonParser);
      }
    } catch (IOException error) {
      throw new SchemaParseException(error);
    }
  }


  private static Schema fromJson(JsonParser jsonParser) throws IOException, SchemaParseException {
    JsonNode jsonNodes = MAPPER.readTree(jsonParser);
    if (!jsonNodes.isArray()) {
      throw new SchemaParseException("Provided schema must be in \"[{}, ...]\" format");
    }
    return new Schema(getFieldsfromJsonNode(jsonNodes));

  }


  private static List<Field> getFieldsfromJsonNode(JsonNode jsonNode)
      throws SchemaParseException {
    List<Field> fields = new LinkedList<>();
    for (JsonNode node : jsonNode) {
      String type = getText(node, "type", "type is missed");
      String name = getText(node, "name", "name is missed");
      fields.add(Field.of(name, stringToFieldType(type)));
    }
    return fields;
  }

  private static FieldType stringToFieldType(String string) {
    TypeName typeName = TypeName.valueOf(string);
    return FieldType.of(typeName);
  }

  private static String getOptionalText(JsonNode node, String key) {
    JsonNode jsonNode = node.get(key);
    return jsonNode != null ? jsonNode.textValue() : null;
  }

  private static String getText(JsonNode node, String key, String errorMessage)
      throws SchemaParseException {
    String text = getOptionalText(node, key);
    if (text == null) {
      throw new SchemaParseException(errorMessage + ": " + node);
    }
    return text;
  }

  public static class SchemaParseException extends Exception {

    public SchemaParseException(Throwable cause) {
      super(cause);
    }

    public SchemaParseException(String message) {
      super(message);
    }
  }
}
