package local.kip1034.dlq.news.freeform;

import java.util.List;
import java.util.Properties;
import local.kip1034.dlq.news.NewsItem;
import local.kip1034.dlq.news.NewsJson;
import local.kip1034.dlq.news.NewsLlmClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses Ollama ({@link NewsLlmClient#generateSyntheticArticles}) to invent {@code N} synthetic {@link NewsItem} rows
 * and publishes JSON to {@link NewsFreeformTopics#RAW}. Count defaults to {@code NEWS_RANDOM_COUNT} or {@code 10};
 * optional theme from {@code NEWS_GEN_THEME} or second CLI argument.
 *
 * <p>Set {@code NEWS_APPEND_FIXTURE_EDGE_CASES=true} to append deterministic missing-categories JSON and a
 * non-JSON value (serde failure → KIP-1034 DLQ).
 *
 * <p>Example: {@code NEWS_RANDOM_COUNT=5 mvn -q exec:java
 * -Dexec.mainClass=local.kip1034.dlq.news.freeform.NewsFreeformRandomLlmProducerMain}
 */
public final class NewsFreeformRandomLlmProducerMain {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformRandomLlmProducerMain.class);

    public static void main(String[] args) throws Exception {
        int count = parseCount(System.getenv().getOrDefault("NEWS_RANDOM_COUNT", "10"), 10);
        String theme = System.getenv().getOrDefault("NEWS_GEN_THEME", "");
        if (args.length >= 1) {
            count = parseCount(args[0], count);
        }
        if (args.length >= 2) {
            theme = args[1];
        }

        log.info("Requesting {} synthetic article(s) from LLM (theme hint: {})", count, theme.isBlank() ? "(default)" : theme);

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        int maxBatch = NewsLlmClient.maxSyntheticBatchSize();
        int totalLlmSent = 0;
        int round = 0;
        final int maxRounds = 200;

        boolean appendDlqFixtures = appendFixtureEdgeCases();
        try (KafkaProducer<String, String> p = new KafkaProducer<>(props)) {
            while (totalLlmSent < count && round < maxRounds) {
                round++;
                int chunk = Math.min(maxBatch, count - totalLlmSent);
                String batchTheme =
                        theme.isBlank()
                                ? "batch " + round + " of a multi-call run; use unique articleId values never reused before."
                                : theme
                                        + " (batch "
                                        + round
                                        + "; unique articleIds; vary regions and beats from prior batches.)";
                log.info("LLM round {}: requesting {} article(s), {} total so far / {}", round, chunk, totalLlmSent, count);
                List<NewsItem> items = NewsLlmClient.generateSyntheticArticles(chunk, batchTheme);
                if (items.isEmpty()) {
                    log.warn("LLM round {} returned no parseable articles; stopping early.", round);
                    break;
                }
                for (NewsItem item : items) {
                    if (totalLlmSent >= count) {
                        break;
                    }
                    String json = NewsJson.stringify(item);
                    p.send(new ProducerRecord<>(NewsFreeformTopics.RAW, item.articleId, json));
                    totalLlmSent++;
                    if (totalLlmSent <= 5 || totalLlmSent % 25 == 0 || totalLlmSent == count) {
                        log.info("Sent key={} heading={} ({} of {})", item.articleId, item.heading, totalLlmSent, count);
                    }
                }
                p.flush();
                if (totalLlmSent >= count) {
                    break;
                }
            }
            if (appendDlqFixtures) {
                log.info("Appending fixture DLQ edge cases (missing categories + serde failure)");
                NewsFreeformFixtureEdgeCases.publishDlqExerciseOnly(p, log);
            } else {
                p.flush();
            }
        }

        if (totalLlmSent == 0) {
            log.error(
                    "LLM returned no parseable articles. Is Ollama running at OLLAMA_URL? Model OLLAMA_MODEL? "
                            + "Try lowering NEWS_RANDOM_COUNT.");
            System.exit(1);
        }
        if (totalLlmSent < count) {
            log.warn("Published {} LLM article(s), fewer than requested {}", totalLlmSent, count);
        }
        int total = totalLlmSent + (appendDlqFixtures ? 2 : 0);
        log.info("Published {} record(s) to {} ({} from LLM)", total, NewsFreeformTopics.RAW, totalLlmSent);
    }

    private static int parseCount(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean appendFixtureEdgeCases() {
        String v = System.getenv().getOrDefault("NEWS_APPEND_FIXTURE_EDGE_CASES", "false").trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    private NewsFreeformRandomLlmProducerMain() {}
}
