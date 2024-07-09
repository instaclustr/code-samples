package DroneDeliveryDemo;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;

import DroneDeliveryDemo.DroneDeliveryDemoApp.DroneWorkflow;

// This is a Kafka consumer that:
// gets an Order ID that is ready for a drone from topic orderjobsTopicName
// gets a Drone ID that is waiting for an order from the topic dronesReadyTopicName
// calls DroneID.gotOrder(OrderID) signal
// acks both kafka topics
// Runs forever




public class MatchOrderToDrone {
	
	

	public MatchOrderToDrone() {
	}
	
	static Properties kafkaProps = new Properties();
	static final String orderjobsTopicName = CommonProps.orderjobsTopicName;
	static final String newordersTopicName = CommonProps.newordersTopicName;
	static final String dronesReadyTopicName = CommonProps.dronesReadyTopicName;
	static final String host = CommonProps.host;
	static final String domainName = CommonProps.domainName;
	static Queue<String> ordersQ = new LinkedList<>();
	static Queue<String> dronesQ = new LinkedList<>();
	

public static void main(String[] args) {
        
        WorkflowClient workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build()),
                        WorkflowClientOptions.newBuilder().setDomain(domainName).build());

        try (FileReader fileReader = new FileReader("consumer2.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // must be in a unique group
        kafkaProps.put("group.id", "matchOrderToDrone");
        kafkaProps.put("max.poll.records", "1");
        
        // props.setProperty("enable.auto.commit", "false");
        
        // TODO Could subscribe to both topics, but then need to handle the logic of matching?
        // i.e. put orders and drones into two queues.
        // if there is an order and drone available then remove them and send signal to drone, send acks to topics
        // continue
        
        // TODO Should probably set autcommit to false and manually commit the drone/order pairs once signal sent?
        // Would need to keep track of offset of orders and drones as well as IDs however

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
            // consumer.subscribe(Collections.singleton(orderjobsTopicName));
        	consumer.subscribe(Arrays.asList(orderjobsTopicName, dronesReadyTopicName));	
        	
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                	System.out.println("MatchOrderToDrone Consumer got record");
                    System.out.println(String.format("topic = %s, partition = %s, offset = %d, key = %s, value = %s",
                            record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                    
                    if (record.topic().equalsIgnoreCase(orderjobsTopicName))
                    {
                    	System.out.println("MatchOrderToDrone got an order " + record.value());
                    	ordersQ.add(record.value());
                    }
                    else
                    {
                    	System.out.println("MatchOrderToDrone got a drone " + record.value());
                    	dronesQ.add(record.value());
                    }
                    
                    String orderID = "";
                    String droneID = "";
                    
                    // if there is an order and drone we have a match!
                    if (ordersQ.size() >= 1 && dronesQ.size() >= 1)
                    {
                    	orderID = ordersQ.poll();
                    	droneID = dronesQ.poll();
                    
                    
                    	// send signal
                    	System.out.println("MatchOrderToDrone signalling DroneID " + droneID + " getOrder() with OrderID " + orderID);
                    	DroneWorkflow droneWF = workflowClient.newWorkflowStub(DroneWorkflow.class, droneID);
                    	droneWF.gotOrder(orderID);
                    	// TODO
                    	// commit(orderID offset); commit(droneID offset);
                    	// i.e. need to keep track of partition and offset? What about topic?
                    	
                    	/*
                    	 * Following examples shows how to commit offset asynchronously with a callback and with the specified offset.

KafkaConsumer defines following method:

public void commitAsync(final Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback)
Where parameter 'offsets' is a map of offsets by partition with associate metadata. The callback is to recieve async commit callback (see last tutorial);
                    	 * before loop do this:
                    	 * topicPartition = new TopicPartition(TOPIC_NAME, 0);
      consumer.assign(Collections.singleton(topicPartition));
                    	 * 
                    	 * consumer.commitAsync(
                      Collections.singletonMap(topicPartition,  new OffsetAndMetadata(record.offset())),
                      (offsets, exception) -> {
                  System.out.printf("Callback, offset: %s, exception %s%n", offsets, exception);
              }
                    	 */
                    }
                    }
                }
            }
        }

// this will start and stop a new consumer each call - not ideal.
// better solution? Start another thread with this consumer in it?
// unused now
static String getNextDrone()
{
	try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
        consumer.subscribe(Collections.singleton(dronesReadyTopicName));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
            	System.out.println("MatchOrderToDrone Consumer got Drone ready for Order");
                System.out.println(String.format("topic = %s, partition = %s, offset = %d, key = %s, value = %s",
                        record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                consumer.commitAsync();
                return record.value().toString();        
                }
            }
        }
}

}
