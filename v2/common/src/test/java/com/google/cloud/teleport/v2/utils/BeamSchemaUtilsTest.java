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

import static org.junit.Assert.assertEquals;

import com.google.cloud.teleport.v2.utils.BeamSchemaUtils.SchemaParseException;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.Schema.FieldType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BeamSchemaUtilsTest {

  public static String jsonSchema = "[\n"
      + "  {\n"
      + "    \"name\": \"byte\",\n"
      + "    \"type\": \"BYTE\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"int16\",\n"
      + "    \"type\": \"INT16\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"int32\",\n"
      + "    \"type\": \"INT32\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"int64\",\n"
      + "    \"type\": \"INT64\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"decimal\",\n"
      + "    \"type\": \"DECIMAL\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"float\",\n"
      + "    \"type\": \"FLOAT\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"double\",\n"
      + "    \"type\": \"DOUBLE\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"string\",\n"
      + "    \"type\": \"STRING\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"datetime\",\n"
      + "    \"type\": \"DATETIME\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"boolean\",\n"
      + "    \"type\": \"BOOLEAN\"\n"
      + "  },\n"
      + "  {\n"
      + "    \"name\": \"bytes\",\n"
      + "    \"type\": \"BYTES\"\n"
      + "  }\n"
      + "]\n";
  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void testFromJson() throws SchemaParseException {
    Schema schema = BeamSchemaUtils.fromJson(jsonSchema);
    assertEquals(11, schema.getFieldCount());

    assertEquals(0, schema.indexOf("byte"));
    assertEquals("byte", schema.getField(0).getName());
    assertEquals(FieldType.BYTE, schema.getField(0).getType());

    assertEquals(1, schema.indexOf("int16"));
    assertEquals("int16", schema.getField(1).getName());
    assertEquals(FieldType.INT16, schema.getField(1).getType());

    assertEquals(2, schema.indexOf("int32"));
    assertEquals("int32", schema.getField(2).getName());
    assertEquals(FieldType.INT32, schema.getField(2).getType());

    assertEquals(3, schema.indexOf("int64"));
    assertEquals("int64", schema.getField(3).getName());
    assertEquals(FieldType.INT64, schema.getField(3).getType());

    assertEquals(4, schema.indexOf("decimal"));
    assertEquals("decimal", schema.getField(4).getName());
    assertEquals(FieldType.DECIMAL, schema.getField(4).getType());

    assertEquals(5, schema.indexOf("float"));
    assertEquals("float", schema.getField(5).getName());
    assertEquals(FieldType.FLOAT, schema.getField(5).getType());

    assertEquals(6, schema.indexOf("double"));
    assertEquals("double", schema.getField(6).getName());
    assertEquals(FieldType.DOUBLE, schema.getField(6).getType());

    assertEquals(7, schema.indexOf("string"));
    assertEquals("string", schema.getField(7).getName());
    assertEquals(FieldType.STRING, schema.getField(7).getType());

    assertEquals(8, schema.indexOf("datetime"));
    assertEquals("datetime", schema.getField(8).getName());
    assertEquals(FieldType.DATETIME, schema.getField(8).getType());

    assertEquals(9, schema.indexOf("boolean"));
    assertEquals("boolean", schema.getField(9).getName());
    assertEquals(FieldType.BOOLEAN, schema.getField(9).getType());

    assertEquals(10, schema.indexOf("bytes"));
    assertEquals("bytes", schema.getField(10).getName());
    assertEquals(FieldType.BYTES, schema.getField(10).getType());
  }

  @Test
  public void testMissedField() throws SchemaParseException {
    exceptionRule.expect(SchemaParseException.class);
    exceptionRule.expectMessage("type is missed: {\"name\":\"testName\"}");
    BeamSchemaUtils.fromJson("[{\"name\": \"testName\"}]");

    exceptionRule.expect(SchemaParseException.class);
    exceptionRule.expectMessage("name is missed: {\"type\":\"testType\"}");
    BeamSchemaUtils.fromJson("[{\"type\": \"testType\"}]");
  }

  @Test
  public void testInvalidFormat() throws SchemaParseException {
    exceptionRule.expect(SchemaParseException.class);
    exceptionRule.expectMessage(
        "Provided schema must be in \"[{\"type\": \"INT32\", \"name\": \"fieldName\"}, ...]\" format");
    BeamSchemaUtils.fromJson("{\"name\": \"bytes\",\"type\": \"BYTES\"}");
  }

  @Test
  public void testInvalidType() throws SchemaParseException {
    exceptionRule.expect(SchemaParseException.class);
    exceptionRule.expectMessage("Provided type \"INVALID\" does not exist");
    BeamSchemaUtils.fromJson("[{\"name\": \"bytes\",\"type\": \"INVALID\"}]");
  }

  @Test
  public void testInvalidNodeFormat() throws SchemaParseException {
    exceptionRule.expect(SchemaParseException.class);
    exceptionRule.expectMessage("Node must be object: [{\"name\":\"bytes\",\"type\":\"BYTES\"}]");
    BeamSchemaUtils.fromJson("[[{\"name\": \"bytes\",\"type\": \"BYTES\"}]]");
  }
}
