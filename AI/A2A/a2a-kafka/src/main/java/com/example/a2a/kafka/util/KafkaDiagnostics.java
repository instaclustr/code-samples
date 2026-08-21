package com.example.a2a.kafka.util;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** One-off diagnostics for cluster connectivity. */
public final class KafkaDiagnostics {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        var props = KafkaPropertiesUtil.producerProps(configPath);

        try (AdminClient admin = AdminClient.create(props)) {
            Set<String> names = admin.listTopics().names().get();
            System.out.println("Topics (" + names.size() + "):");
            names.stream().filter(n -> n.startsWith("a2a")).sorted().forEach(n -> System.out.println("  " + n));

            var consumerProps = KafkaPropertiesUtil.consumerProps(configPath, "a2a-diag-" + System.currentTimeMillis());
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                var reportTopics = names.stream()
                        .filter(n -> n.startsWith("a2a"))
                        .sorted()
                        .toList();
                for (String topic : reportTopics) {
                    reportTopic(consumer, topic);
                }
            }
        }
    }

    private static void reportTopic(KafkaConsumer<String, String> consumer, String topic) {
        var parts = consumer.partitionsFor(topic);
        if (parts == null || parts.isEmpty()) {
            System.out.println(topic + ": (no metadata / missing)");
            return;
        }
        System.out.println(topic + ": " + parts.size() + " partition(s)");
        for (var p : parts) {
            Map<TopicPartition, Long> end = consumer.endOffsets(
                    Collections.singletonList(new TopicPartition(topic, p.partition())));
            long high = end.values().iterator().next();
            System.out.println("  p" + p.partition() + " endOffset=" + high);
        }
    }
}
