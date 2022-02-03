package DroneMaths;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;

import DroneMaths.DroneDeliveryApp2.OrderWorkflow;

// This is a Kafka consumer than reads from new orders Kafka topic and create an Order WF simple!
// Runs forever

public class CreateOrderWFConsumer {

	public CreateOrderWFConsumer() {
		// TODO Auto-generated constructor stub
	}
	
	static Properties kafkaProps;
	//static final String orderjobsTopicName = "orderjobs";
	//static final String newordersTopicName = "neworders";  // Kafka topic to request new order WF creation
	static final String orderjobsTopicName = CommonProps.orderjobsTopicName;
	static final String newordersTopicName = CommonProps.newordersTopicName;

public static void main(String[] args) {
    	
    	String host  = "34.195.123.250";
    	
    	String domainName = "droneDemo";
    	
        
        WorkflowClient workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build()),
                        WorkflowClientOptions.newBuilder().setDomain(domainName).build());

        
        Properties kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("consumer2.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // must be in a different group to other consumers
        kafkaProps.put("group.id", "newOrder");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
            consumer.subscribe(Collections.singleton(newordersTopicName));

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                	System.out.print("Consumer got new Order WF creation request! ");
                    System.out.println(String.format("topic = %s, partition = %s, offset = %d, key = %s, value = %s",
                            record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                    
                 
                    String orderName = record.value().toString();
                    
                    OrderWorkflow orderWorkflow = workflowClient.newWorkflowStub(OrderWorkflow.class);
                    //orderName = name + "_order_" + i;
                    System.out.println("Starting new Order workfow!");
                	WorkflowExecution workflowExecution = WorkflowClient.start(orderWorkflow::startWorkflow, orderName);
                    System.out.println("Started new Order workfow! Workflow ID = " + workflowExecution.getWorkflowId());

                    
                    }
                }
            }
        }


}
