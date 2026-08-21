package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** A2A event: task status update (streaming / Kafka). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatusUpdateEvent {
    public static final String KIND = "status-update";

    private String taskId;
    private String contextId;
    private String kind = KIND;
    private TaskStatus status;
    private Boolean final_;
    private Map<String, Object> metadata;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Boolean getFinal() { return final_; }
    public void setFinal(Boolean final_) { this.final_ = final_; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
