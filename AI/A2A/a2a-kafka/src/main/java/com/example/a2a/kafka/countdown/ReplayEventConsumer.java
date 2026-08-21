package com.example.a2a.kafka.countdown;

import com.example.a2a.kafka.A2AKafkaConfig;
import com.example.a2a.kafka.A2AKafkaConsumer;
import com.example.a2a.kafka.model.Task;
import com.example.a2a.kafka.model.TaskStatusUpdateEvent;
import com.example.a2a.kafka.util.KafkaPropertiesUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Properties;
import java.util.UUID;

/**
 * Replay demo: new consumer group reads {@code a2a.updates} from the earliest offset.
 * Run after a countdown client/agent session to show durable log replay.
 */
public final class ReplayEventConsumer {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "producer.properties";
        int idleSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 15;

        A2AKafkaConfig config = new A2AKafkaConfig();
        String groupId = "a2a-replay-" + UUID.randomUUID().toString().substring(0, 8);

        Properties consumerProps = KafkaPropertiesUtil.consumerProps(configPath, groupId);
        consumerProps.setProperty("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumerProps)) {

            A2AKafkaConsumer a2aConsumer = new A2AKafkaConsumer(kafkaConsumer, config, new A2AKafkaConsumer.A2AMessageHandler() {
                @Override
                public void onStatusUpdate(TaskStatusUpdateEvent event) {
                    if (event != null) {
                        System.out.println("[replay] status task=" + event.getTaskId() + " final=" + event.getFinal());
                    }
                }

                @Override
                public void onTaskResult(Task task) {
                    if (task != null) {
                        System.out.println("[replay] task id=" + task.getId());
                    }
                }
            });

            Thread runner = new Thread(() -> a2aConsumer.runUpdatesLoop(), "replay-consumer");
            runner.setDaemon(true);
            runner.start();

            System.out.println("[replay] group=" + groupId + " topic=" + config.getUpdatesTopic()
                    + " from earliest; listening " + idleSeconds + "s then exit.");
            Thread.sleep(idleSeconds * 1000L);
            a2aConsumer.stop();
            runner.join(3000);
            System.out.println("[replay] done.");
        }
    }
}
