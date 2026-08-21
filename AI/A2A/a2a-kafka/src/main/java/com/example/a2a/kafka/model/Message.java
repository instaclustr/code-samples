package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** A2A Message: a communication turn with role and parts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    public static final String KIND = "message";

    private String kind = KIND;
    private String role;  // "user" | "agent"
    private List<Part> parts;
    private Map<String, Object> metadata;
    private List<String> extensions;
    private List<String> referenceTaskIds;
    private String messageId;
    private String taskId;
    private String contextId;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public List<String> getExtensions() { return extensions; }
    public void setExtensions(List<String> extensions) { this.extensions = extensions; }
    public List<String> getReferenceTaskIds() { return referenceTaskIds; }
    public void setReferenceTaskIds(List<String> referenceTaskIds) { this.referenceTaskIds = referenceTaskIds; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
}
