# Kafka 4.0 New Features: Next Generation Consumer Rebalance Protocol

## Description

Example code demonstrating the new Consumer Rebalance Protocol introduced in Apache Kafka 4.0. This new protocol can achieve up to 20x faster consumer group rebalancing compared to the classic protocol.

The following examples are from the blog "Rebalance your Apache Kafka partitions with the next generation Consumer Rebalance Protocol — up to 20x faster!"

This project includes:
- **rebalance_experiment.sh** - A bash script for benchmarking rebalance performance. It creates a topic with 100 partitions, starts 10 consumers, then scales to 1000 partitions while monitoring how fast the rebalance completes.
- **Test4.java** - A sample Java consumer using the new `group.protocol=consumer` protocol setting with the `uniform` remote assignor.

## Getting Started

### Running the Rebalance Experiment

1. Download and install [Apache Kafka 4.0](https://kafka.apache.org/downloads).
2. Update the broker IP address in `rebalance_experiment.sh`.
3. Run the script:
   ```bash
   bash rebalance_experiment.sh
   ```
4. To test the new consumer protocol, edit the script and change:
   ```
   --consumer-property group.protocol=classic
   ```
   to:
   ```
   --consumer-property group.protocol=consumer
   ```

### Running the Java Consumer

1. Add the Kafka 4.0 client dependency to your project:
   ```xml
   <dependency>
     <groupId>org.apache.kafka</groupId>
     <artifactId>kafka-clients</artifactId>
     <version>4.0.0</version>
   </dependency>
   ```
2. Update the bootstrap server address in `Test4.java`.
3. Compile and run:
   ```bash
   javac -cp kafka-clients-4.0.0.jar Test4.java
   java -cp .:kafka-clients-4.0.0.jar Test4
   ```

### Prerequisites

- **Apache Kafka 4.0** (local installation or managed service such as [NetApp Instaclustr Kafka](https://www.instaclustr.com/products/apache-kafka/) running version 4.0)
- **Bash** shell (for the rebalance experiment script)
- **Java 8** or higher (for the Java consumer example)
- A running Kafka 4.0 cluster accessible from your machine

## Deployment

These are local demo scripts intended to run against a Kafka 4.0 cluster. No special deployment is required beyond having Kafka 4.0 available.

For a managed Kafka 4.0 cluster, see [Instaclustr managed Apache Kafka](https://www.instaclustr.com/products/apache-kafka/).

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
