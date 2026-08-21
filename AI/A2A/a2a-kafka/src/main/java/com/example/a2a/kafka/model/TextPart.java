package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** A2A text content part. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextPart implements Part {
    public static final String KIND = "text";

    private String kind = KIND;
    private String text;
    private Map<String, Object> metadata;

    public TextPart() {}

    public TextPart(String text) {
        this.text = text;
    }

    @Override
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
