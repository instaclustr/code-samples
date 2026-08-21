package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** A2A Artifact: output composed of parts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Artifact {
    private String artifactId;
    private String name;
    private String description;
    private List<Part> parts;
    private Map<String, Object> metadata;
    private List<String> extensions;

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public List<String> getExtensions() { return extensions; }
    public void setExtensions(List<String> extensions) { this.extensions = extensions; }
}
