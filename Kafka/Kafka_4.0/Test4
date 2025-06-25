import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/*
Sample Java code for the new Kafka 4.0 consumer rebalance protocol.
To run it you need a Kafka 4.0 cluster and Kafka 4.0 clients. 
You need to add this to your pom.xml file to use Kafka 4.0 clients:

<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>4.0.0</version>
	</dependency>  


*/

public class Test4 {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "IP:9092");
        props.put("group.id", "test-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        // new 4.0 rebalance protocol properties
        props.put("group.protocol", "consumer");
        props.put("group.remote.assignor", "uniform");



        // Create the Kafka consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

        // Subscribe to topics
        consumer.subscribe(List.of("t1"));

        // Poll for records
        while (true) {
            var records = consumer.poll(Duration.ofMillis(100));
            for (var record : records) {
                System.out.printf("Consumed record with key %s and value %s%n", record.key(), record.value());
            }
        }
    }
}
