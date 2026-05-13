package local.kip1034.dlq.news.freeform;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import local.kip1034.dlq.news.NewsItem;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NewsFreeformTopologyIT {

    private TopologyTestDriver driver;

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @Test
    void jsonNewsItemDeserializerRejectsPlainProse() {
        JsonNewsItemSerde serde = new JsonNewsItemSerde();
        byte[] prose = "not json at all".getBytes(StandardCharsets.UTF_8);
        assertThrows(SerializationException.class, () -> serde.deserializer().deserialize("t", prose));
    }

    @Test
    void missingCategoriesGoToAppDlqAndValidItemWindowed() throws Exception {
        StreamsBuilder b = new StreamsBuilder();
        NewsFreeformTopology.build(b);
        Topology topology = b.build();

        Properties cfg = new Properties();
        cfg.put(StreamsConfig.APPLICATION_ID_CONFIG, "news-ff-topology-test");
        cfg.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        driver = new TopologyTestDriver(topology, cfg);
        JsonNewsItemSerde serde = new JsonNewsItemSerde();

        TestInputTopic<String, NewsItem> raw =
                driver.createInputTopic(
                        NewsFreeformTopics.RAW, new StringSerializer(), serde.serializer());
        TestOutputTopic<String, String> appDlq =
                driver.createOutputTopic(
                        NewsFreeformTopics.APP_DLQ, new StringDeserializer(), new StringDeserializer());
        TestOutputTopic<String, String> windowed =
                driver.createOutputTopic(
                        NewsFreeformTopics.WINDOWED, new StringDeserializer(), new StringDeserializer());

        NewsItem missing = new NewsItem();
        missing.articleId = "m1";
        missing.heading = "h";
        missing.article = "b";
        missing.categories = List.of();
        long t0 = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli();
        raw.pipeInput("m1", missing, t0);

        assertTrue(appDlq.readRecordsToList().size() >= 1, "missing categories -> app DLQ envelope");

        NewsItem ok = new NewsItem();
        ok.articleId = "o1";
        ok.heading = "Markets up";
        ok.article = "Stocks gained.";
        ok.categories = List.of("markets", "us");
        raw.pipeInput("o1", ok, t0);

        driver.advanceWallClockTime(Duration.ofMinutes(2));

        var wins = windowed.readRecordsToList();
        assertTrue(!wins.isEmpty(), "windowed output expected");
        String joined = wins.get(0).value();
        assertTrue(joined.contains("markets") || joined.contains("us"), joined);
    }
}
