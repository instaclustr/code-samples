package local.kip1034.dlq.news.freeform;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import local.kip1034.dlq.news.NewsItem;
import local.kip1034.dlq.news.NewsJson;
import local.kip1034.dlq.news.NewsStreamPayload;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.common.serialization.Serdes;

/**
 * Merge raw + repaired {@link NewsItem} streams (strict JSON serde at the topic edge). Invalid JSON / free-form prose
 * never enters this graph: it is emitted to the KIP-1034 DLQ by the framework. Missing categories are routed to {@link
 * NewsFreeformTopics#APP_DLQ} as JSON envelopes. Valid items are exploded by category and windowed.
 */
public final class NewsFreeformTopology {

    public static void build(StreamsBuilder b) {
        JsonNewsItemSerde newsSerde = new JsonNewsItemSerde();
        Consumed<String, NewsItem> consumed = Consumed.with(Serdes.String(), newsSerde);

        KStream<String, NewsItem> raw = b.stream(NewsFreeformTopics.RAW, consumed);
        KStream<String, NewsItem> repaired = b.stream(NewsFreeformTopics.REPAIRED, consumed);
        KStream<String, NewsItem> merged = raw.merge(repaired);

        Map<String, KStream<String, NewsItem>> branches =
                merged.split(Named.as("ff-"))
                        .branch((k, item) -> item != null && !item.hasAnyCategory(), Branched.as("missing-categories"))
                        .defaultBranch(Branched.as("ok"));

        branches
                .get("ff-missing-categories")
                .mapValues(
                        item -> {
                            try {
                                String json = NewsJson.stringify(item);
                                return NewsJson.dlqEnvelope(
                                        "MISSING_CATEGORIES",
                                        item.articleId == null ? "" : item.articleId,
                                        json,
                                        "categories empty or all blank");
                            } catch (JsonProcessingException e) {
                                return NewsJson.dlqEnvelope(
                                        "MISSING_CATEGORIES", "", "", e.getMessage());
                            }
                        })
                .to(NewsFreeformTopics.APP_DLQ, Produced.with(Serdes.String(), Serdes.String()));

        KStream<String, NewsItem> ok = branches.get("ff-ok");

        ok.flatMap(
                        (articleKey, item) -> {
                            List<KeyValue<String, String>> out = new ArrayList<>();
                            if (item.categories == null) {
                                return out;
                            }
                            for (String cat : item.categories) {
                                if (cat == null || cat.isBlank()) {
                                    continue;
                                }
                                String catKey = cat.trim().toLowerCase(Locale.ROOT);
                                String payload;
                                try {
                                    payload =
                                            NewsJson.stringify(
                                                    new NewsStreamPayload(
                                                            item.articleId, item.heading, catKey));
                                } catch (JsonProcessingException e) {
                                    continue;
                                }
                                out.add(KeyValue.pair(catKey, payload));
                            }
                            return out;
                        })
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(10)))
                .aggregate(
                        () -> "",
                        (catKey, payload, agg) -> agg.isEmpty() ? payload : agg + "|" + payload,
                        Materialized.with(Serdes.String(), Serdes.String()))
                .toStream()
                .map(
                        (windowedKey, agg) ->
                                KeyValue.pair(
                                        windowedKey.key(),
                                        windowedKey.window().start()
                                                + ":"
                                                + windowedKey.window().end()
                                                + ":"
                                                + agg))
                .to(NewsFreeformTopics.WINDOWED, Produced.with(Serdes.String(), Serdes.String()));
    }

    private NewsFreeformTopology() {}
}
