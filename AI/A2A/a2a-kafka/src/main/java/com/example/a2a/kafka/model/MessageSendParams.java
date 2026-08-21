package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** A2A message/send request params. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSendParams {
    private Message message;
    private MessageSendConfiguration configuration;
    private Map<String, Object> metadata;

    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public MessageSendConfiguration getConfiguration() { return configuration; }
    public void setConfiguration(MessageSendConfiguration configuration) { this.configuration = configuration; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageSendConfiguration {
        private String[] acceptedOutputModes;
        private Integer historyLength;
        private Boolean blocking;

        public String[] getAcceptedOutputModes() { return acceptedOutputModes; }
        public void setAcceptedOutputModes(String[] acceptedOutputModes) { this.acceptedOutputModes = acceptedOutputModes; }
        public Integer getHistoryLength() { return historyLength; }
        public void setHistoryLength(Integer historyLength) { this.historyLength = historyLength; }
        public Boolean getBlocking() { return blocking; }
        public void setBlocking(Boolean blocking) { this.blocking = blocking; }
    }
}
