package local.kip1034.dlq.news.freeform;

import java.util.ArrayList;
import java.util.List;
import local.kip1034.dlq.news.NewsItem;
import local.kip1034.dlq.news.NewsJson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;

/**
 * Deterministic payloads on {@link NewsFreeformTopics#RAW} for DLQ exercises: missing categories (app DLQ) and
 * non-JSON text (KIP-1034 serde DLQ).
 */
public final class NewsFreeformFixtureEdgeCases {

    public static ProducerRecord<String, String> missingCategoriesArticle() throws Exception {
        return rec(
                item(
                        "ff-fixture-missing",
                        "City council delays vote",
                        "Members postponed the housing plan until next month.",
                        List.of()));
    }

    public static ProducerRecord<String, String> nonJsonProseOrBadJson() {
        return new ProducerRecord<>(NewsFreeformTopics.RAW, "ff-fixture-bad-json", "{ not json");
    }

    /**
     * After LLM-generated articles, appends rows for {@code MISSING_CATEGORIES} (app DLQ) and serde failure (KIP-1034
     * DLQ).
     */
    public static void publishDlqExerciseOnly(KafkaProducer<String, String> p, Logger log) throws Exception {
        ProducerRecord<String, String> missing = missingCategoriesArticle();
        ProducerRecord<String, String> bad = nonJsonProseOrBadJson();
        p.send(missing);
        log.info("Sent fixture app-DLQ missing-categories key={} preview={}", missing.key(), preview(missing.value()));
        p.send(bad);
        log.info("Sent fixture KIP-1034 serde failure key={}", bad.key());
        p.flush();
    }

    private static NewsItem item(String id, String heading, String article, List<String> cats) {
        NewsItem n = new NewsItem();
        n.articleId = id;
        n.heading = heading;
        n.article = article;
        n.categories = new ArrayList<>(cats);
        return n;
    }

    private static ProducerRecord<String, String> rec(NewsItem item) throws Exception {
        return new ProducerRecord<>(NewsFreeformTopics.RAW, item.articleId, NewsJson.stringify(item));
    }

    private static String preview(String v) {
        if (v.length() < 100) {
            return v;
        }
        return v.substring(0, 100) + "…";
    }

    private NewsFreeformFixtureEdgeCases() {}
}
