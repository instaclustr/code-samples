package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** A2A Task: stateful unit of work. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {
    public static final String KIND = "task";

    private String kind = KIND;
    private String id;
    private String contextId;
    private TaskStatus status;
    private List<Message> history;
    private List<Artifact> artifacts;
    private Map<String, Object> metadata;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public List<Message> getHistory() { return history; }
    public void setHistory(List<Message> history) { this.history = history; }
    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
