# Spinning Apache Kafka Microservices With Cadence Workflows

Example code for the [Cadence Kafka Blog](https://www.instaclustr.com/blog/spinning-apache-kafka-microservices-with-cadence-workflows-part-2/) demonstrating how to integrate sending a message to Apache Kafka and getting a reply back using Uber Cadence workflows, with support for both signal-based and activity-completion-based reply patterns.

## Description

This project showcases two approaches for integrating Apache Kafka with Cadence workflows:

- **Signal-based replies**: The Kafka consumer sends a Cadence signal back to the originating workflow using the workflow ID from message headers.
- **Activity-completion-based replies**: The Kafka consumer completes an outstanding Cadence activity using a task token from message headers.

The demo starts 10 parallel workflows that each send a message to a Kafka topic and then wait for a reply. A separate Kafka consumer reads these messages and sends replies back to Cadence using the appropriate method.

## Getting Started

1. Configure your Kafka broker IPs and credentials in `consumer.properties` and `producer.properties`.
2. Update the Cadence host IP address in the Java source files.
3. Build the project using Maven:
   ```bash
   mvn clean install
   ```
4. Run the Kafka Consumer class first:
   ```bash
   java -cp target/*.jar BlogCadenceKafkaExample_KafkaConsumer
   ```
5. Run the Cadence workflow class with either signal or activity completions flag set to `true`/`false`:
   ```bash
   java -cp target/*.jar BlogCadenceKafkaExample_Cadence
   ```

The Kafka consumer detects whether the reply should be sent via signal or activity completion based on message headers.

### Prerequisites

- **Java 8** or higher
- **Apache Maven** for building the project
- **Apache Kafka cluster** (e.g., Instaclustr managed Kafka) with auto topic creation enabled
- **Cadence server** (e.g., Instaclustr managed Cadence)
- The IP address of the machine running the code must be added to both Kafka and Cadence cluster firewall configurations
- Kafka broker IPs, username, and password configured in `consumer.properties` and `producer.properties`
- Cadence host IP set in the Java source files

## Deployment

This project is intended to run as a local demo against remote Kafka and Cadence clusters. To deploy:

1. Provision an [Instaclustr Kafka cluster](https://www.instaclustr.com/products/apache-kafka/) with SASL authentication and auto topic creation enabled.
2. Provision an [Instaclustr Cadence cluster](https://www.instaclustr.com/products/cadence/).
3. Add your client machine's IP to the allowed firewall rules for both clusters.
4. Update the properties files and source code with your cluster connection details.
5. Build and run the consumer and workflow classes as described in Getting Started.

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
