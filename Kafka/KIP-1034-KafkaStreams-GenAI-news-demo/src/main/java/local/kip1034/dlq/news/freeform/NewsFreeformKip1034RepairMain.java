package local.kip1034.dlq.news.freeform;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import local.kip1034.dlq.news.NewsItem;
import local.kip1034.dlq.news.NewsJson;
import local.kip1034.dlq.news.NewsLlmClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes KIP-1034 deserialization DLQ records ({@link NewsFreeformTopics#STREAMS_DLQ}), interprets the raw value bytes
 * as UTF-8 prose (or broken JSON), asks {@link NewsLlmClient#structurePlainTextToNewsItemJson} for structured JSON, and
 * produces valid {@link NewsItem} JSON to {@link NewsFreeformTopics#REPAIRED}.
 */
public final class NewsFreeformKip1034RepairMain {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformKip1034RepairMain.class);

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties cProps = new Properties();
        cProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cProps.put(ConsumerConfig.GROUP_ID_CONFIG, "news-ff-kip1034-repair-" + System.currentTimeMillis());
        cProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        cProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        cProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        cProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        Properties pProps = new Properties();
        pProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        pProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(cProps);
                KafkaProducer<String, String> producer = new KafkaProducer<>(pProps)) {
            consumer.subscribe(Collections.singletonList(NewsFreeformTopics.STREAMS_DLQ));
            awaitAssignedAndSeekToEnd(consumer);
            log.info(
                    "KIP-1034 repair loop on {} -> {} (start before producer burst).",
                    NewsFreeformTopics.STREAMS_DLQ,
                    NewsFreeformTopics.REPAIRED);

            while (true) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(800));
                for (ConsumerRecord<byte[], byte[]> r : records) {
                    if (r.value() == null) {
                        continue;
                    }
                    String keyUtf8 =
                            r.key() == null ? "" : new String(r.key(), StandardCharsets.UTF_8);
                    String rawText = new String(r.value(), StandardCharsets.UTF_8);
                    log.info("KIP-1034 DLQ offset={} key={} preview={}", r.offset(), keyUtf8, preview(rawText));

                    Optional<NewsItem> fixed = tryStructure(keyUtf8, rawText);
                    if (fixed.isEmpty()) {
                        log.warn("Could not structure KIP-1034 DLQ record offset={}", r.offset());
                        continue;
                    }
                    NewsItem item = fixed.get();
                    String json = NewsJson.stringify(item);
                    producer.send(new ProducerRecord<>(NewsFreeformTopics.REPAIRED, item.articleId, json));
                    producer.flush();
                    log.info("Repaired (KIP-1034 path) -> {} key={}", NewsFreeformTopics.REPAIRED, item.articleId);
                }
            }
        }
    }

    private static Optional<NewsItem> tryStructure(String keyUtf8, String rawText) {
        Optional<String> llm = NewsLlmClient.structurePlainTextToNewsItemJson(keyUtf8, rawText);
        if (llm.isPresent()) {
            try {
                NewsItem out = NewsJson.parse(llm.get());
                if (out.hasAnyCategory()) {
                    log.info(
                            "KIP-1034 repair source=OLLAMA model={} key={} categories={}",
                            System.getenv().getOrDefault("OLLAMA_MODEL", "llama3:latest"),
                            keyUtf8,
                            out.categories);
                    return Optional.of(out);
                }
            } catch (Exception e) {
                log.warn("Ollama returned text but not valid NewsItem JSON key={}: {}", keyUtf8, e.toString());
            }
        } else {
            log.info("KIP-1034 repair: Ollama returned no usable text for key={} (is `ollama serve` running?)", keyUtf8);
        }
        try {
            NewsItem direct = NewsJson.parse(rawText);
            if (direct.hasAnyCategory()) {
                log.info("KIP-1034 repair source=DIRECT_JSON key={}", keyUtf8);
                return Optional.of(direct);
            }
        } catch (Exception ignored) {
        }
        NewsItem stub = deterministicFromPlainText(keyUtf8, rawText);
        log.info(
                "KIP-1034 repair source=DETERMINISTIC_WORDS key={} categories={}",
                keyUtf8,
                stub.categories);
        return Optional.of(stub);
    }

    /**
     * When Ollama is offline or output is unusable: build a minimal {@link NewsItem} and derive coarse tags from words
     * (demo-friendly).
     */
    private static NewsItem deterministicFromPlainText(String keyUtf8, String rawText) {
        NewsItem stub = new NewsItem();
        stub.articleId =
                (keyUtf8 == null || keyUtf8.isBlank()) ? "gen-" + UUID.randomUUID().toString().substring(0, 8) : keyUtf8;
        String t = rawText.trim();
        int nl = t.indexOf('\n');
        stub.heading = (nl > 0 ? t.substring(0, nl) : t).trim();
        if (stub.heading.length() > 120) {
            stub.heading = stub.heading.substring(0, 117) + "...";
        }
        if (stub.heading.isBlank()) {
            stub.heading = "Untitled";
        }
        stub.article = t;
        stub.categories.clear();
        String h = stub.heading.toLowerCase();
        for (String token : h.split("[^a-z0-9]+")) {
            if (token.length() >= 4 && stub.categories.size() < 4) {
                stub.categories.add(token);
            }
        }
        for (String token : t.toLowerCase().split("[^a-z0-9]+")) {
            if (token.length() >= 5 && stub.categories.size() < 5 && !stub.categories.contains(token)) {
                stub.categories.add(token);
            }
        }
        if (!stub.hasAnyCategory()) {
            stub.categories.add("general");
        }
        return stub;
    }

    private static String preview(String s) {
        if (s.length() <= 120) {
            return s.replace('\n', ' ');
        }
        return s.substring(0, 117).replace('\n', ' ') + "...";
    }

    private static void awaitAssignedAndSeekToEnd(KafkaConsumer<byte[], byte[]> c) {
        while (c.assignment().isEmpty()) {
            c.poll(Duration.ofMillis(200));
        }
        c.poll(Duration.ZERO);
        Set<TopicPartition> assignment = c.assignment();
        Map<TopicPartition, Long> endOffsets = c.endOffsets(assignment);
        for (TopicPartition tp : assignment) {
            Long end = endOffsets.get(tp);
            if (end != null) {
                c.seek(tp, end);
            }
        }
    }

    private NewsFreeformKip1034RepairMain() {}
}
