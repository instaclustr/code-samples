package local.kip1034.dlq.news.freeform;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Long-running producer: repeatedly calls {@link NewsLlmClient#generateSyntheticArticles} and publishes each article as
 * JSON to {@link NewsFreeformTopics#RAW}, sleeping between batches (valid categorized rows; no DLQ fixtures).
 *
 * <p>Environment: {@code NEWS_LLM_BATCH_SIZE} (default {@code 5}, max {@link NewsLlmClient#maxSyntheticBatchSize()}),
 * {@code NEWS_LLM_STREAM_INTERVAL_SEC} (default {@code 60}), {@code NEWS_LLM_STREAM_MAX_BATCHES} ({@code 0} = until
 * Ctrl+C), {@code NEWS_GEN_THEME}, {@code KAFKA_BOOTSTRAP_SERVERS}.
 */
public final class NewsFreeformStreamingLlmProducerMain {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformStreamingLlmProducerMain.class);

    public static void main(String[] args) throws Exception {
        int batchSize =
                parsePositiveInt(
                        System.getenv().getOrDefault("NEWS_LLM_BATCH_SIZE", "5"),
                        5,
                        1,
                        NewsLlmClient.maxSyntheticBatchSize());
        long intervalSec = parsePositiveLong(System.getenv().getOrDefault("NEWS_LLM_STREAM_INTERVAL_SEC", "60"), 60, 5);
        int maxBatches = parseMaxBatches(System.getenv().getOrDefault("NEWS_LLM_STREAM_MAX_BATCHES", "0"));
        String themeBase = System.getenv().getOrDefault("NEWS_GEN_THEME", "");

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        AtomicBoolean running = new AtomicBoolean(true);
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    log.info("Shutdown requested; stopping producer loop.");
                                    running.set(false);
                                    mainThread.interrupt();
                                },
                                "news-ff-streaming-llm-shutdown"));

        log.info(
                "Streaming LLM producer: batchSize={} intervalSec={} maxBatches={} themeBase={} topic={}",
                batchSize,
                intervalSec,
                maxBatches <= 0 ? "unlimited" : Integer.toString(maxBatches),
                themeBase.isBlank() ? "(default)" : themeBase,
                NewsFreeformTopics.RAW);

        int batchIndex = 0;
        long totalPublished = 0;
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            while (running.get()) {
                batchIndex++;
                if (maxBatches > 0 && batchIndex > maxBatches) {
                    log.info("Reached NEWS_LLM_STREAM_MAX_BATCHES={}; exiting.", maxBatches);
                    break;
                }
                String theme =
                        themeBase.isBlank()
                                ? ""
                                : themeBase + " (batch " + batchIndex + "; vary stories and regions each batch.)";

                log.info("Batch {}: requesting {} article(s) from LLM…", batchIndex, batchSize);
                List<NewsItem> items = NewsLlmClient.generateSyntheticArticles(batchSize, theme);
                if (items.isEmpty()) {
                    log.warn("Batch {}: LLM returned no parseable articles; nothing sent.", batchIndex);
                } else {
                    for (NewsItem item : items) {
                        String json = NewsJson.stringify(item);
                        producer.send(new ProducerRecord<>(NewsFreeformTopics.RAW, item.articleId, json));
                        totalPublished++;
                        log.info("Sent key={} heading={}", item.articleId, item.heading);
                    }
                    producer.flush();
                    log.info("Batch {}: published {} article(s); totalPublished={}", batchIndex, items.size(), totalPublished);
                }

                if (maxBatches > 0 && batchIndex >= maxBatches) {
                    break;
                }
                if (!running.get()) {
                    break;
                }
                try {
                    Thread.sleep(intervalSec * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Sleep interrupted; exiting loop.");
                    break;
                }
            }
        }
        log.info("Streaming LLM producer stopped. totalPublished={}", totalPublished);
    }

    private static int parsePositiveInt(String raw, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < min) {
                return min;
            }
            return Math.min(v, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parsePositiveLong(String raw, long fallback, long min) {
        try {
            long v = Long.parseLong(raw.trim());
            return v < min ? min : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseMaxBatches(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private NewsFreeformStreamingLlmProducerMain() {}
}
