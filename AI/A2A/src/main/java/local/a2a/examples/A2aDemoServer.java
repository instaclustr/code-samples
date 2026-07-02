package local.a2a.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class A2aDemoServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final Map<String, CountdownTask> TASKS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMER = Executors.newScheduledThreadPool(2);

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("A2A_DEMO_PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/.well-known/agent.json", A2aDemoServer::handleAgentCard);
        server.createContext("/rpc", A2aDemoServer::handleRpc);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Clockwork Agent running on http://localhost:" + port);
        System.out.println("Agent card: http://localhost:" + port + "/.well-known/agent.json");
        System.out.println("RPC endpoint: http://localhost:" + port + "/rpc");
    }

    private static void handleAgentCard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("method_not_allowed", "GET only"));
            return;
        }

        ObjectNode card = MAPPER.createObjectNode();
        card.put("name", "Clockwork Agent");
        card.put("description", "Deterministic time-and-timer agent: synchronous time, async countdown, and input-required confirm flow");
        card.put("url", "http://localhost:8080/rpc");
        card.put("version", "1.0.0");
        card.put("protocolVersion", "1.0");
        ObjectNode capabilities = card.putObject("capabilities");
        capabilities.put("streaming", false);
        capabilities.put("pushNotifications", false);

        ArrayNode skills = card.putArray("skills");
        ObjectNode timeSkill = skills.addObject();
        timeSkill.put("id", "current-time");
        timeSkill.put("name", "Current Time");
        timeSkill.put("description", "Returns current time synchronously.");

        ObjectNode countdownSkill = skills.addObject();
        countdownSkill.put("id", "countdown");
        countdownSkill.put("name", "Countdown Timer");
        countdownSkill.put("description", "Counts down asynchronously and exposes progress via GetTask.");

        ObjectNode gatedCountdownSkill = skills.addObject();
        gatedCountdownSkill.put("id", "countdown-confirm");
        gatedCountdownSkill.put("name", "Countdown with Confirmation");
        gatedCountdownSkill.put("description", "Returns input-required first, then starts after confirmation.");

        ArrayNode inModes = card.putArray("defaultInputModes");
        inModes.add("text/plain");
        ArrayNode outModes = card.putArray("defaultOutputModes");
        outModes.add("text/plain");

        sendJson(exchange, 200, card);
    }

    private static void handleRpc(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("method_not_allowed", "POST only"));
            return;
        }

        JsonNode request = MAPPER.readTree(exchange.getRequestBody());
        String method = request.path("method").asText("");
        JsonNode id = request.get("id");
        JsonNode params = request.path("params");

        ObjectNode response;
        switch (method) {
            case "SendMessage" -> response = success(id, handleSendMessage(params));
            case "GetTask" -> response = success(id, handleGetTask(params));
            case "CancelTask" -> response = success(id, handleCancelTask(params));
            default -> response = jsonRpcError(id, -32601, "Method not found: " + method);
        }
        sendJson(exchange, 200, response);
    }

    private static JsonNode handleSendMessage(JsonNode params) {
        String text = extractText(params.path("message"));
        String normalized = text.toLowerCase();
        String taskId = params.path("taskId").asText("");

        if (normalized.contains("time")) {
            return immediateMessage("The current time is " + TIME_FORMAT.format(Instant.now()) + ".");
        }

        if (!taskId.isBlank() && normalized.contains("confirm")) {
            CountdownTask existing = TASKS.get(taskId);
            if (existing != null && existing.confirmAndStart()) {
                ObjectNode result = MAPPER.createObjectNode();
                result.set("task", existing.toTaskNode());
                return result;
            }
        }

        int countdownSeconds = parseCountdownSeconds(normalized);
        if (countdownSeconds > 0) {
            if (normalized.contains("confirm")) {
                return createConfirmRequiredCountdownTask(countdownSeconds);
            }
            return createCountdownTask(countdownSeconds);
        }

        return immediateMessage(
                "I did not understand. Try 'what time is it?', 'count down 60 seconds', or 'count down 20 seconds with confirm'.");
    }

    private static JsonNode handleGetTask(JsonNode params) {
        String taskId = params.path("taskId").asText("");
        CountdownTask task = TASKS.get(taskId);
        if (task == null) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("error", "Task not found");
            return node;
        }
        return task.toTaskNode();
    }

    private static JsonNode handleCancelTask(JsonNode params) {
        String taskId = params.path("taskId").asText("");
        CountdownTask task = TASKS.get(taskId);
        if (task == null) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("error", "Task not found");
            return node;
        }
        task.cancel();
        return task.toTaskNode();
    }

    private static ObjectNode immediateMessage(String text) {
        ObjectNode message = MAPPER.createObjectNode();
        message.put("kind", "message");
        message.put("messageId", "msg-" + UUID.randomUUID());
        message.put("role", "agent");
        ArrayNode parts = message.putArray("parts");
        ObjectNode part = parts.addObject();
        part.put("kind", "text");
        part.put("text", text);

        ObjectNode result = MAPPER.createObjectNode();
        result.set("message", message);
        return result;
    }

    private static ObjectNode createCountdownTask(int countdownSeconds) {
        String taskId = "task-" + UUID.randomUUID();
        CountdownTask task = new CountdownTask(taskId, countdownSeconds, false);
        TASKS.put(taskId, task);
        task.start();

        ObjectNode result = MAPPER.createObjectNode();
        result.set("task", task.toTaskNode());
        return result;
    }

    private static ObjectNode createConfirmRequiredCountdownTask(int countdownSeconds) {
        String taskId = "task-" + UUID.randomUUID();
        CountdownTask task = new CountdownTask(taskId, countdownSeconds, true);
        TASKS.put(taskId, task);

        ObjectNode result = MAPPER.createObjectNode();
        result.set("task", task.toTaskNode());
        return result;
    }

    private static int parseCountdownSeconds(String normalized) {
        String[] tokens = normalized.replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches("\\d+")) {
                int value = Integer.parseInt(tokens[i]);
                if (i + 1 < tokens.length && tokens[i + 1].startsWith("min")) {
                    return value * 60;
                }
                return value;
            }
        }
        return 0;
    }

    private static String extractText(JsonNode message) {
        for (JsonNode part : message.path("parts")) {
            if ("text".equals(part.path("kind").asText())) {
                return part.path("text").asText("");
            }
        }
        return "";
    }

    private static ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.set("id", id == null ? MAPPER.nullNode() : id);
        node.set("result", result);
        return node;
    }

    private static ObjectNode jsonRpcError(JsonNode id, int code, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.set("id", id == null ? MAPPER.nullNode() : id);
        ObjectNode err = node.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return node;
    }

    private static ObjectNode error(String code, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("error", code);
        node.put("message", message);
        return node;
    }

    private static void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class CountdownTask {
        private final String id;
        private final int totalSeconds;
        private volatile int remainingSeconds;
        private volatile String state;
        private volatile String statusMessage;
        private volatile String finalArtifact;
        private volatile boolean canceled;
        private final boolean requireConfirmation;

        CountdownTask(String id, int totalSeconds, boolean requireConfirmation) {
            this.id = id;
            this.totalSeconds = totalSeconds;
            this.remainingSeconds = totalSeconds;
            this.requireConfirmation = requireConfirmation;
            if (requireConfirmation) {
                this.state = "input-required";
                this.statusMessage = "Please confirm countdown start for " + totalSeconds + " seconds.";
            } else {
                this.state = "submitted";
                this.statusMessage = "Timer accepted for " + totalSeconds + " seconds.";
            }
        }

        void start() {
            this.state = "working";
            this.statusMessage = "Countdown started: " + remainingSeconds + "s remaining.";
            TIMER.scheduleAtFixedRate(
                    () -> {
                        if (canceled || isTerminal()) {
                            return;
                        }
                        remainingSeconds -= 10;
                        if (remainingSeconds > 0) {
                            statusMessage = "Countdown update: " + remainingSeconds + "s remaining.";
                        } else {
                            remainingSeconds = 0;
                            state = "completed";
                            statusMessage = "Countdown completed at " + TIME_FORMAT.format(Instant.now()) + ".";
                            finalArtifact = statusMessage;
                        }
                    },
                    10,
                    10,
                    TimeUnit.SECONDS);
        }

        void cancel() {
            if (!isTerminal()) {
                canceled = true;
                state = "canceled";
                statusMessage = "Countdown canceled with " + remainingSeconds + "s remaining.";
            }
        }

        boolean confirmAndStart() {
            if (!requireConfirmation || !"input-required".equals(state) || canceled || isTerminal()) {
                return false;
            }
            start();
            return true;
        }

        boolean isTerminal() {
            return "completed".equals(state) || "failed".equals(state) || "canceled".equals(state) || "rejected".equals(state);
        }

        ObjectNode toTaskNode() {
            ObjectNode task = MAPPER.createObjectNode();
            task.put("kind", "task");
            task.put("id", id);
            task.put("contextId", "ctx-" + id);
            ObjectNode status = task.putObject("status");
            status.put("state", state);
            ObjectNode msg = status.putObject("message");
            msg.put("role", "agent");
            ArrayNode parts = msg.putArray("parts");
            parts.addObject().put("kind", "text").put("text", statusMessage);
            status.put("timestamp", Instant.now().toString());

            if (finalArtifact != null) {
                ArrayNode artifacts = task.putArray("artifacts");
                ObjectNode artifact = artifacts.addObject();
                artifact.put("artifactId", "artifact-" + id);
                artifact.put("name", "countdown-result");
                ArrayNode artifactParts = artifact.putArray("parts");
                artifactParts.addObject().put("kind", "text").put("text", finalArtifact);
            }
            return task;
        }
    }

    private A2aDemoServer() {}
}
