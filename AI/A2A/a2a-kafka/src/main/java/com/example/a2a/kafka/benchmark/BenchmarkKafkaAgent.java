package com.example.a2a.kafka.benchmark;

import com.example.a2a.kafka.ReplyRouting;
import com.example.a2a.kafka.model.Artifact;
import com.example.a2a.kafka.model.MessageSendParams;
import com.example.a2a.kafka.model.Task;
import com.example.a2a.kafka.model.TaskArtifactUpdateEvent;
import com.example.a2a.kafka.model.TaskState;
import com.example.a2a.kafka.model.TaskStatus;
import com.example.a2a.kafka.model.TaskStatusUpdateEvent;
import com.example.a2a.kafka.model.TextPart;
import com.example.a2a.kafka.A2AKafkaProducer;
import com.example.a2a.kafka.countdown.CountdownParser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Zero-work instant Kafka A2A agent for protocol benchmarking. */
public final class BenchmarkKafkaAgent {

    public static final int STREAM_UPDATES = 3;
    private static final int RESULT_BYTES = 4_500;
    private static final int STREAM_CHUNK_BYTES = RESULT_BYTES / STREAM_UPDATES;
    private static final String FILL_PATTERN = "A2A-BENCH-PAYLOAD-0123456789-";

    public static final String MODE_RR = "bench:rr";
    public static final String MODE_STREAM = "bench:stream";
    public static final String MODE_NOTIFY = "bench:notify";
    public static final String MODE_FANOUT_STREAM = "bench:fanout-stream";
    public static final String MODE_LLM_SIM = "bench:llm-sim";

    public static final int FANOUT_DEFAULT_CHUNK_COUNT = 100;
    public static final int FANOUT_DEFAULT_CHUNK_BYTES = 512;
    public static final int FANOUT_DEFAULT_INTER_CHUNK_MS = 0;
    public static final String FANOUT_ARTIFACT_ID = "bench-fanout";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final A2AKafkaProducer producer;

    BenchmarkKafkaAgent(A2AKafkaProducer producer) {
        this.producer = producer;
    }

    public void onMessageSend(String taskId, MessageSendParams params) {
        String replyTopic = ReplyRouting.extractReplyTopic(params, producer.getUpdatesTopic());
        String userText = CountdownParser.extractUserText(params);
        String mode = parseMode(userText);
        String result = resultPayload();
        switch (mode) {
            case MODE_RR -> runRequestResponse(replyTopic, taskId, result);
            case MODE_STREAM -> runStreaming(replyTopic, taskId);
            case MODE_FANOUT_STREAM -> runFanoutStream(replyTopic, taskId, userText);
            case MODE_LLM_SIM -> runLlmSim(replyTopic, taskId, userText);
            case MODE_NOTIFY -> runNotification(replyTopic, taskId, result);
            default -> publishStatus(replyTopic, taskId, TaskState.REJECTED, "Unknown benchmark mode: " + mode, true);
        }
    }

    private void runRequestResponse(String replyTopic, String taskId, String result) {
        publishArtifact(replyTopic, taskId, result);
        publishStatus(replyTopic, taskId, TaskState.COMPLETED, "bench:rr complete", true);
        publishTaskResult(replyTopic, taskId, result);
    }

    private void runStreaming(String replyTopic, String taskId) {
        publishStatus(replyTopic, taskId, TaskState.WORKING, "bench:stream started", false);
        for (int i = 1; i <= STREAM_UPDATES; i++) {
            publishArtifact(replyTopic, taskId, streamChunkPayload(i));
        }
        publishStatus(replyTopic, taskId, TaskState.COMPLETED, "bench:stream complete", true);
    }

    private void runLlmSim(String replyTopic, String taskId, String userText) {
        LlmSimParams params = parseLlmSimParams(userText);
        if (params.thinkMs() > 0) {
            try {
                Thread.sleep(params.thinkMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                publishStatus(replyTopic, taskId, TaskState.FAILED, "bench:llm-sim interrupted during think", true);
                return;
            }
        }
        publishStatusNoFlush(replyTopic, taskId, TaskState.WORKING, "bench:llm-sim streaming", false);
        for (int i = 1; i <= params.chunkCount(); i++) {
            if (params.interChunkMs() > 0) {
                try {
                    Thread.sleep(params.interChunkMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    publishStatus(replyTopic, taskId, TaskState.FAILED, "bench:llm-sim interrupted", true);
                    return;
                }
            }
            publishArtifactNoFlush(replyTopic, taskId, fanoutChunkPayload(i, params.chunkBytes()), FANOUT_ARTIFACT_ID);
        }
        publishStatusNoFlush(replyTopic, taskId, TaskState.COMPLETED, "bench:llm-sim complete", true);
        producer.flush();
    }

    private void runFanoutStream(String replyTopic, String taskId, String userText) {
        FanoutParams params = parseFanoutParams(userText);
        publishStatusNoFlush(replyTopic, taskId, TaskState.WORKING, "bench:fanout-stream started", false);
        for (int i = 1; i <= params.chunkCount(); i++) {
            if (params.interChunkMs() > 0) {
                try {
                    Thread.sleep(params.interChunkMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    publishStatus(replyTopic, taskId, TaskState.FAILED, "bench:fanout-stream interrupted", true);
                    return;
                }
            }
            publishArtifactNoFlush(replyTopic, taskId, fanoutChunkPayload(i, params.chunkBytes()), FANOUT_ARTIFACT_ID);
        }
        publishStatusNoFlush(replyTopic, taskId, TaskState.COMPLETED, "bench:fanout-stream complete", true);
        producer.flush();
    }

    private void runNotification(String replyTopic, String taskId, String result) {
        publishStatus(replyTopic, taskId, TaskState.WORKING, "bench:notify working", false);
        publishArtifact(replyTopic, taskId, result);
        publishStatus(replyTopic, taskId, TaskState.COMPLETED, "bench:notify complete", true);
    }

    static boolean isBenchmarkRequest(String text) {
        String mode = parseMode(text);
        return MODE_RR.equals(mode) || MODE_STREAM.equals(mode) || MODE_NOTIFY.equals(mode)
                || MODE_FANOUT_STREAM.equals(mode) || MODE_LLM_SIM.equals(mode);
    }

    static String parseMode(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }

    static String streamChunkPayload(int chunkIndex) {
        if (chunkIndex < 1 || chunkIndex > STREAM_UPDATES) {
            throw new IllegalArgumentException("chunkIndex out of range: " + chunkIndex);
        }
        return fixedSizeUtf8("A2A-BENCH-STREAM-" + chunkIndex + "-", STREAM_CHUNK_BYTES);
    }

    static String fanoutChunkPayload(int chunkIndex, int chunkBytes) {
        return fixedSizeUtf8("FANOUT-" + chunkIndex + "-", chunkBytes);
    }

    static LlmSimParams parseLlmSimParams(String message) {
        LlmSimParams defaults = LlmSimParams.defaults();
        if (message == null) {
            return defaults;
        }
        int firstNewline = message.indexOf('\n');
        if (firstNewline < 0) {
            return defaults;
        }
        int secondLineStart = firstNewline + 1;
        int secondNewline = message.indexOf('\n', secondLineStart);
        String line2 = secondNewline >= 0
                ? message.substring(secondLineStart, secondNewline)
                : message.substring(secondLineStart);
        line2 = line2.trim();
        if (!line2.startsWith("{")) {
            return defaults;
        }
        try {
            LlmSimParamsJson parsed = JSON.readValue(line2, LlmSimParamsJson.class);
            return new LlmSimParams(
                    parsed.thinkMs >= 0 ? parsed.thinkMs : defaults.thinkMs(),
                    parsed.chunkCount > 0 ? parsed.chunkCount : defaults.chunkCount(),
                    parsed.chunkBytes > 0 ? parsed.chunkBytes : defaults.chunkBytes(),
                    Math.max(0, parsed.interChunkMs));
        } catch (Exception e) {
            return defaults;
        }
    }

    static FanoutParams parseFanoutParams(String message) {
        FanoutParams defaults = FanoutParams.defaults();
        if (message == null) {
            return defaults;
        }
        int firstNewline = message.indexOf('\n');
        if (firstNewline < 0) {
            return defaults;
        }
        int secondLineStart = firstNewline + 1;
        int secondNewline = message.indexOf('\n', secondLineStart);
        String line2 = secondNewline >= 0
                ? message.substring(secondLineStart, secondNewline)
                : message.substring(secondLineStart);
        line2 = line2.trim();
        if (!line2.startsWith("{")) {
            return defaults;
        }
        try {
            FanoutParamsJson parsed = JSON.readValue(line2, FanoutParamsJson.class);
            return new FanoutParams(
                    parsed.chunkCount > 0 ? parsed.chunkCount : defaults.chunkCount(),
                    parsed.chunkBytes > 0 ? parsed.chunkBytes : defaults.chunkBytes(),
                    Math.max(0, parsed.interChunkMs),
                    defaults.fanoutConsumers());
        } catch (Exception e) {
            return defaults;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class LlmSimParamsJson {
        public int thinkMs;
        public int chunkCount;
        public int chunkBytes;
        public int interChunkMs;
    }

    record LlmSimParams(int thinkMs, int chunkCount, int chunkBytes, int interChunkMs) {
        static LlmSimParams defaults() {
            return new LlmSimParams(500, 50, 512, 100);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class FanoutParamsJson {
        public int chunkCount;
        public int chunkBytes;
        public int interChunkMs;
        public int fanoutConsumers;
    }

    record FanoutParams(int chunkCount, int chunkBytes, int interChunkMs, int fanoutConsumers) {
        static FanoutParams defaults() {
            return new FanoutParams(
                    FANOUT_DEFAULT_CHUNK_COUNT,
                    FANOUT_DEFAULT_CHUNK_BYTES,
                    FANOUT_DEFAULT_INTER_CHUNK_MS,
                    1);
        }
    }

    static String resultPayload() {
        return fixedSizeUtf8("A2A-BENCH-RESULT-", RESULT_BYTES);
    }

    private static String fixedSizeUtf8(String prefix, int totalBytes) {
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length < totalBytes) {
            sb.append(FILL_PATTERN);
        }
        String candidate = sb.toString();
        while (candidate.getBytes(StandardCharsets.UTF_8).length > totalBytes) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        while (candidate.getBytes(StandardCharsets.UTF_8).length < totalBytes) {
            candidate = candidate + "x";
        }
        return candidate;
    }

    private void publishStatusNoFlush(String replyTopic, String taskId, TaskState state, String text, boolean terminal) {
        TaskStatus status = new TaskStatus();
        status.setState(state);
        status.setMessage(CountdownParser.agentMessage(text));
        status.setTimestamp(Instant.now().toString());

        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(taskId);
        event.setStatus(status);
        event.setFinal(terminal);
        producer.sendStatusUpdate(event, replyTopic);
    }

    private void publishArtifactNoFlush(String replyTopic, String taskId, String resultText, String artifactId) {
        Artifact artifact = new Artifact();
        artifact.setArtifactId(artifactId);
        artifact.setName(artifactId);
        artifact.setParts(List.of(new TextPart(resultText)));

        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(taskId);
        event.setArtifact(artifact);
        producer.sendArtifactUpdate(event, replyTopic);
    }

    private void publishStatus(String replyTopic, String taskId, TaskState state, String text, boolean terminal) {
        publishStatusNoFlush(replyTopic, taskId, state, text, terminal);
        producer.flush();
    }

    private void publishArtifact(String replyTopic, String taskId, String resultText) {
        publishArtifactNoFlush(replyTopic, taskId, resultText, "bench-result");
        producer.flush();
    }

    private void publishTaskResult(String replyTopic, String taskId, String resultText) {
        TaskStatus status = new TaskStatus();
        status.setState(TaskState.COMPLETED);
        status.setMessage(CountdownParser.agentMessage("bench:rr complete"));
        status.setTimestamp(Instant.now().toString());

        Artifact artifact = new Artifact();
        artifact.setArtifactId("bench-result");
        artifact.setName("bench-result");
        artifact.setParts(List.of(new TextPart(resultText)));

        Task task = new Task();
        task.setId(taskId);
        task.setContextId(taskId);
        task.setStatus(status);
        task.setArtifacts(List.of(artifact));
        producer.sendTaskResult(task, UUID.randomUUID().toString(), replyTopic);
        producer.flush();
    }
}
