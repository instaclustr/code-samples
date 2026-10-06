package local.a2a.scenarios.dronesar.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/** Publishes safety/significant events to Kafka for the analyst consumer. */
public final class SarKafkaEventPublisher implements Closeable {

    public static final String DEFAULT_EVENTS_TOPIC = "sar.telemetry.events";
    public static final String DEFAULT_ASSESSMENTS_TOPIC = "sar.violation-assessments";

    private static final ObjectMapper MAPPER = SarJson.mapper();

    private final KafkaProducer<String, String> producer;
    private final String eventsTopic;
    private final String rulesetId;

    public SarKafkaEventPublisher(KafkaProducer<String, String> producer, String eventsTopic, String rulesetId) {
        this.producer = producer;
        this.eventsTopic = eventsTopic;
        this.rulesetId = rulesetId;
    }

    public static SarKafkaEventPublisher fromEnv(String rulesetId) {
        String bootstrap = System.getenv("SAR_KAFKA_BOOTSTRAP");
        if (bootstrap == null || bootstrap.isBlank()) {
            return null;
        }
        String eventsTopic = System.getenv().getOrDefault("SAR_KAFKA_EVENTS_TOPIC", DEFAULT_EVENTS_TOPIC);
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "sar-mission-client");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        return new SarKafkaEventPublisher(new KafkaProducer<>(props), eventsTopic, rulesetId);
    }

    public void publishEvent(SarMissionEvent event) {
        if (event == null || !SarSignificantEventDetector.isSafetyEvent(event.type())) {
            return;
        }
        publish(SarTelemetryKafkaMessage.ofEvent(rulesetId, event), event.missionId());
    }

    public void publishSummary(String missionId, SarJson.MissionSummary summary) {
        SarMissionEvent marker =
                SarMissionEvent.of("MISSION_SAFETY_SUMMARY", summary.ticksRun(), missionId, null, null, null);
        publish(new SarTelemetryKafkaMessage(rulesetId, marker, summary), missionId);
    }

    private void publish(SarTelemetryKafkaMessage message, String key) {
        try {
            String json = MAPPER.writeValueAsString(message);
            producer.send(new ProducerRecord<>(eventsTopic, key, json));
            producer.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish SAR Kafka event", ex);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
