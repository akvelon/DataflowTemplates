/*
 * Copyright (C) 2020 Google Inc.
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

import static org.apache.beam.sdk.io.gcp.bigquery.BigQueryUtils.fromTableSchema;

import com.google.api.services.bigquery.model.TableSchema;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The {@link BigQuerySchema} Class to read JSON based schema. Is there available to read from file or from string.
 * Currently supported local File System and GCS.
 */
public class BigQuerySchema {
    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(BigQuerySchema.class);

    private TableSchema tableSchema;

    public BigQuerySchema(String schema) {
        parseJson(schema);
    }

    public BigQuerySchema(String path, Charset encoding) throws IOException {
        if (path.startsWith("gs://")) {
            parseJson(new String(readGcsFile(path), encoding));
        } else {
            byte[] encoded = Files.readAllBytes(Paths.get(path));
            parseJson(new String(encoded, encoding));
        }
    }

    public TableSchema getTableSchema() {
        return tableSchema;
    }

    private void parseJson(String jsonSchema) throws UnsupportedOperationException {
        TableSchema schema = BigQueryHelpers.fromJsonString(jsonSchema, TableSchema.class);
        validateSchemaTypes(schema);
        tableSchema = schema;

    }

    private void validateSchemaTypes(TableSchema bigQuerySchema) {
        try {
            fromTableSchema(bigQuerySchema);
        } catch (UnsupportedOperationException exception) {
            LOG.error("Check json schema, {}", exception.getMessage());
        } catch (NullPointerException npe){
            LOG.error("Missing schema keywords, please check what all required fields presented");
        }
    }

    /**
     * Reads a file from GCS.
     *
     * @param gcsFilePath path to file in GCS in format "gs://your-bucket/path/to/file"
     * @throws IOException thrown if not able to read or write file
     */
    public static byte[] readGcsFile(String gcsFilePath)
            throws IOException {
        LOG.info("Reading contents from GCS file: {}", gcsFilePath);
        // Read the GCS file into byte[] and will throw an I/O exception in case file not found.
        try (ReadableByteChannel readerChannel =
                     FileSystems.open(FileSystems.matchSingleFileSpec(gcsFilePath).resourceId())) {
            ByteBuffer buf = ByteBuffer.allocate(Integer.MAX_VALUE);
            readerChannel.read(buf);
            return buf.array();
        }
    }
}
