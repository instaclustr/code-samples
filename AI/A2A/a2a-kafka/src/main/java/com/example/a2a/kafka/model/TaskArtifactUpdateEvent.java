package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** A2A event: artifact update (streaming / Kafka). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskArtifactUpdateEvent {
    public static final String KIND = "artifact-update";

    private String taskId;
    private String contextId;
    private String kind = KIND;
    private Artifact artifact;
    private Map<String, Object> metadata;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Artifact getArtifact() { return artifact; }
    public void setArtifact(Artifact artifact) { this.artifact = artifact; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
