package DroneDeliveryDemo;

public class CommonProps {

	public CommonProps() {
	}
	
	// Cadence server IP and domain
	final static String host1  = "34.195.123.250";
	final static String host2 = "34.199.37.19";
	final static String host3 = "52.0.232.41";
	final static String host4 = "18.211.192.93"; // 2nd non GUI host
	final static String host = "lb-04eb046c-3e94-461b-a049-d2c27-972c34a80dc0559e.elb.us-east-1.amazonaws.com";
	final static String domainName = "droneDemo4";

	final static String orderjobsTopicName = "orderjobs16";  // Kafka topic to send orders waiting for drone allocation
	final static String newordersTopicName = "neworders16";  // Kafka topic to request new order WF creation
	final static String dronesReadyTopicName = "dronesready16"; // Kafka topic for drones to request an order. 
}
