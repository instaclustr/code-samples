package com.instaclustr.blogs.CadenceKafkaExample;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
// kafka consumer
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;

import com.instaclustr.blogs.CadenceKafkaExample.BlogCadenceKafkaExample_Cadence.ExampleWorkflow;
import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;



// Kafka consumer that gets message sent from the Kafka producer running in a Cadence workflow
// And sends a replay using either Cadence signals or activity completions

public class BlogCadenceKafkaExample_KafkaConsumer {
	
	static Properties kafkaProps;
    
    public static void main(String[] args) {
    	
    	// This code runs as a Kafka consumer, so needs Kafka cluster configuration, but it also needs
    	// Cadence cluster configuration and domain name as it sends a response back to Cadence.
    	// It assumes the domain exists
    	String host  = "Cadence Host IP";
    	String domainName = "sample-domain5";
    	
        // To link the workflow implementation to the Cadence framework, it should be
        // registered with a worker that connects to a Cadence Service.
        WorkflowClient workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build()),
                        WorkflowClientOptions.newBuilder().setDomain(domainName).build());
       
        ActivityCompletionClient completionClient = workflowClient.newActivityCompletionClient();

        // Kafka consumer setup
        
        Properties kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("consumer.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // For each message check if the Header contains either the WF ID or Task ID.
        // If WF Id, send signal; else if Task ID, use activity completion.
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
            consumer.subscribe(Collections.singleton("topic1"));

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println(String.format("topic = %s, partition = %s, offset = %d, key = %s, value = %s",
                            record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                    
                    String id = "";
                    byte[] task = null;
                    
                    for (Header header : record.headers()) {  
                        System.out.println("Consumer: header key " + header.key() + ", header value " + new String(header.value())); 
                        if (header.key().matches("Cadence_WFID"))
                        	id = new String(header.value());
                        else if (header.key().matches("Cadence_TASKID"))
                            task = Arrays.copyOf(header.value(), header.value().length);
                    }
                    
                    if (id != "")
                    {
                    	System.out.println("Cadence_WFID found in header, signalling WF now!");
                    
                    	// Creates workflow client stub for a known execution. Use it to send signals or queries to a running workflow. Do not call methods annotated with @WorkflowMethod.
                    	ExampleWorkflow workflowById =
                                workflowClient.newWorkflowStub(ExampleWorkflow.class, id);
                        workflowById.sendSignal(record.value() + " sending Signal");
                    }
                    else if (task != null)
                    {
                    	System.out.println("Task ID found in header = " + task.toString() + ", completing task now!");
                    	completionClient.complete(task, record.value() + " completing");
                    }
                    // else do "normal" processing
                }
            }
        }

     
    }
}