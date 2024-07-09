package DroneDeliveryDemo;

public class CommonProps {

	public CommonProps() {
	}
	
	// Cadence server IP and domain
	final static String host  = "34.195.123.250";
	final static String domainName = "droneDemo";

	final static String orderjobsTopicName = "orderjobs2";  // Kafka topic to send orders waiting for drone allocation
	final static String newordersTopicName = "neworders2";  // Kafka topic to request new order WF creation
}
