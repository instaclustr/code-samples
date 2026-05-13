package local.kip1034.dlq.news.freeform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads application-level DLQ envelopes from {@link NewsFreeformTopics#APP_DLQ} (e.g. {@code MISSING_CATEGORIES}) and
 * writes repaired {@link NewsItem} JSON to {@link NewsFreeformTopics#REPAIRED}.
 */
public final class NewsFreeformAppDlqRepairMain {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformAppDlqRepairMain.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties cProps = new Properties();
        cProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cProps.put(ConsumerConfig.GROUP_ID_CONFIG, "news-ff-app-dlq-repair-" + System.currentTimeMillis());
        cProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        cProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        cProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        cProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        Properties pProps = new Properties();
        pProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        pProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cProps);
                KafkaProducer<String, String> producer = new KafkaProducer<>(pProps)) {
            consumer.subscribe(Collections.singletonList(NewsFreeformTopics.APP_DLQ));
            awaitAssignedAndSeekToEnd(consumer);
            log.info("App DLQ repair loop on {} (start before producer burst).", NewsFreeformTopics.APP_DLQ);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(800));
                for (ConsumerRecord<String, String> r : records) {
                    Optional<NewsItem> fixed = repairEnvelope(r.key(), r.value());
                    if (fixed.isEmpty()) {
                        log.warn("Could not repair app DLQ value offset={}", r.offset());
                        continue;
                    }
                    NewsItem item = fixed.get();
                    String json = NewsJson.stringify(item);
                    producer.send(new ProducerRecord<>(NewsFreeformTopics.REPAIRED, item.articleId, json));
                    producer.flush();
                    log.info("Repaired (app DLQ) -> {} key={}", NewsFreeformTopics.REPAIRED, item.articleId);
                }
            }
        }
    }

    private static Optional<NewsItem> repairEnvelope(String kafkaKey, String dlqJson) {
        try {
            JsonNode root = MAPPER.readTree(dlqJson);
            String reason = root.path("reason").asText("");
            String raw = root.path("rawJson").asText("");
            if (raw.isBlank()) {
                return Optional.empty();
            }
            if ("MISSING_CATEGORIES".equals(reason)) {
                NewsItem base = NewsJson.parse(raw);
                Optional<String> llm = NewsLlmClient.inferCategoriesJson(base);
                if (llm.isPresent()) {
                    try {
                        NewsItem out = NewsJson.parse(llm.get());
                        if (out.hasAnyCategory()) {
                            return Optional.of(out);
                        }
                    } catch (Exception ignored) {
                    }
                }
                return fallbackFromHeading(base);
            }
            if ("BAD_JSON".equals(reason)) {
                String aid = root.path("articleId").asText("").trim();
                if (aid.isEmpty()) {
                    aid = kafkaKey == null ? "unknown" : kafkaKey;
                }
                NewsItem stub = new NewsItem();
                stub.articleId = aid;
                stub.heading = "Recovered article";
                stub.article = raw;
                stub.categories.clear();
                Optional<String> llm = NewsLlmClient.inferCategoriesJson(stub);
                if (llm.isPresent()) {
                    try {
                        NewsItem out = NewsJson.parse(llm.get());
                        if (out.hasAnyCategory()) {
                            return Optional.of(out);
                        }
                    } catch (Exception ignored) {
                    }
                }
                return Optional.empty();
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("repairEnvelope failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private static Optional<NewsItem> fallbackFromHeading(NewsItem base) {
        String h = base.heading == null ? "" : base.heading.toLowerCase();
        base.categories.clear();
        for (String token : h.split("[^a-z0-9]+")) {
            if (token.length() >= 4 && base.categories.size() < 4) {
                base.categories.add(token);
            }
        }
        if (!base.hasAnyCategory()) {
            base.categories.add("general");
        }
        return Optional.of(base);
    }

    private static void awaitAssignedAndSeekToEnd(KafkaConsumer<String, String> c) {
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

    private NewsFreeformAppDlqRepairMain() {}
}
