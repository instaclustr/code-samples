package local.kip1034.dlq;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_EXCEPTION_MESSAGE_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_EXCEPTION_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_OFFSET_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_PARTITION_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_STACKTRACE_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_TOPIC_NAME;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.DoubleDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KIP-1034 deserialization DLQ smoke: {@link org.apache.kafka.common.serialization.IntegerDeserializer} rejects
 * non-4-byte values; {@link org.apache.kafka.common.serialization.StringDeserializer} does not throw on bad UTF-8.
 * The topology is a 10-second tumbling mean per key over 10 rotating producer keys.
 */
class Kip1034DeserializationDlqIT {

    private static final String BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

    /** Number of source records (99 valid + 1 invalid). */
    private static final int BATCH_SIZE = 100;

    /** Producer keys {@code key-0} … {@code key-(NUM_DISTINCT_KEYS-1)}. */
    private static final int NUM_DISTINCT_KEYS = 10;

    /** Tumbling window size for per-key averages (matches topology). */
    private static final long WINDOW_SIZE_MS = 10_000L;

    /** Set {@code -Dkip1034.trace=true} to print every produced input row and every output sink row. */
    private static final boolean TRACE = Boolean.getBoolean("kip1034.trace");

    /** 0-based index of the single faulty value (1 in {@value #BATCH_SIZE}). */
    private static final int FAULTY_INDEX = 77;

    static final String INPUT_TOPIC = "kip1034-dlq-input";
    /** Windowed averages use {@link org.apache.kafka.common.serialization.DoubleSerializer}; keep a dedicated topic. */
    static final String OUTPUT_TOPIC = "kip1034-dlq-output-windowed";
    static final String DLQ_TOPIC = "kip1034-dlq-dead-letter";

    private String applicationId;
    private KafkaStreams streams;

    @BeforeAll
    static void createTopicsOnce() throws Exception {
        try (AdminClient admin = AdminClient.create(adminProps())) {
            List<NewTopic> topics =
                    List.of(
                            new NewTopic(INPUT_TOPIC, 1, (short) 1),
                            new NewTopic(OUTPUT_TOPIC, 1, (short) 1),
                            new NewTopic(DLQ_TOPIC, 1, (short) 1));
            try {
                admin.createTopics(topics).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
    }

    @BeforeEach
    void newApplicationId() {
        applicationId = "kip1034-dlq-it-" + UUID.randomUUID();
    }

    @AfterEach
    void stopStreams() {
        if (streams != null) {
            streams.close(Duration.ofSeconds(30));
            streams = null;
        }
    }

    @Test
    @DisplayName("100 records on 10 keys: 99 valid ints + 1 bad payload → 1 DLQ + tumbling 10s mean per key")
    void batchOf100_withOneFaultyRecord_producesSingleDlqAndWindowedAverages() throws Exception {
        Topology topology = buildTopology();
        Properties streamsProps = streamsProps(applicationId);
        streams = new KafkaStreams(topology, streamsProps);
        streams.start();

        await().atMost(90, SECONDS).until(() -> streams.state() == KafkaStreams.State.RUNNING);

        final byte[] badValue = "not-an-int".getBytes(StandardCharsets.UTF_8);
        final byte[] faultyKeyBytes = keyBytesForSlot(FAULTY_INDEX % NUM_DISTINCT_KEYS);
        final Map<String, Double> expectedAverages = expectedPerKeyAveragesInFirstWindow();

        Map<String, Object> baseConsumerProps = new HashMap<>();
        baseConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        baseConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        baseConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        baseConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        Map<String, Object> dlqProps = new HashMap<>(baseConsumerProps);
        dlqProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-verify-" + UUID.randomUUID());

        Map<String, Object> outProps = new HashMap<>(baseConsumerProps);
        outProps.put(ConsumerConfig.GROUP_ID_CONFIG, "out-verify-" + UUID.randomUUID());
        outProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        outProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DoubleDeserializer.class.getName());

        try (KafkaConsumer<byte[], byte[]> dlqConsumer = new KafkaConsumer<>(dlqProps);
                KafkaConsumer<String, Double> outputConsumer = new KafkaConsumer<>(outProps)) {
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            outputConsumer.subscribe(Collections.singletonList(OUTPUT_TOPIC));
            awaitAssignedAndSeekToEnd(dlqConsumer);
            awaitAssignedAndSeekToEnd(outputConsumer);

            Map<String, Object> producerProps = new HashMap<>();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
            producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

            try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps)) {
                long now = System.currentTimeMillis();
                long batchEventTs = (now / WINDOW_SIZE_MS) * WINDOW_SIZE_MS;
                if (TRACE) {
                    System.out.printf(
                            "%n========== TRACE: produce → %s (batchEventTs=%d windowSizeMs=%d) ==========%n",
                            INPUT_TOPIC, batchEventTs, WINDOW_SIZE_MS);
                }
                for (int i = 0; i < BATCH_SIZE; i++) {
                    int slot = i % NUM_DISTINCT_KEYS;
                    byte[] key = keyBytesForSlot(slot);
                    byte[] value;
                    if (i == FAULTY_INDEX) {
                        value = badValue;
                    } else {
                        value = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1_000 + i).array();
                    }
                    if (TRACE) {
                        traceProduceLine(i, slot, key, value, batchEventTs);
                    }
                    producer.send(new ProducerRecord<>(INPUT_TOPIC, null, batchEventTs, key, value)).get();
                }
                producer.flush();
                if (TRACE) {
                    System.out.println("========== TRACE: produce end (flushed) ==========\n");
                }
            }

            AtomicReference<ConsumerRecord<byte[], byte[]>> dlqHolder = new AtomicReference<>();
            await().atMost(90, SECONDS).pollInterval(Duration.ofMillis(200)).until(
                    () -> {
                        ConsumerRecords<byte[], byte[]> polled =
                                dlqConsumer.poll(Duration.ofMillis(500));
                        for (ConsumerRecord<byte[], byte[]> r : polled) {
                            if (r.value() != null
                                    && r.key() != null
                                    && Arrays.equals(badValue, r.value())
                                    && Arrays.equals(faultyKeyBytes, r.key())) {
                                dlqHolder.set(r);
                                return true;
                            }
                        }
                        return false;
                    });

            ConsumerRecord<byte[], byte[]> dlq = dlqHolder.get();
            assertNotNull(dlq, "expected one DLQ record for the faulty payload");
            dumpDlqRecordToStdout(dlq);
            assertArrayEquals(faultyKeyBytes, dlq.key(), "DLQ key should be raw source key bytes");
            assertArrayEquals(badValue, dlq.value(), "DLQ value should be raw source value bytes");
            assertHeaderContains(dlq, HEADER_ERRORS_EXCEPTION_NAME, "SerializationException");
            assertHeaderContains(dlq, HEADER_ERRORS_TOPIC_NAME, INPUT_TOPIC);
            assertHeaderPresent(dlq, HEADER_ERRORS_EXCEPTION_MESSAGE_NAME);
            assertHeaderPresent(dlq, HEADER_ERRORS_STACKTRACE_NAME);
            assertHeaderContains(dlq, HEADER_ERRORS_PARTITION_NAME, "0");
            assertHeaderPresent(dlq, HEADER_ERRORS_OFFSET_NAME);

            Map<String, Double> lastAverageByKey = new HashMap<>();
            AtomicInteger outputSeq = new AtomicInteger(0);
            if (TRACE) {
                System.out.printf(
                        "========== TRACE: output ← %s (running mean per key in window; may be >99 lines) ==========%n",
                        OUTPUT_TOPIC);
            }
            await().atMost(90, SECONDS).pollInterval(Duration.ofMillis(200)).until(
                    () -> {
                        ConsumerRecords<String, Double> polled =
                                outputConsumer.poll(Duration.ofMillis(500));
                        for (ConsumerRecord<String, Double> r : polled) {
                            if (TRACE) {
                                traceOutputLine(outputSeq.incrementAndGet(), r);
                            }
                            lastAverageByKey.put(r.key(), r.value());
                        }
                        if (lastAverageByKey.size() < NUM_DISTINCT_KEYS) {
                            return false;
                        }
                        for (Map.Entry<String, Double> e : expectedAverages.entrySet()) {
                            Double v = lastAverageByKey.get(e.getKey());
                            if (v == null
                                    || Math.abs(v - e.getValue()) > 1e-6) {
                                return false;
                            }
                        }
                        return true;
                    });
            if (TRACE) {
                System.out.println("========== TRACE: output end (final running mean matches batch average per key) ==========");
                lastAverageByKey.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> System.out.printf("  final key=%s mean=%s%n", e.getKey(), e.getValue()));
                System.out.println();
            }
            assertEquals(NUM_DISTINCT_KEYS, lastAverageByKey.size(), "all 10 keys should appear");
            for (Map.Entry<String, Double> e : expectedAverages.entrySet()) {
                assertTrue(
                        lastAverageByKey.containsKey(e.getKey()),
                        () -> "missing output key " + e.getKey());
                assertEquals(
                        e.getValue(),
                        lastAverageByKey.get(e.getKey()),
                        1e-9,
                        () -> "final window average for " + e.getKey());
            }
        }
    }

    private static void traceProduceLine(int i, int slot, byte[] key, byte[] value, long batchEventTs) {
        String keyUtf8 = new String(key, StandardCharsets.UTF_8);
        String valueDesc;
        if (value.length == 4) {
            int v = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).getInt();
            valueDesc = String.valueOf(v);
        } else {
            valueDesc = "FAULT(" + safeUtf8Preview(value) + ")";
        }
        System.out.printf(
                "produce i=%3d slot=%d key=%-6s CreateTime=%d value=%s%n", i, slot, keyUtf8, batchEventTs, valueDesc);
    }

    private static void traceOutputLine(int seq, ConsumerRecord<String, Double> r) {
        System.out.printf(
                "output  #%4d key=%-6s mean=%s partition=%d offset=%d ts=%d tsType=%s%n",
                seq,
                r.key(),
                r.value(),
                r.partition(),
                r.offset(),
                r.timestamp(),
                r.timestampType());
    }

    private static byte[] keyBytesForSlot(int slot) {
        return String.format("key-%d", slot).getBytes(StandardCharsets.UTF_8);
    }

    /** Expected mean of {@code 1000 + i} over indices {@code i % NUM_DISTINCT_KEYS == slot}, excluding DLQ row. */
    private static Map<String, Double> expectedPerKeyAveragesInFirstWindow() {
        long[] sum = new long[NUM_DISTINCT_KEYS];
        long[] count = new long[NUM_DISTINCT_KEYS];
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (i == FAULTY_INDEX) {
                continue;
            }
            int slot = i % NUM_DISTINCT_KEYS;
            sum[slot] += 1_000L + i;
            count[slot]++;
        }
        Map<String, Double> out = new TreeMap<>();
        for (int slot = 0; slot < NUM_DISTINCT_KEYS; slot++) {
            String key = "key-" + slot;
            out.put(key, count[slot] == 0 ? 0.0 : sum[slot] / (double) count[slot]);
        }
        return out;
    }

    /** Prints every field and header on the DLQ {@link ConsumerRecord} (stdout → Surefire console). */
    private static void dumpDlqRecordToStdout(ConsumerRecord<byte[], byte[]> r) {
        HexFormat hex = HexFormat.of();
        StringBuilder sb = new StringBuilder(2_048);
        sb.append("\n========== DLQ ConsumerRecord dump ==========\n");
        sb.append("topic:              ").append(r.topic()).append('\n');
        sb.append("partition:          ").append(r.partition()).append('\n');
        sb.append("offset:             ").append(r.offset()).append('\n');
        sb.append("timestamp:          ").append(r.timestamp()).append('\n');
        sb.append("timestampType:      ").append(r.timestampType()).append('\n');
        sb.append("serializedKeySize:  ").append(r.serializedKeySize()).append('\n');
        sb.append("serializedValueSize:").append(r.serializedValueSize()).append('\n');
        sb.append("leaderEpoch:        ").append(r.leaderEpoch()).append('\n');
        sb.append("key (hex):          ").append(r.key() == null ? "null" : hex.formatHex(r.key())).append('\n');
        sb.append("key (UTF-8 try):    ")
                .append(r.key() == null ? "null" : safeUtf8Preview(r.key()))
                .append('\n');
        sb.append("value (hex):        ").append(r.value() == null ? "null" : hex.formatHex(r.value())).append('\n');
        sb.append("value (UTF-8 try):  ")
                .append(r.value() == null ? "null" : safeUtf8Preview(r.value()))
                .append('\n');
        sb.append("--- headers (").append(r.headers().toArray().length).append(") ---\n");
        for (org.apache.kafka.common.header.Header h : r.headers()) {
            byte[] v = h.value();
            sb.append("  [").append(h.key()).append("]\n");
            sb.append("      hex: ").append(v == null ? "null" : hex.formatHex(v)).append('\n');
            sb.append("      utf8:").append(v == null ? " null" : " " + safeUtf8Preview(v)).append('\n');
        }
        sb.append("============================================\n");
        System.out.print(sb);
    }

    private static String safeUtf8Preview(byte[] b) {
        if (b == null) {
            return "null";
        }
        String s = new String(b, StandardCharsets.UTF_8);
        String escaped = s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        if (escaped.length() > 500) {
            return escaped.substring(0, 500) + "…(" + escaped.length() + " chars)";
        }
        return escaped;
    }

    private static void awaitAssignedAndSeekToEnd(KafkaConsumer<?, ?> c) {
        await().atMost(30, SECONDS).until(
                () -> {
                    c.poll(Duration.ofMillis(300));
                    return !c.assignment().isEmpty();
                });
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

    private static void assertHeaderPresent(ConsumerRecord<byte[], byte[]> r, String header) {
        assertNotNull(r.headers().lastHeader(header), "missing header: " + header);
    }

    private static void assertHeaderContains(ConsumerRecord<byte[], byte[]> r, String header, String substring) {
        org.apache.kafka.common.header.Header h = r.headers().lastHeader(header);
        assertNotNull(h, "missing header: " + header);
        String v = new String(h.value(), StandardCharsets.UTF_8);
        assertNotNull(v);
        assertTrue(v.contains(substring), () -> "header " + header + " was: " + v);
    }

    private static Topology buildTopology() {
        StreamsBuilder b = new StreamsBuilder();
        KStream<String, Integer> input =
                b.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), Serdes.Integer()));
        input.groupByKey(Grouped.with(Serdes.String(), Serdes.Integer()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMillis(WINDOW_SIZE_MS)))
                .aggregate(
                        () -> "0,0",
                        (key, value, aggregate) -> {
                            int comma = aggregate.lastIndexOf(',');
                            long s = Long.parseLong(aggregate.substring(0, comma)) + value;
                            long c = Long.parseLong(aggregate.substring(comma + 1)) + 1;
                            return s + "," + c;
                        },
                        Materialized.with(Serdes.String(), Serdes.String()))
                .toStream()
                .mapValues(
                        sumCount -> {
                            int comma = sumCount.lastIndexOf(',');
                            long s = Long.parseLong(sumCount.substring(0, comma));
                            long c = Long.parseLong(sumCount.substring(comma + 1));
                            return c == 0 ? 0.0 : s / (double) c;
                        })
                .selectKey((Windowed<String> windowedKey, Double avg) -> windowedKey.key())
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.Double()));
        return b.build();
    }

    private Properties streamsProps(String appId) {
        Properties p = new Properties();
        p.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        p.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        p.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/kip1034-" + appId);
        p.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
        p.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        p.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class);
        p.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLQ_TOPIC);

        p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        return p;
    }

    private static Map<String, Object> adminProps() {
        Map<String, Object> m = new HashMap<>();
        m.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        m.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        return m;
    }
}
