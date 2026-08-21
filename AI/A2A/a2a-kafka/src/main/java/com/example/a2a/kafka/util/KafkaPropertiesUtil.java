package com.example.a2a.kafka.util;

import com.example.a2a.kafka.A2AKafkaConfig;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/** Load shared Kafka client properties from producer.properties (or path argument). */
public final class KafkaPropertiesUtil {

    private KafkaPropertiesUtil() {}

    public static Properties loadFile(String configPath) throws IOException {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(configPath)) {
            props.load(reader);
        }
        return props;
    }

    public static Properties producerProps(String configPath) throws IOException {
        Properties props = loadFile(configPath);
        A2AKafkaConfig.baseProducerProps().forEach(props::putIfAbsent);
        return props;
    }

    public static Properties consumerProps(String configPath, String groupId) throws IOException {
        Properties props = loadFile(configPath);
        props.remove("key.serializer");
        props.remove("value.serializer");
        A2AKafkaConfig.baseConsumerProps(groupId).forEach(props::putIfAbsent);
        return props;
    }
}
