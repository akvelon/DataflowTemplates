/*
 * Copyright (C) 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Enums;
import com.google.common.base.Optional;
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

  /**
   * Parse provided json string to {@link Schema}.
   *
   * <p>Json string should be in "[{"type": "INT32", "name": "fieldName"}, ...]" format
   *
   * @param jsonString json compatible string
   * @return converted {@link Schema}
   */
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
      throw new SchemaParseException(
          "Provided schema must be in \"[{\"type\": \"INT32\", \"name\": \"fieldName\"}, ...]\" format");
    }
    return new Schema(getFieldsfromJsonNode(jsonNodes));

  }


  private static List<Field> getFieldsfromJsonNode(JsonNode jsonNode)
      throws SchemaParseException {
    List<Field> fields = new LinkedList<>();
    for (JsonNode node : jsonNode) {
      if (!node.isObject()) {
        throw new SchemaParseException("Node must be object: "+ node.toString());
      }
      String type = getText(node, "type", "type is missed");
      String name = getText(node, "name", "name is missed");
      fields.add(Field.of(name, stringToFieldType(type)));
    }
    return fields;
  }

  private static FieldType stringToFieldType(String string) throws SchemaParseException {
    Optional<TypeName> typeName = Enums.getIfPresent(TypeName.class, string);
    if (typeName.isPresent()) {
      return FieldType.of(typeName.get());
    }
    throw new SchemaParseException(String.format("Provided type \"%s\" does not exist", string));
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
