package com.example.a2a.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Kafka message envelope for A2A protocol.
 * Carries JSON-RPC 2.0 style method + params or result, for transport over Kafka.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2AEnvelope {
    public static final String JSONRPC_VERSION = "2.0";

    private String jsonrpc = JSONRPC_VERSION;
    private String method;
    private Object params;
    private Object result;
    private Object error;
    private String id;
    private String timestamp;  // ISO 8601

    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Object getParams() { return params; }
    public void setParams(Object params) { this.params = params; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public Object getError() { return error; }
    public void setError(Object error) { this.error = error; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /** Build a request envelope (e.g. message/send). */
    public static A2AEnvelope request(String method, Object params, String id) {
        A2AEnvelope e = new A2AEnvelope();
        e.setMethod(method);
        e.setParams(params);
        e.setId(id);
        e.setTimestamp(java.time.Instant.now().toString());
        return e;
    }

    /** Build a result/event envelope (e.g. status-update, artifact-update, or Task/Message result). */
    public static A2AEnvelope result(String method, Object result, String id) {
        A2AEnvelope e = new A2AEnvelope();
        e.setMethod(method);
        e.setResult(result);
        e.setId(id);
        e.setTimestamp(java.time.Instant.now().toString());
        return e;
    }
}
