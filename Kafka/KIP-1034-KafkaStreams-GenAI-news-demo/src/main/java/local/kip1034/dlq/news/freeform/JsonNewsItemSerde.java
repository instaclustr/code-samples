package local.kip1034.dlq.news.freeform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import local.kip1034.dlq.news.NewsItem;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Strict JSON serde for {@link NewsItem}. Invalid JSON or wrong shape throws {@link SerializationException} so {@link
 * org.apache.kafka.streams.errors.LogAndContinueExceptionHandler} can emit a KIP-1034 DLQ row. Plain prose on the wire
 * therefore fails deserialization.
 */
public final class JsonNewsItemSerde implements Serde<NewsItem> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Serializer<NewsItem> serializer() {
        return new Serializer<>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {}

            @Override
            public byte[] serialize(String topic, NewsItem data) {
                if (data == null) {
                    return null;
                }
                try {
                    return MAPPER.writeValueAsBytes(data);
                } catch (Exception e) {
                    throw new SerializationException("Failed to serialize NewsItem", e);
                }
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public Deserializer<NewsItem> deserializer() {
        return new Deserializer<>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {}

            @Override
            public NewsItem deserialize(String topic, byte[] data) {
                if (data == null) {
                    return null;
                }
                try {
                    return MAPPER.readValue(data, NewsItem.class);
                } catch (Exception e) {
                    throw new SerializationException(
                            "Failed to deserialize NewsItem: " + new String(data, StandardCharsets.UTF_8), e);
                }
            }

            @Override
            public void close() {}
        };
    }

    public static Serde<NewsItem> serde() {
        JsonNewsItemSerde s = new JsonNewsItemSerde();
        return Serdes.serdeFrom(s.serializer(), s.deserializer());
    }
}
