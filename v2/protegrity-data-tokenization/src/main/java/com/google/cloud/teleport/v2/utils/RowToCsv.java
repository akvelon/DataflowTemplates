package com.google.cloud.teleport.v2.utils;

import java.util.stream.Collectors;
import org.apache.beam.sdk.values.Row;

public class RowToCsv {

  private  final String csvDelimiter;

  public RowToCsv(String csvDelimiter) {
    this.csvDelimiter = csvDelimiter;
  }

  public String getCsvFromRow(Row row) {
    return row.getValues()
        .stream()
        .map(Object::toString)
        .collect(Collectors.joining(csvDelimiter));
  }
}