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
package com.google.cloud.teleport.v2.transforms;

import com.google.cloud.teleport.v2.values.FailsafeElement;
import java.time.Instant;
import java.util.ArrayList;
import org.apache.beam.sdk.transforms.SerializableFunction;

/**
 * The {@link SerializableFunctions} class to store static Serializable functions.
 */
public class SerializableFunctions {

  private static final SerializableFunction<FailsafeElement<String, String>, String> csvErrorConverter = (FailsafeElement<String, String> failsafeElement) -> {
    ArrayList<String> outputRow = new ArrayList<>();
    final String message = failsafeElement.getOriginalPayload();
    String timestamp = Instant.now().toString();
    outputRow.add(timestamp);
    outputRow.add(failsafeElement.getErrorMessage());
    outputRow.add(failsafeElement.getStacktrace());
    // Only set the payload if it's populated on the message.
    if (failsafeElement.getOriginalPayload() != null) {
      outputRow.add(message);
    }

    return String.join(",", outputRow);
  };

  public static SerializableFunction<FailsafeElement<String, String>, String> getCsvErrorConverter() {
    return csvErrorConverter;
  }
}