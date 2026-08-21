package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A2A TaskStatus: current state and optional message. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatus {
    private TaskState state;
    private Message message;
    private String timestamp;  // ISO 8601

    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }
    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
