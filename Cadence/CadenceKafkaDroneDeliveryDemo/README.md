# Drone Delivery Demo App

## Description

This is from the Cadence blog series below. It uses Cadence workflows and multiple Kafka integration patterns to simulate a drone delivery system. The demo showcases order and delivery workflows orchestrated by Uber Cadence with Apache Kafka handling the messaging between microservices.

The application demonstrates:
- Order placement and processing workflows
- Drone delivery assignment and tracking
- Integration patterns between Cadence workflows and Kafka topics
- Scalability testing (how many drones can we fly?)

## Blog Series

- [Part 1: Spinning Your Workflows With Cadence!](https://www.instaclustr.com/blog/spinning-your-workflows-with-cadence/)
- [Part 2: Spinning Apache Kafka Microservices With Cadence Workflows](https://www.instaclustr.com/blog/spinning-apache-kafka-microservices-with-cadence-workflows/)
- [Part 3: Spinning Your Drones With Cadence - Introduction](https://www.instaclustr.com/blog/spinning-your-drones-with-cadence-introduction/)
- [Part 4: Architecture, Order and Delivery Workflows](https://www.instaclustr.com/blog/spinning-your-drones-with-cadence-and-apache-kafka-architecture-order-and-delivery-workflows/)
- [Part 5: Integration Patterns and New Cadence Features](https://www.instaclustr.com/blog/spinning-your-drones-with-cadence-and-apache-kafka-integration-patterns-and-new-cadence-features/)
- [Part 6: How Many Drones Can We Fly?](https://www.instaclustr.com/blog/spinning-your-drones-with-cadence-and-apache-kafka-how-many-drones-can-we-fly/)

## Features

- **Order Workflows**: Manages the lifecycle of customer orders from placement through fulfillment
- **Delivery Workflows**: Coordinates drone assignment, pickup, transit, and delivery stages
- **Kafka Integration**: Uses multiple Kafka topics for communication between workflow stages and microservices
- **Web Dashboard**: Includes an HTML-based visualization for monitoring drone delivery status
- **Scalability**: Designed to test concurrent workflow execution and Kafka throughput under load

## Getting Started

1. Configure your Kafka broker IPs and credentials in `consumer.properties` and `producer.properties`.
2. Update the Cadence host IP address in the Java source files.
3. Build the project using Maven:
   ```bash
   mvn clean install
   ```
4. Start the Kafka consumers for processing delivery events.
5. Launch the Cadence workflow starters to begin placing orders and dispatching drones.
6. Open the included HTML dashboard to visualize drone delivery progress.

Refer to the [blog series](#blog-series) for detailed step-by-step walkthrough of the full demo setup.

### Prerequisites

- **Java 8** or higher
- **Apache Maven** for building the project
- **Apache Kafka cluster** (e.g., Instaclustr managed Kafka) with SASL authentication and auto topic creation enabled
- **Cadence server** (e.g., Instaclustr managed Cadence)
- The IP address of the machine running the code must be added to both Kafka and Cadence cluster firewall configurations
- Kafka broker IPs, username, and password configured in the properties files
- Cadence host IP set in the Java source files

## Deployment

This project is intended to run as a local demo against remote Kafka and Cadence clusters:

1. Provision an [Instaclustr Kafka cluster](https://www.instaclustr.com/products/apache-kafka/) with SASL authentication and auto topic creation enabled.
2. Provision an [Instaclustr Cadence cluster](https://www.instaclustr.com/products/cadence/).
3. Add your client machine's IP to the allowed firewall rules for both clusters.
4. Update the properties files and source code with your cluster connection details.
5. Build and run the application as described in Getting Started.

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
