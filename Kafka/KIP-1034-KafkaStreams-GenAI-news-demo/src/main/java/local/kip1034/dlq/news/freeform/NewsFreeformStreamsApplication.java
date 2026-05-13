package local.kip1034.dlq.news.freeform;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * News demo variant: values on raw/repaired must deserialize as {@link local.kip1034.dlq.news.NewsItem} JSON; free-form
 * prose fails at the edge
 * and is emitted to the KIP-1034 DLQ topic ({@link NewsFreeformTopics#STREAMS_DLQ}). Missing categories use {@link
 * NewsFreeformTopics#APP_DLQ}.
 */
public final class NewsFreeformStreamsApplication {

    private static final Logger log = LoggerFactory.getLogger(NewsFreeformStreamsApplication.class);

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String appId =
                System.getenv().getOrDefault("NEWS_FF_STREAMS_APPLICATION_ID", "demo-news-freeform-v1");

        NewsFreeformBootstrap.createTopics(bootstrap);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        props.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/kafka-streams-" + appId);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1_000);
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class);
        props.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, NewsFreeformTopics.STREAMS_DLQ);

        StreamsBuilder b = new StreamsBuilder();
        NewsFreeformTopology.build(b);

        KafkaStreams streams = new KafkaStreams(b.build(), props);

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    log.info("Shutdown: closing Streams");
                                    streams.close(Duration.ofSeconds(30));
                                    shutdown.countDown();
                                },
                                "news-ff-streams-shutdown"));

        streams.setStateListener(
                (newState, oldState) -> {
                    log.info("Streams {} -> {}", oldState, newState);
                    if (newState == KafkaStreams.State.RUNNING) {
                        log.info(
                                "News free-form RUNNING raw={} repaired={} streamsDLQ(KIP-1034)={} appDLQ={} windowed={}",
                                NewsFreeformTopics.RAW,
                                NewsFreeformTopics.REPAIRED,
                                NewsFreeformTopics.STREAMS_DLQ,
                                NewsFreeformTopics.APP_DLQ,
                                NewsFreeformTopics.WINDOWED);
                    }
                });
        streams.start();
        log.info("KafkaStreams.start() returned (application.id={}); awaiting RUNNING...", appId);
        shutdown.await();
    }

    private NewsFreeformStreamsApplication() {}
}
