package DroneDeliveryDemo;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import com.uber.cadence.activity.Activity;


// initiates the drone demo by requesting some new orders to be delivered
// Pure Kafka, just sends a new request to the new orders topic
public class CreateOrdersProducer {
	
	static Properties kafkaProps;
	static final String orderjobsTopicName = CommonProps.orderjobsTopicName;
	static final String newordersTopicName = CommonProps.newordersTopicName;

	public CreateOrdersProducer() {
	}

	public static void main(String[] args) {
		
		
		kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("producer2.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        int numOrders = 20;
        KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);
        
        for (int i=0; i < numOrders; i++) {
        	
        	// String order = "order_" + i;
        	String order = "" + i;
        	ProducerRecord<String, String> producerRecord = new ProducerRecord<>(newordersTopicName, "", order);

        	try  {
        		producer.send(producerRecord);
        		System.out.println("sent order " + order + " to topic " + newordersTopicName);
        		// producer.flush();
        	} catch (Exception e) {
             e.printStackTrace();
        	}
        
        	
        }
        producer.flush();
        producer.close();
	}
}
