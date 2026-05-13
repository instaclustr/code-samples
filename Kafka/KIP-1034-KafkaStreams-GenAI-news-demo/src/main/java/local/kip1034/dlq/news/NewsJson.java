package local.kip1034.dlq.news;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class NewsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static NewsItem parse(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, NewsItem.class);
    }

    public static String stringify(NewsItem item) throws JsonProcessingException {
        return MAPPER.writeValueAsString(item);
    }

    public static String stringify(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }

    public static String dlqEnvelope(String reason, String articleId, String rawJson, String detail) {
        try {
            return MAPPER.writeValueAsString(
                    java.util.Map.of(
                            "reason",
                            reason,
                            "articleId",
                            articleId == null ? "" : articleId,
                            "rawJson",
                            rawJson == null ? "" : rawJson,
                            "detail",
                            detail == null ? "" : detail));
        } catch (JsonProcessingException e) {
            return "{\"reason\":\"" + reason + "\",\"rawJson\":\"\"}";
        }
    }

    private NewsJson() {}
}
