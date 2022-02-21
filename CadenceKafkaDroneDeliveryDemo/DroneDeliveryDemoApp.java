package DroneDeliveryDemo;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.thrift.TException;

import com.uber.cadence.BadRequestError;
import com.uber.cadence.ClientVersionNotSupportedError;
import com.uber.cadence.DomainAlreadyExistsError;


import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.ServiceBusyError;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowService.AsyncProcessor.DescribeWorkflowExecution;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.common.MethodRetry;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import DroneMaths.DroneDeliveryApp2.DroneWorkflow;
import DroneMaths.DroneDeliveryApp2.OrderWorkflow;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

//import ExampleWorkflowApp3KafkaConsumer.ExampleWorkflow;

// Kafka producer
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;


// Demonstration Drone Delivery Demo Application using Cadence and multiple Apache Kafka integration patterns.
// To run you need Instaclustr managed Cadence and Kafka clusters provisioned.
// Set the host IPs addresses from the info found in the Instaclustr console.
// Kafka needs auto topic create enabled.
// Step 1: Run the CreateOrderWFConsumer (this runs forever)
// Step 2: Run this class, this creates the requested number of Drones which will then wait for orders to deliver, deliver them, recharge, and start new drone WF instances etc.
// Step 3: Run the CreateOrdersProducer - this will request n new orders and exit, triggering the rest of the actions.

public class DroneDeliveryDemoApp {
	
	// static String host  = "34.195.123.250";
	static String host = CommonProps.host;
	static final String orderjobsTopicName = CommonProps.orderjobsTopicName;
	
	static final String newordersTopicName = CommonProps.newordersTopicName;
	
	static final String domainName = CommonProps.domainName;
	
	static Properties kafkaProps;

	
	static final String orderActivityName = "OrderActivity";
	static final String droneActivityName = "DroneActivity";
	
	static final double timeScale = 0; // 1.0 is real-time, 0 is as fast it can go
	static LatLon baseLocation = new LatLon(-35.20586, 149.09462);
	static double droneSpeed = 20.0; // drone speed is 20km/h - assume it is either going or not going
	static double moveTime = 10.0; // time in s for each movement and location update
	static double pickupTime = 60.0; // seconds to collect order once arrived at location
	static double dropTime = 60.0; // seconds to drop the order once arrived at location
	static final double maxFlightTime = 30 * 60; // max time drones can fly for before flat battery, in s
	static double maxFlightDistance = droneSpeed * (maxFlightTime/(60*60)); // maximum distance drones can fly for, 10km for initial version
	static double maxLegDistance = maxFlightDistance / 4.0; // assumes that getting to order location and delivering are 2 legs, leaving us 2 legs (a safety margin) to get back to base
	static final double maxChargeTime = maxFlightTime/2.0; // assume recharging takes 1/2 of max flight time max
	
	static WorkflowClient workflowClient = null;
	static int numOrders = 1;	// each drone only has 1 order at present - bigger drones may be possible in future	

    // Workflow interface has to have at least one method annotated with @WorkflowMethod.
	// Order Workflows can take longer than Drone delivery workflows, but by how much?
    public interface OrderWorkflow {
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = (int) (maxFlightTime * 4), taskList = orderActivityName)
        String startWorkflow(String name);
        @SignalMethod
        void updateGPSLocation(LatLon l);
		@SignalMethod
        void signalOrder(String msg);
        @SignalMethod
        void updateLocation(String loc);
        @QueryMethod
        String getState();
        @QueryMethod
        LatLon getOrderLocation();
        @QueryMethod
        LatLon getDeliveryLocation();
    }
    
    public interface DroneWorkflow {
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = (int)maxFlightTime, taskList = droneActivityName)
        String startWorkflow(String name);
        @SignalMethod
        void updateGPSLocation(LatLon loc);
        @SignalMethod
        void updateCharge(double time);
		@SignalMethod
        void signalOrder(String msg);
        @QueryMethod
        String getState();
        @QueryMethod
        LatLon getLatLon();
        @QueryMethod
        LatLon getNextLatLon();
    }
    
    public interface OrderActivities
    {
    	// How long should an Order wait for a Drone?
    	@ActivityMethod(scheduleToCloseTimeoutSeconds = (int) maxFlightTime)
   	 	String readyForDelivery(String name);	
    }
    
    public interface DroneActivities
    {
    	// How long should a Drone wait to get an order? Is forever ok?
    	@ActivityMethod(scheduleToCloseTimeoutSeconds = (int) maxFlightTime)
    	// TODO Add explicit retries? @MethodRetry(maximumAttempts = 2, initialIntervalSeconds = 1, expirationSeconds = 30, maximumIntervalSeconds = 30)
   	 	String waitForOrder(String name);
    	// nextLeg time < maxFlightTime - experiment to add retry policy with optional failure on 1st attempt
    	@ActivityMethod(scheduleToCloseTimeoutSeconds = (int) maxFlightTime)
    	@MethodRetry(maximumAttempts = 2, initialIntervalSeconds = 1, expirationSeconds = 30, maximumIntervalSeconds = 30)
    	void nextLeg(LatLon start, LatLon end, boolean updateOrderLocation, String orderID);
    }
    
    public static class OrderWorkflowImpl implements OrderWorkflow {
    	
    	String orderName = "";
    	String newState = "";
    	String lastState = "";
    	
    	
    	ArrayList<String> updates = new ArrayList<String>();
    	ArrayList<String> locations = new ArrayList<String>();
    	ArrayList<LatLon> gpsLocations = new ArrayList<LatLon>();
    	
    	// current location
    	LatLon gpsLocation = null;
    	
    	LatLon startLocation = null;
    	LatLon deliveryLocation = null;

    	private OrderActivities activities = null;
    	
    	public OrderWorkflowImpl() {
            this.activities = Workflow.newActivityStub(OrderActivities.class);
        }
    	
    	// These are the actual workflow steps
        @Override
        public String startWorkflow(String name) {	
        	System.out.println("Started Order workflow " + name + ", ID=" + Workflow.getWorkflowInfo().getWorkflowId());
        	
        	startLocation = Workflow.sideEffect(LatLon.class, () -> DroneMaths.newDestination(baseLocation, 0.1, maxLegDistance));
        	System.out.println("Order WF startLocation = " + startLocation.toString());
        	
        	deliveryLocation = Workflow.sideEffect(LatLon.class, () -> DroneMaths.newDestination(startLocation, 0.1, maxLegDistance));
        	System.out.println("Order WF deliveryLocation = " + deliveryLocation.toString());

        	// A real activity - request a drone - could be triggered after some time period when order is actually ready in practice
        	activities.readyForDelivery(name);
        	
        	// order doesn't complete until this endState is reached.
        	String endState = "orderComplete";
        	boolean delivered = false;
        	while (!delivered)
        	{
        		Workflow.await(() -> newState != "");
        		System.out.println("order " + name + " got signal = " + newState);
        		updates.add(newState);
        		if (newState.equals(endState))
        		{
        			delivered = true;
        			System.out.println("Order WF exiting!");
        		}
        		lastState = newState;
        		newState = "";
        	} 	
        	return "Order " + name + " " + endState;	
        }
        
        @Override
        public void signalOrder(String state) {
        	System.out.println("Order " + orderName + " got a signal = " + state);
        	newState = state;
        }
        
        @Override
        public void updateLocation(String l) {
        	System.out.println("Order " + orderName + " new location = " + l);
        	locations.add(l);
        }
		
		@Override
        public void updateGPSLocation(LatLon l) {
        		System.out.println("Order " + orderName + " GPS Location " + l);
        		gpsLocations.add(l);
        		gpsLocation = l;
        }
        
        @Override
        public String getState() {
            return lastState;
        }
        
        @Override
        public LatLon getOrderLocation() {
            return startLocation;
        }
        
        @Override
        public LatLon getDeliveryLocation() {
            return deliveryLocation;
        }

		
       
    }
    
public static class DroneWorkflowImpl implements DroneWorkflow {
	
	String droneName = "";
	String state = "";
	String location = "";	// String name of location, not lat/lon GPS location
	double charge = 100.0; 	// percentage charge from 0 to 100; Drone WF starts fully charged.
	String orderID = "";
	OrderWorkflow orderWorkflow = null;

	ArrayList<String> updates = new ArrayList<String>();
	ArrayList<String> locations = new ArrayList<String>();
	ArrayList<LatLon> gpsLocations = new ArrayList<LatLon>(); // new GPS locations sent by activity
	LatLon gpsLocation = baseLocation;
	LatLon nextGPSLocation = null;
	
	// Flight Plan - Drone flies from start->order->delivery->end. Start and end are base.
	LatLon planStart = baseLocation;
	LatLon planOrder = null;
	LatLon planDelivery = null;
	LatLon planEnd = baseLocation;
	
	private DroneActivities activities = null;
	
	public DroneWorkflowImpl() {
        this.activities = Workflow.newActivityStub(DroneActivities.class);
    }
	
	public void newStateAndLocation(String state, String location)
	{
		this.state = state;
		this.location = location;
		System.out.println("Drone " + droneName + ": new state = " + state + ", location = " + location);
	}
	
	// In this version, some steps are local methods, but not all.
	// Some are activities, and some are just in-line
    @Override
    public String startWorkflow(String name) {
    	
    	droneName = name;
    	
    	System.out.println("Started Drone workflow " + name + ", ID=" + Workflow.getWorkflowInfo().getWorkflowId());
    	
    	// STEP 0 - ready
    	// Drones always start ready, at the base location
    	newStateAndLocation("ready", "base");
        
    	// STEP 1 - wait for an Order
    	// this step calls a real activity which blocks until an order arrives
    	// returns an OrderWorkflow which we used to signal Order WF, also sets OrderID which is just a String
    	// we assume that the Drone remains fully charged while waiting
    	orderWorkflow = step1_GetOrder();
    	newStateAndLocation("gotOrder", "base");

    	
        // STEP 2 - generate "flight plan" using the order and delivery locations from the Order
    	// The Order WF is responsible for generating random order and delivery locations that are within Drone range
        step2_GenerateFlightPlan();
    	newStateAndLocation("flightPlanGenerated", "base");

        // STEP 3 - another real activity - flying to get the order 
        System.out.println("Drone " + name + " flying to pickup Order");
    	newStateAndLocation("flyingToOrder", "betweenBaseAndOrder");
    	// Let the Order WF know that the drone is on the way
        orderWorkflow.signalOrder("droneOnWayForPickup");
        // nextLeg is where the Drone movement is calculated, causing the drone to "fly" from planStart to planOrder locations
        // false and null arguments ensure that the Order location isn't updated yet, but charge is reduced
        activities.nextLeg(planStart, planOrder, false, null);
        
        // STEP 4 - arrived at order location, collect order - this takes time and uses charge to
        System.out.println("Drone " + name + " picking up Order");
        newStateAndLocation("pickingUpOrder", "orderLocation");
        step4_pickUpOrder();
        
        // STEP 5 - flying to deliver the order   
        System.out.println("Drone " + name + " delivering Order...");
        newStateAndLocation("startedDelivery", "betweenOrderAndDelivery");
        // next GPS location drone flies to
        nextGPSLocation = planDelivery;
        // let Order WF know the delivery has started
        orderWorkflow.signalOrder("outForDelivery");
        orderWorkflow.updateLocation("onWay");
        // drone flies to delivery location, updating drone and order locations and drone charge as it goes
        activities.nextLeg(planOrder, planDelivery, true, orderID);
        
        // STEP 6 - drop order
        System.out.println("Drone " + name + " dropping Order!");
        newStateAndLocation("droppingOrder", "deliveryLocation");
        step6_dropOrder();
         
        // Step 7 - return to base
        System.out.println("Drone " + name + " returning to Base");
        newStateAndLocation("returningToBase", "betweenDeliveryAndBase");
        nextGPSLocation = planEnd;
        // fly back to base, update drone location and charge, but not Order location as it's already been delivered.
        activities.nextLeg(planDelivery, planEnd, false, null);
        
        // STEP 8 - back at base
        System.out.println("Drone " + name + " returned to Base!");
        newStateAndLocation("backAtBase", "base");
 
        // STEP 9 - check order - if successful then Order WF completes
        newStateAndLocation("checkOrder", "base");
        step9_checkOrder();  
        
        // Step 10 - delivery complete
        newStateAndLocation("droneDeliveryCompleted", "base");
              
        // Step 11 - charge
        newStateAndLocation("charging", "base");
        step11_recharge();
        
        // Step 12 - fully recharged
        newStateAndLocation("charged", "base");
        
        System.out.println(">>>>>>>>> Starting new Drone delivery WF with coninueAsnew with same WF ID!");
        
        // Workflow.continueAsNew(name);
        
    	return "Drone Delivery Workflow " + name + " completed!";	
    }
    
    private OrderWorkflow step1_GetOrder() {
    	// STEP 1 get order from Kafka            
        System.out.println("Drone " + droneName + " is waiting for an order...");
        orderID = activities.waitForOrder(droneName);
        System.out.println("Drone " + droneName + " got an order from Kafka + " + orderID);
        
        
        // Get the Order WF so we can send location and state updates to it
        try {
        	orderWorkflow = workflowClient.newWorkflowStub(OrderWorkflow.class, orderID);   
        	orderWorkflow.signalOrder("droneHasOrder");
        }
        catch (Exception e)
        {
        	System.out.println(e + " Expected order workflow to be running but it's not, giving up!");
        	// TODO Need better exception handling here, i.e. send orderId to exception queue, and retry this activity to get another order?
        	return null;
        }
        System.out.println("Drone " + droneName + " has got order " + orderID);
        
        return orderWorkflow;
	}
    
    private void step2_GenerateFlightPlan() {
    	LatLon orderLoc = orderWorkflow.getOrderLocation();
        LatLon deliveryLoc = orderWorkflow.getDeliveryLocation();
        
        // Generate "Flight plan"
        // Leg 1 is from planStart to Order
        planOrder = orderLoc;
        // set nextGPSLocation with the next location to fly to
        nextGPSLocation = planOrder;
        // Leg 2 is from order to delivery locations
        planDelivery = deliveryLoc;
        // Leg 3 is from delivery location back to start, planEnd is already set to planStart


        System.out.println("Drone " + droneName + " has generated a flight plan based on Order and Delivery locations");
        System.out.println("Start " + planStart.toString());
        System.out.println("Order " + planOrder.toString());
        System.out.println("Delivery " + planDelivery.toString());
        System.out.println("End " + planEnd.toString());

        double distance = 0;
        double distanceToDelivery = 0;
        double distanceToOrder = 0;
        distance += DroneMaths.distance(planStart, planOrder);
        distanceToOrder = distance;
        distance += DroneMaths.distance(planOrder, planDelivery);
        distanceToDelivery = distance;
        distance += DroneMaths.distance(planDelivery, planEnd);

        System.out.println("Drone " + droneName + " flight plan total distance (km) = " + distance);
        System.out.println("Drone " + droneName + " estimated total flight time (h) = " + distance/droneSpeed);
        System.out.println("Drone " + droneName + " distance to order (km) = " + distanceToOrder);
        System.out.println("Drone " + droneName + " estimated time until order pickup (h) = " + distanceToOrder/droneSpeed);
        System.out.println("Drone " + droneName + " distance to delivery (km) = " + distanceToDelivery);
        System.out.println("Drone " + droneName + " estimated time until delivery (h) = " + distanceToDelivery/droneSpeed);	
	}

    private void step4_pickUpOrder() {
    	// picking up takes some time
        System.out.println("Drone " + droneName + " picking up Order!");
        Workflow.sleep((int)(pickupTime * 1000 * timeScale));
        System.out.println("Drone " + droneName + " picked up Order!");
        orderWorkflow.signalOrder("pickedUpByDrone");
        orderWorkflow.updateLocation(location);
        // we assume that the drone is still using power while picking up the order
        updateCharge(pickupTime);
	}

    private void step6_dropOrder() {
    	 // dropping off takes some time
        Workflow.sleep((int)(dropTime * 1000 * timeScale));
        System.out.println("Drone " + droneName + " dropped Order!");
        orderWorkflow.updateLocation(location);
        orderWorkflow.signalOrder("delivered");
        // we assume that the drone is still using power while dropping the order
        updateCharge(dropTime);
	}
    
    // this method is intended to perform post delivery checks to ensure delivery was really successful
    // currently just checks if ordered is in "delivered" state
    private void step9_checkOrder() {
    	if (!orderWorkflow.getState().equals("delivered"))	
        {
        	System.out.println("Drone " + droneName + " undelivered order exception = " + orderID);
        	// Perform some compensating action
        }
        else
        	orderWorkflow.signalOrder("orderComplete"); // this terminates the order WF	
	}
    
    private void step11_recharge() {
      	int chargingTime = (int)((1-(charge/100.0)) * maxChargeTime);
        System.out.println("Drone " + droneName + " charging! charging time = " + chargingTime + "s");
        Workflow.sleep((int) (1000 * chargingTime * timeScale));
        charge = 100.0;	
  	}
	
	
	@Override
    public void signalOrder(String s) {
    	state = s;
    }
    
	@Override
    public void updateGPSLocation(LatLon loc) {
    	System.out.println("Drone " + droneName + " gps location update " + loc);
    	gpsLocations.add(loc);
    	gpsLocation = loc;
    }
	
	@Override
    public void updateCharge(double time) {
    	// given flying time, update remaining Charge
		double chargeUsed = (time/maxFlightTime) * 100.0;
		charge -= chargeUsed;
		if (charge < 0.0)
			charge = 0.0;
		System.out.println("Drone " + droneName + " charge now = " + charge + "%, last used = " + chargeUsed);
    }
    
    @Override
    public String getState() {
        return state;
    }
    
    @Override
    public LatLon getLatLon() {
        return gpsLocation;
    }
    
    @Override
    public LatLon getNextLatLon() {
        return nextGPSLocation;
    }  
}

    
    // Note we have to get activity info from Activity not Worker
	// https://cadenceworkflow.io/docs/java-client/implementing-activities/#accessing-activity-info
    public static class OrderActivitiesImpl implements OrderActivities
    {
         // activity which sends a message to Kafka to notify Drone to come and get it
         public String readyForDelivery(String name) {
        	 
        	 String id = Activity.getWorkflowExecution().getWorkflowId();
        	 System.out.println("Order WF readyForDelivery activity " + name + " id " + id);
        	
        	 // topic, key, value, all Strings
        	 ProducerRecord<String, String> producerRecord = new ProducerRecord<>(orderjobsTopicName, "", id);

             try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps)) {
                 producer.send(producerRecord);
                 producer.flush();
             } catch (Exception e) {
                 e.printStackTrace();
             }
             
        	 return "done"; 
         }  
         
    }
    
    public static class DroneActivitiesImpl implements DroneActivities
    {
    	 // keep track of number of invocations, used to test retries.
    	 private int count = 0;
         
         public String waitForOrder(String name) {
        	 // Kafka consumer that polls for a new Order that's been created and is ready for pickup to trigger Drone delivery trip
        	 // Each Drone can only have 1 order at a time, and each order can only be delivered by 1 drone (or drone wars may result)
        	 // However, if something goes wrong with drone and it cannot pickup the order, then it should put the order back into the Kafka topic for redelivery perhaps and increment a pickup attempts count in Order WF.
        	 Properties kafkaProps = new Properties();

             try (FileReader fileReader = new FileReader("consumer.properties")) {
                 kafkaProps.load(fileReader);
             } catch (IOException e) {
                 e.printStackTrace();
             }
             
             // We only want the Drone to get 1 order at a time, how to configure for 1 only?
             // set max.poll.records to 1 https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html
             // All consumers waiting for order are in a new consumer group
             // NOTE that this means we need partitions >= number of Drones - assumption is this is < 100 for performance reasons
             // What if millions? Then need to do something different
             kafkaProps.put("group.id", "waitForOrder");
             kafkaProps.put("max.poll.records", "1");
             
             try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps)) {
                 consumer.subscribe(Collections.singleton(orderjobsTopicName));

                 while (true) {
                     ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                     for (ConsumerRecord<String, String> record : records) {
                         System.out.print("waitForOrder got an order! ");
                         System.out.println(String.format("topic = %s, partition = %s, offset = %d, key = %s, value = %s",
                                 record.topic(), record.partition(), record.offset(), record.key(), record.value()));
                         // ensure that we don't get this order again
                         consumer.commitAsync();
                         return record.value().toString();
                     }
                 }
             }
             catch (Exception e)
             {
            	 e.printStackTrace();
             }
			return "";
         }
         	
         
         // nextLeg() - activity method to compute location from A to B and fly
         // What if it fails and is restarted? Need to set start to current location.  
         // Updates Drone location, and if updateOrderLocation true then updates the Order WF with orderID location
         public void nextLeg(LatLon start, LatLon end, boolean updateOrderLocation, String orderID)
         {
        	 System.out.println("nextLeg count = " + count++);
        	 
        	 WorkflowExecution execution = Activity.getWorkflowExecution();
        	 String id = execution.getWorkflowId();   	
			 DroneDeliveryDemoApp.DroneWorkflow droneWF = workflowClient.newWorkflowStub(DroneWorkflow.class, id);
			 DroneDeliveryDemoApp.OrderWorkflow orderWF = null;
			 
			 if (updateOrderLocation) 
			 {
				 try {
					 orderWF = workflowClient.newWorkflowStub(OrderWorkflow.class, orderID);
				 }
				 catch (Exception e) { System.out.println("failed to create orderWF!!!"); return; }
			 }
			 
			 LatLon actualLocation = droneWF.getLatLon();
			 System.out.println("Drone WF gpsLocation = " + actualLocation);
			 System.out.println("start loc = " + start);

			 // this version ignores provided start, and uses actual location instead!
			 LatLon here = actualLocation;
        	
        	while (true)
     		{
        		try {
					Thread.sleep((int)(moveTime * 1000 * timeScale));
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
     			
     			LatLon next = DroneMaths.nextPosition(here, end, droneSpeed, moveTime);
     			here = next;
     			System.out.println("Drone flew to new location = " + here.toString());
     			double distance = DroneMaths.distance(here, end);
     			System.out.println("Distance to destination = " + distance + " km");
     			
     			droneWF.updateGPSLocation(here);
     			droneWF.updateCharge(moveTime);
     			
     			if (updateOrderLocation)
     				orderWF.updateGPSLocation(here);
     			

     			if (end.sameLocation(here)) // within 1 m
     			{
     				System.out.println("Drone arrived at destination.");
     				return;
     			}
     			
     			// experiment to simulate failure and retry once only
     			if (count == 1)
            	{
            		System.out.println("nextLeg failed on count 1 - retry");
            		throw new IllegalStateException("nextLeg failed on count 1 - retry");
            	}
     		}

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
			e.printStackTrace();
		} catch (ServiceBusyError e) {
			e.printStackTrace();
		} catch (ClientVersionNotSupportedError e) {
			e.printStackTrace();
		} catch (TException e) {
			e.printStackTrace();
		}
    }
    
  

	public static void main(String[] args) {
    	
    	System.out.println("registerDomain(" + domainName + ")");
    	registerDomain(domainName);
    	
    	
        // To link the workflow implementation to the Cadence framework, it should be
        // registered with a worker that connects to a Cadence Service.
        workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.newBuilder().setHost(host).setPort(7933).build()),
                        WorkflowClientOptions.newBuilder().setDomain(domainName).build());

        // Get workers to poll the task list. 
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(orderActivityName);
        Worker worker2 = factory.newWorker(droneActivityName);


        // Workflows are stateful. So you need a type to create instances.
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        worker2.registerWorkflowImplementationTypes(DroneWorkflowImpl.class);

        // And don't forget to register Activities
        worker.registerActivitiesImplementations(new OrderActivitiesImpl());
        worker2.registerActivitiesImplementations(new DroneActivitiesImpl());
        
        // Start listening to the workflow and activity task lists.
        factory.start();
        
        kafkaProps = new Properties();

        try (FileReader fileReader = new FileReader("producer.properties")) {
            kafkaProps.load(fileReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
                
        ArrayList<DroneWorkflow> drones = new ArrayList<DroneWorkflow>();
        
        // start some Drone workflows
        int numDrones = 1;
        for (int i=0; i < numDrones; i++)
        {
        	DroneWorkflow droneWorkflow = workflowClient.newWorkflowStub(DroneWorkflow.class);
        	WorkflowExecution workflowExecution = WorkflowClient.start(droneWorkflow::startWorkflow, "Drone_" + i);
        	drones.add(droneWorkflow);
        }
        
        // Have to wait until all finished otherwise there won't be any worker threads running!
        try {
			Thread.sleep(600000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        
        System.exit(0);
    }
}