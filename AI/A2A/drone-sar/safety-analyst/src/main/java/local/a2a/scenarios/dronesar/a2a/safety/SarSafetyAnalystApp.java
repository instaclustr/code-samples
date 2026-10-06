package local.a2a.scenarios.dronesar.a2a.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import local.a2a.scenarios.dronesar.a2a.SarJson;
import local.a2a.scenarios.dronesar.a2a.SarKafkaEventPublisher;
import local.a2a.scenarios.dronesar.a2a.SarMissionEvent;
import local.a2a.scenarios.dronesar.a2a.SarSafetyAnalystService;
import local.a2a.scenarios.dronesar.a2a.SarSignificantEventDetector;
import local.a2a.scenarios.dronesar.a2a.SarTelemetryKafkaMessage;
import local.a2a.scenarios.dronesar.a2a.SarViolationAssessment;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Consumes {@code sar.telemetry.events}, runs local Ollama safety assessments, publishes to
 * {@code sar.violation-assessments}.
 */
public final class SarSafetyAnalystApp {

    private static final ObjectMapper MAPPER = SarJson.mapper();

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv().getOrDefault("SAR_KAFKA_BOOTSTRAP", "localhost:9092");
        String eventsTopic = System.getenv().getOrDefault("SAR_KAFKA_EVENTS_TOPIC", SarKafkaEventPublisher.DEFAULT_EVENTS_TOPIC);
        String assessmentsTopic =
                System.getenv().getOrDefault("SAR_KAFKA_ASSESSMENTS_TOPIC", SarKafkaEventPublisher.DEFAULT_ASSESSMENTS_TOPIC);
        String groupId = System.getenv().getOrDefault("SAR_KAFKA_CONSUMER_GROUP", "sar-safety-analyst");
        int maxAssessments = Integer.parseInt(System.getenv().getOrDefault("SAR_SAFETY_MAX_ASSESSMENTS", "10"));
        long idleMs = Long.parseLong(System.getenv().getOrDefault("SAR_SAFETY_IDLE_MS", "15000"));

        SarSafetyAnalystService analyst = SarSafetyAnalystService.fromEnv();
        List<SarViolationAssessment> assessments = new ArrayList<>();

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "sar-safety-analyst");

        System.out.println("=== SAR Safety Analyst ===");
        System.out.println("bootstrap=" + bootstrap);
        System.out.println("eventsTopic=" + eventsTopic);
        System.out.println("assessmentsTopic=" + assessmentsTopic);
        System.out.println("maxAssessments=" + maxAssessments);

        AtomicInteger produced = new AtomicInteger();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
                KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            consumer.subscribe(Collections.singletonList(eventsTopic));
            long lastMessageAt = System.currentTimeMillis();
            while (produced.get() < maxAssessments) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    if (System.currentTimeMillis() - lastMessageAt >= idleMs) {
                        break;
                    }
                    continue;
                }
                for (ConsumerRecord<String, String> record : records) {
                    lastMessageAt = System.currentTimeMillis();
                    SarTelemetryKafkaMessage message = MAPPER.readValue(record.value(), SarTelemetryKafkaMessage.class);
                    SarViolationAssessment assessment = assess(message, analyst);
                    if (assessment == null) {
                        continue;
                    }
                    String outJson = MAPPER.writeValueAsString(assessment);
                    producer.send(new ProducerRecord<>(assessmentsTopic, assessment.missionId(), outJson));
                    producer.flush();
                    assessments.add(assessment);
                    produced.incrementAndGet();
                    System.out.println("assessment #" + produced.get() + " severity=" + assessment.severity()
                            + " rule=" + assessment.ruleId());
                    System.out.println("  " + assessment.summary());
                    System.out.println("  action=" + assessment.recommendedAction());
                    if (produced.get() >= maxAssessments) {
                        break;
                    }
                }
            }
        }

        System.out.println("\n=== Safety assessments complete (" + assessments.size() + ") ===");
        for (int i = 0; i < assessments.size(); i++) {
            SarViolationAssessment a = assessments.get(i);
            System.out.println((i + 1) + ". [" + a.severity() + "] " + a.sourceEventType() + " — " + a.summary());
        }
    }

    static SarViolationAssessment assess(SarTelemetryKafkaMessage message, SarSafetyAnalystService analyst) {
        if (message == null || message.event() == null) {
            return null;
        }
        SarMissionEvent event = message.event();
        if (!SarSignificantEventDetector.isSafetyEvent(event.type())) {
            return null;
        }
        if ("MISSION_SAFETY_SUMMARY".equals(event.type()) && message.summary() != null) {
            return analyst.assessSummary(message.rulesetId(), event.missionId(), message.summary());
        }
        return analyst.assessEvent(message.rulesetId(), event);
    }

    private SarSafetyAnalystApp() {}
}
