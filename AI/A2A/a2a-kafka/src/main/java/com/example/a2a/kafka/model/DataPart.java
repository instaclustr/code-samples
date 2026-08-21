package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** A2A structured data part. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataPart implements Part {
    public static final String KIND = "data";

    private String kind = KIND;
    private Map<String, Object> data;
    private Map<String, Object> metadata;

    public DataPart() {}

    public DataPart(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
