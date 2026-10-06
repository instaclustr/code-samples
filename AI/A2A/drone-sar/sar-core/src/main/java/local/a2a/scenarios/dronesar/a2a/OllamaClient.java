package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal client for Ollama {@code /api/generate} (non-streaming). */
public final class OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient http;
    private final URI generateUri;
    private final String model;

    public OllamaClient(String baseUrl, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.generateUri = URI.create(normalized + "/api/generate");
        this.model = model;
    }

    public String generate(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("system", systemPrompt);
        body.put("prompt", userPrompt);
        body.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(generateUri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = MAPPER.readTree(response.body());
        String text = node.path("response").asText("").trim();
        if (text.isEmpty()) {
            throw new IOException("Ollama returned empty response");
        }
        return text;
    }
}
