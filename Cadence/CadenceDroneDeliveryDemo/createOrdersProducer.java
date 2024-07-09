package DroneMaths;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import com.uber.cadence.activity.Activity;

public class createOrdersProducer {
	
	static Properties kafkaProps;
	// static final String orderjobsTopicName = "orderjobs";
	//static final String newordersTopicName = "neworders";  // Kafka topic to request new order WF creation

	static final String orderjobsTopicName = CommonProps.orderjobsTopicName;
	static final String newordersTopicName = CommonProps.newordersTopicName;

	public createOrdersProducer() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		
		
		kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("producer2.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        int numOrders = 1;
        
        for (int i=0; i < numOrders; i++) {
        	
        	String order = "order_" + i;
        
        	// TODO Check order value - seems to be a GUID!???
        	
		 ProducerRecord<String, String> producerRecord = new ProducerRecord<>(newordersTopicName, "", order);

         try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps)) {
             producer.send(producerRecord);
             System.out.println("sent order " + order + " to topic " + newordersTopicName);
             producer.flush();
         } catch (Exception e) {
             e.printStackTrace();
         }
        }
         
	}

}
