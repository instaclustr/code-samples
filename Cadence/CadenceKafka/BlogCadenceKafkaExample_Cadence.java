package com.instaclustr.blogs.CadenceKafkaExample;


import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.thrift.TException;

import com.uber.cadence.BadRequestError;
import com.uber.cadence.ClientVersionNotSupportedError;
import com.uber.cadence.DomainAlreadyExistsError;


import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.ServiceBusyError;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

// Kafka producer
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;


// Simple example of integrating Cadence with Kafka to enable reuse of Kafka Consumer based microservices.
// This is the Cadence side code. It sends a message to a Kafka topic and waits for a reply, either using signals or activity completions.
// For the demo to work, you need a Cadence cluster, and Kafka cluster. This class works on the Cadence cluster, the other class runs as a Kafka consumer.

public class BlogCadenceKafkaExample_Cadence {
	
	// Cadence host
	static String host  = "cadence host IP";
	
	static final String activityName = "ExampleActivity";
	
	static Properties kafkaProps;

    // Workflow interface has to have at least one method annotated with @WorkflowMethod.
    public interface ExampleWorkflow {
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = 120, taskList = activityName)
        String startWorkflow(String name);
        @SignalMethod
        void sendSignal(String name);
    }
    
    // 2 approaches, sendKafka() waits in WF, sendKafkaBlocking waits for completion
    public interface ExampleActivities
    {
    	@ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
    	 String sendKafka(String name);
    	@ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
   	 	String sendKafkaBlocking(String name);
    }
    
    public static class ExampleWorkflowImpl implements ExampleWorkflow {
    	
    	String message = "";
    	
    	private ExampleActivities activities = null;
    	
    	public ExampleWorkflowImpl() {
            this.activities = Workflow.newActivityStub(ExampleActivities.class);
        }
    	
        @Override
        public String startWorkflow(String name) {
        	System.out.println("Started workflow! ID=" + Workflow.getWorkflowInfo().getWorkflowId());
        	
        	// Configure to either use signals or activity completions to get the response back from Kafka
        	Boolean signal = true;
        	
        	if (signal)
        	{
        	
        		System.out.println("Signal version!");
        		// the return value is ignored
        		String r1 = activities.sendKafka(name);
        	
        		System.out.println("sendKafka ID=" + Workflow.getWorkflowInfo().getWorkflowId());

        		// wait until the signal is sent from Kafka consumer back to this workflow with the response
        		Workflow.await(() -> message != "");
        		
        		System.out.println("workflow got signal = " + message);
            	
            	return message;
        	}
        	else
        	{
        		System.out.println("Activity Completion version!");
        		String r1 = activities.sendKafkaBlocking(name);
        		System.out.println("workflow activity completed with " + r1);
        		return r1;
        	}
        }
        
        @Override
        public void sendSignal(String msg) {
        	System.out.println("signal method got " + msg);
        	message = msg;
        }
       
    }
    
    public static class ExampleActivitiesImpl implements ExampleActivities
    {
    	// the signal version
         public String sendKafka(String msg) {
        	 String id = Activity.getWorkflowExecution().getWorkflowId();
        	 System.out.println("Send to Kafka topic1 " + msg + " from WF " + id);
        	
        	 ProducerRecord<String, String> producerRecord = new ProducerRecord<>("topic1", id, msg);
        	 
        	 // Also send the WF id in the Kafka header, so that Kafka consumer can send signal back again
        	 producerRecord.headers().add(new RecordHeader("Cadence_WFID", id.getBytes()));

             try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps)) {
                 producer.send(producerRecord);
                 producer.flush();
             } catch (Exception e) {
                 e.printStackTrace();
             }
             
        	 return "done"; 
         }
         
         // the activity completion version
         public String sendKafkaBlocking(String msg) {
        	 String id = Activity.getWorkflowExecution().getWorkflowId();
        	
        	 ProducerRecord<String, String> producerRecord = new ProducerRecord<>("topic1", id, msg);
        	 
        	 byte[] taskToken = Activity.getTaskToken(); // Used to correlate reply.
        	 
        	 producerRecord.headers().add(new RecordHeader("Cadence_TASKID", taskToken));
        	 
        	 System.out.println("Blocking send to Kafka topic1 " + msg + " from WF " + id + ", task " + taskToken.toString());

             try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps)) {
                 producer.send(producerRecord);
                 producer.flush();
             } catch (Exception e) {
                 e.printStackTrace();
             }
             
             // Tell the activity not to complete on return
             Activity.doNotCompleteOnReturn();
        	 // Note that the return value is ignored, and replaced by the completion supplied value.
             return "replacedByCompletionValue";
         }
    }

    // from here https://github.com/uber/cadence-java-samples/blob/master/src/main/java/com/uber/cadence/samples/common/RegisterDomain.java
    public static void registerDomain(String domain)
    {   	
    	String name = "";
    	
    	IWorkflowService cadenceService = new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build());
        RegisterDomainRequest request = new RegisterDomainRequest();
        request.setDescription(name);
        request.setEmitMetric(false);
        request.setName(domain);
        int retentionPeriodInDays = 1;
        request.setWorkflowExecutionRetentionPeriodInDays(retentionPeriodInDays);
        try {
          cadenceService.RegisterDomain(request);
          System.out.println(
              "Successfully registered domain \""
                  + domain
                  + "\" with retentionDays="
                  + retentionPeriodInDays);
        } catch (DomainAlreadyExistsError e) {
          System.out.println("Domain \"" + domain + "\" is already registered");
        } catch (BadRequestError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServiceBusyError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientVersionNotSupportedError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public static void main(String[] args) {
    	
    	
    	String domainName = "sample-domain5";
    	
    	System.out.println("registerDomain(" + domainName + ")");
    	registerDomain(domainName);
    	
        // To link the workflow implementation to the Cadence framework, it should be
        // registered with a worker that connects to a Cadence Service.
        WorkflowClient workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build()),
                        WorkflowClientOptions.newBuilder().setDomain(domainName).build());

        // Get worker to poll the task list.
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(activityName);

        // Workflows are stateful. So you need a type to create instances.
        worker.registerWorkflowImplementationTypes(ExampleWorkflowImpl.class);

        
        worker.registerActivitiesImplementations(new ExampleActivitiesImpl());
        
        // Start listening to the workflow and activity task lists.
        factory.start();
        
        // Kafka Producer setup
        kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("producer.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // start 10 workflows
        int number = 10;
        for (int i=0; i < number; i++)
        {
        	ExampleWorkflow exampleWorkflow = workflowClient.newWorkflowStub(ExampleWorkflow.class);
            
        	WorkflowExecution workflowExecution = WorkflowClient.start(exampleWorkflow::startWorkflow, "Thing" + i);
        }

        try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        
        // Have to wait until all workflows have finished otherwise there won't be any worker threads running!
        try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


        
        System.exit(0);
    }
}