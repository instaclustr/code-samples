package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * A2A Part: smallest unit of content in a Message or Artifact.
 * One of TextPart, FilePart, or DataPart.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "text", value = TextPart.class),
    @JsonSubTypes.Type(name = "file", value = FilePart.class),
    @JsonSubTypes.Type(name = "data", value = DataPart.class)
})
public interface Part {
    String getKind();
    Map<String, Object> getMetadata();
}
