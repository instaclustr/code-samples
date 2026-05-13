package local.kip1034.dlq.news.freeform;

import java.util.List;
import java.util.Properties;
import local.kip1034.dlq.news.NewsItem;
import local.kip1034.dlq.news.NewsJson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes a small mix: valid categorized JSON, JSON missing categories (app DLQ), and free-form prose (KIP-1034
 * deserialization DLQ after strict {@link JsonNewsItemSerde}).
 */
public final class NewsFreeformProducerMain {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformProducerMain.class);

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> p = new KafkaProducer<>(props)) {
            String freeformKey = "ff-prose-1";
            String freeform = FreeformSampleProse.museumDemoIncident();
            p.send(new ProducerRecord<>(NewsFreeformTopics.RAW, freeformKey, freeform));
            log.info("Sent free-form prose key={} (expect KIP-1034 DLQ then LLM repair)", freeformKey);

            NewsItem missing = new NewsItem();
            missing.articleId = "ff-missing-1";
            missing.heading = "Markets rally";
            missing.article = "Stocks rose on optimism.";
            missing.categories = List.of();
            p.send(new ProducerRecord<>(NewsFreeformTopics.RAW, missing.articleId, NewsJson.stringify(missing)));
            log.info("Sent JSON missing categories key={} (expect app DLQ)", missing.articleId);

            NewsItem ok = new NewsItem();
            ok.articleId = "ff-ok-1";
            ok.heading = "Local weather";
            ok.article = "Rain expected.";
            ok.categories = List.of("weather", "local");
            p.send(new ProducerRecord<>(NewsFreeformTopics.RAW, ok.articleId, NewsJson.stringify(ok)));
            log.info("Sent valid JSON key={}", ok.articleId);
        }
        log.info("Producer done.");
    }

    private NewsFreeformProducerMain() {}
}
