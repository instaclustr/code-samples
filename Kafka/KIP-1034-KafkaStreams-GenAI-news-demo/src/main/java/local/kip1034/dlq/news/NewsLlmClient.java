package local.kip1034.dlq.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Calls local Ollama for category inference, plain-text structuring, and synthetic article batches. */
public final class NewsLlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** Upper bound per {@link #generateSyntheticArticles} call (single Ollama prompt). */
    private static final int MAX_SYNTH_BATCH = 40;

    public static int maxSyntheticBatchSize() {
        return MAX_SYNTH_BATCH;
    }

    /**
     * Returns JSON text for a full {@link NewsItem} (same keys as input). Uses {@code OLLAMA_URL} (default {@code
     * http://localhost:11434}) and {@code OLLAMA_MODEL} (default {@code llama3:latest}).
     */
    public static Optional<String> inferCategoriesJson(NewsItem item) {
        String input;
        try {
            input = NewsJson.stringify(item);
        } catch (Exception e) {
            return Optional.empty();
        }

        String prompt =
                "You label news for downstream topic grouping. Given JSON with keys articleId, heading, article, "
                        + "categories (array, may be empty). Output ONLY one JSON object with the SAME four keys. "
                        + "Fill categories with 1 to 6 short topical tags in English (nouns or short phrases, "
                        + "lowercase is fine). Do not copy the heading verbatim as the only tag. "
                        + "No markdown, no commentary.\n\nInput:\n"
                        + input;
        return ollamaGenerate(prompt, Duration.ofSeconds(120)).map(NewsLlmClient::stripMarkdownFence);
    }

    /**
     * Turns arbitrary prose (or malformed JSON text) into a single {@link NewsItem} as JSON text. Used when KIP-1034
     * DLQ carries raw UTF-8 bytes that are not valid {@link NewsItem} JSON.
     */
    public static Optional<String> structurePlainTextToNewsItemJson(String articleIdHint, String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return Optional.empty();
        }
        String idRule =
                (articleIdHint == null || articleIdHint.isBlank())
                        ? "Use articleId as a short unique string (e.g. gen- plus random alphanumeric)."
                        : "Use exactly this articleId string: " + articleIdHint.trim();
        String prompt =
                "You structure news text for a Kafka Streams pipeline. Output ONLY one JSON object with keys "
                        + "articleId (string), heading (one line, <=120 chars), article (the story; you may lightly "
                        + "edit the source text for clarity), categories (array of 1-5 short topical English tags). "
                        + idRule
                        + " Your entire reply MUST start with { as the first non-whitespace character — no preamble, "
                        + "no markdown fences, no \"Here is\" text.\n\nSource text:\n"
                        + plainText.trim();
        return ollamaGenerate(prompt, Duration.ofSeconds(240))
                .map(
                        r -> {
                            String t = stripMarkdownFence(r);
                            return isolateLeadingJson(t);
                        });
    }

    /**
     * Asks the model for {@code count} distinct synthetic news rows. Uses the same Ollama env vars as {@link
     * #inferCategoriesJson}. Parsing tolerates a top-level JSON array, or an object with an {@code articles} array, or
     * a single object. Missing {@code articleId} values are filled with {@code gen-}<i>uuid</i>.
     */
    public static List<NewsItem> generateSyntheticArticles(int count, String themeHint) {
        int n = Math.min(Math.max(count, 1), MAX_SYNTH_BATCH);
        String theme =
                themeHint == null || themeHint.isBlank()
                        ? "mixed beats worldwide (science, business, local politics, sport, culture, environment)"
                        : themeHint.trim();

        String prompt =
                "You invent plausible but fictional news briefs for a Kafka Streams QA demo. "
                        + "Output ONLY a JSON array of exactly "
                        + n
                        + " objects. No markdown fences, no text before or after the array. "
                        + "Each object MUST have keys: articleId (string, unique across the array), "
                        + "heading (one line, <=120 chars), article (1–4 sentences), "
                        + "categories (array of 1–5 short topical English tags; invent tags freely, no fixed list). "
                        + "Vary tone and region; avoid repeating the same headline pattern. "
                        + "Bias topics toward: "
                        + theme
                        + ".";

        Optional<String> raw = ollamaGenerate(prompt, Duration.ofSeconds(420));
        if (raw.isEmpty()) {
            return List.of();
        }
        return parseSyntheticArticles(stripMarkdownFence(raw.get()));
    }

    private static Optional<String> ollamaGenerate(String prompt, Duration readTimeout) {
        String base = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "llama3:latest");
        String body;
        try {
            body = MAPPER.writeValueAsString(java.util.Map.of("model", model, "prompt", prompt, "stream", false));
        } catch (Exception e) {
            return Optional.empty();
        }

        try {
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/generate"))
                            .timeout(readTimeout)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(resp.body());
            String text = root.path("response").asText("").trim();
            if (!text.isEmpty()) {
                return Optional.of(text);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static List<NewsItem> parseSyntheticArticles(String jsonText) {
        List<NewsItem> out = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(isolateLeadingJson(jsonText));
            if (root.isArray()) {
                addItemsFromArray(out, root);
            } else if (root.isObject()) {
                JsonNode arr = root.get("articles");
                if (arr != null && arr.isArray()) {
                    addItemsFromArray(out, arr);
                } else {
                    NewsItem one = MAPPER.treeToValue(root, NewsItem.class);
                    if (one != null && one.heading != null && !one.heading.isBlank()) {
                        ensureId(one);
                        out.add(one);
                    }
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }

    private static String isolateLeadingJson(String t) {
        String s = t.trim();
        if (s.startsWith("[") || s.startsWith("{")) {
            return s;
        }
        int arr = s.indexOf('[');
        int obj = s.indexOf('{');
        if (arr >= 0 && (obj < 0 || arr <= obj)) {
            return s.substring(arr);
        }
        if (obj >= 0) {
            return s.substring(obj);
        }
        return s;
    }

    private static void addItemsFromArray(List<NewsItem> out, JsonNode array) {
        for (JsonNode el : array) {
            try {
                NewsItem it = MAPPER.treeToValue(el, NewsItem.class);
                if (it != null && it.heading != null && !it.heading.isBlank()) {
                    ensureId(it);
                    if (it.categories == null) {
                        it.categories = new ArrayList<>();
                    }
                    out.add(it);
                }
            } catch (Exception ignored) {
                // skip malformed element
            }
        }
    }

    private static void ensureId(NewsItem it) {
        if (it.articleId == null || it.articleId.isBlank()) {
            it.articleId = "gen-" + UUID.randomUUID();
        }
    }

    private static String stripMarkdownFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int fence = t.lastIndexOf("```");
            if (fence >= 0) {
                t = t.substring(0, fence).trim();
            }
        }
        return t.trim();
    }

    private NewsLlmClient() {}
}
