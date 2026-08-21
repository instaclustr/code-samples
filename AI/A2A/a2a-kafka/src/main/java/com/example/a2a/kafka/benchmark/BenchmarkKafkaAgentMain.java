package com.example.a2a.kafka.benchmark;

import com.example.a2a.kafka.A2AKafkaConfig;
import com.example.a2a.kafka.A2AKafkaConsumer;
import com.example.a2a.kafka.A2AKafkaProducer;
import com.example.a2a.kafka.countdown.CountdownKafkaAgent;
import com.example.a2a.kafka.countdown.CountdownParser;
import com.example.a2a.kafka.model.MessageSendParams;
import com.example.a2a.kafka.util.KafkaPropertiesUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.UUID;

/**
 * Combined agent: routes benchmark messages to {@link BenchmarkKafkaAgent},
 * countdown messages to {@link CountdownKafkaAgent}.
 */
public final class BenchmarkKafkaAgentMain {

    private BenchmarkKafkaAgentMain() {}

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        A2AKafkaConfig config = new A2AKafkaConfig();

        try (KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(KafkaPropertiesUtil.producerProps(configPath));
             KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(
                     KafkaPropertiesUtil.consumerProps(configPath, "a2a-benchmark-agent"))) {

            A2AKafkaProducer a2aProducer = new A2AKafkaProducer(kafkaProducer, config);
            BenchmarkKafkaAgent benchAgent = new BenchmarkKafkaAgent(a2aProducer);
            CountdownKafkaAgent countdownAgent = new CountdownKafkaAgent(a2aProducer);

            A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(kafkaConsumer, config, new A2AKafkaConsumer.A2AMessageHandler() {
                @Override
                public void onMessageSend(String partitionKey, MessageSendParams params) {
                    String taskId = partitionKey != null && !partitionKey.isBlank()
                            ? partitionKey
                            : "task-" + UUID.randomUUID();
                    String userText = CountdownParser.extractUserText(params);
                    if (BenchmarkKafkaAgent.isBenchmarkRequest(userText)) {
                        benchAgent.onMessageSend(taskId, params);
                    } else {
                        countdownAgent.onMessageSend(taskId, params);
                    }
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down benchmark agent...");
                a2aConsumer.stop();
                countdownAgent.shutdown();
            }));

            System.out.println("Benchmark Kafka Agent — consuming " + config.getRequestsTopic()
                    + ", publishing " + config.getUpdatesTopic());
            System.out.println("Handles bench:rr, bench:stream, bench:notify, bench:fanout-stream, bench:llm-sim plus countdown.");
            System.out.println("Ctrl+C to stop.");
            a2aConsumer.runRequestsLoop();
        }
    }
}
