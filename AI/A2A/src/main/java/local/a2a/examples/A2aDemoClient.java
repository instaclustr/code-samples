package local.a2a.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public final class A2aDemoClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("A2A_DEMO_BASE_URL", "http://localhost:8080");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        fetchAndPrintCard(http, baseUrl);
        runSynchronousTimeExample(http, baseUrl);
        runAsyncCountdownExample(http, baseUrl, 60);
        runInputRequiredCountdownExample(http, baseUrl, 20);
    }

    private static void fetchAndPrintCard(HttpClient http, String baseUrl) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(baseUrl + "/.well-known/agent.json")).GET().build();
        JsonNode card = readJson(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        System.out.println("\n=== Agent Card ===");
        System.out.println("name: " + card.path("name").asText());
        System.out.println("skills: " + card.path("skills").size());
    }

    private static void runSynchronousTimeExample(HttpClient http, String baseUrl) throws Exception {
        System.out.println("\n=== Example 1: Synchronous Time Service ===");
        JsonNode result = sendMessage(http, baseUrl, "What is the current time?");
        JsonNode message = result.path("message");
        String text = extractText(message);
        System.out.println("Response: " + text);
    }

    private static void runAsyncCountdownExample(HttpClient http, String baseUrl, int seconds) throws Exception {
        System.out.println("\n=== Example 2: Async Countdown Timer ===");
        JsonNode result = sendMessage(http, baseUrl, "Count down " + seconds + " seconds");
        JsonNode task = result.path("task");
        if (task.isMissingNode()) {
            System.out.println("Expected task response but got: " + result);
            return;
        }

        String taskId = task.path("id").asText();
        System.out.println("Task created: " + taskId);

        while (true) {
            Thread.sleep(5000);
            JsonNode taskNow = getTask(http, baseUrl, taskId);
            String state = taskNow.path("status").path("state").asText();
            String statusText = extractText(taskNow.path("status").path("message"));
            System.out.println("state=" + state + " | " + statusText);

            if (isTerminal(state)) {
                JsonNode artifacts = taskNow.path("artifacts");
                if (artifacts.isArray() && !artifacts.isEmpty()) {
                    String finalText = extractText(artifacts.get(0));
                    System.out.println("Final artifact: " + finalText);
                }
                break;
            }
        }
    }

    private static void runInputRequiredCountdownExample(HttpClient http, String baseUrl, int seconds) throws Exception {
        System.out.println("\n=== Example 3: Input-Required Countdown ===");
        JsonNode result = sendMessage(http, baseUrl, "Count down " + seconds + " seconds with confirm");
        JsonNode task = result.path("task");
        if (task.isMissingNode()) {
            System.out.println("Expected task response but got: " + result);
            return;
        }
        String taskId = task.path("id").asText();
        String initialState = task.path("status").path("state").asText();
        String prompt = extractText(task.path("status").path("message"));
        System.out.println("Task created: " + taskId);
        System.out.println("state=" + initialState + " | " + prompt);

        JsonNode confirmResult = sendMessageForTask(http, baseUrl, taskId, "confirm");
        JsonNode confirmedTask = confirmResult.path("task");
        System.out.println("After confirm: state=" + confirmedTask.path("status").path("state").asText()
                + " | " + extractText(confirmedTask.path("status").path("message")));

        while (true) {
            Thread.sleep(5000);
            JsonNode taskNow = getTask(http, baseUrl, taskId);
            String state = taskNow.path("status").path("state").asText();
            String statusText = extractText(taskNow.path("status").path("message"));
            System.out.println("state=" + state + " | " + statusText);
            if (isTerminal(state)) {
                JsonNode artifacts = taskNow.path("artifacts");
                if (artifacts.isArray() && !artifacts.isEmpty()) {
                    String finalText = extractText(artifacts.get(0));
                    System.out.println("Final artifact: " + finalText);
                }
                break;
            }
        }
    }

    private static JsonNode sendMessage(HttpClient http, String baseUrl, String text) throws Exception {
        ObjectNode request = rpcEnvelope("SendMessage");
        ObjectNode params = request.putObject("params");
        ObjectNode message = params.putObject("message");
        message.put("messageId", "msg-" + UUID.randomUUID());
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("kind", "text").put("text", text);
        return rpc(http, baseUrl, request).path("result");
    }

    private static JsonNode sendMessageForTask(HttpClient http, String baseUrl, String taskId, String text) throws Exception {
        ObjectNode request = rpcEnvelope("SendMessage");
        ObjectNode params = request.putObject("params");
        params.put("taskId", taskId);
        ObjectNode message = params.putObject("message");
        message.put("messageId", "msg-" + UUID.randomUUID());
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("kind", "text").put("text", text);
        return rpc(http, baseUrl, request).path("result");
    }

    private static JsonNode getTask(HttpClient http, String baseUrl, String taskId) throws Exception {
        ObjectNode request = rpcEnvelope("GetTask");
        request.putObject("params").put("taskId", taskId);
        return rpc(http, baseUrl, request).path("result");
    }

    private static JsonNode rpc(HttpClient http, String baseUrl, ObjectNode payload) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(baseUrl + "/rpc"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                        .build();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = readJson(response.body());
        if (json.has("error")) {
            throw new IOException("RPC error: " + json.get("error").toString());
        }
        return json;
    }

    private static ObjectNode rpcEnvelope(String method) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", "req-" + UUID.randomUUID());
        node.put("method", method);
        return node;
    }

    private static String extractText(JsonNode node) {
        JsonNode parts = node.path("parts");
        if (parts.isArray() && !parts.isEmpty()) {
            return parts.get(0).path("text").asText("");
        }
        JsonNode artifactParts = node.path("parts");
        if (artifactParts.isArray() && !artifactParts.isEmpty()) {
            return artifactParts.get(0).path("text").asText("");
        }
        return "";
    }

    private static boolean isTerminal(String state) {
        return "completed".equals(state) || "failed".equals(state) || "canceled".equals(state) || "rejected".equals(state);
    }

    private static JsonNode readJson(String body) throws IOException {
        return MAPPER.readTree(body);
    }

    private A2aDemoClient() {}
}
