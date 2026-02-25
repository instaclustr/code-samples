# Apache ZooKeeper Meets the Dining Philosophers

## Description

Demo code for the ZooKeeper blog - [Apache ZooKeeper Meets the Dining Philosophers](https://www.instaclustr.com/blog/apache-zookeeper-meets-the-dining-philosophers/).

This project demonstrates the application of Apache ZooKeeper in solving the classic Dining Philosophers problem, a common synchronization issue in concurrent programming. By utilizing ZooKeeper's distributed coordination capabilities, the project showcases how to prevent deadlocks and manage resource allocation efficiently in distributed systems. The sample code provides a practical example of implementing distributed locks and ephemeral nodes to synchronize actions across multiple processes.

## Features

* **Distributed Coordination**: Utilizes ZooKeeper to coordinate actions across distributed systems, preventing deadlocks
* **Implementation of Distributed Locks**: Demonstrates the use of distributed locks (via Curator InterProcessSemaphoreMutex) to manage resource access among multiple processes
* **Ephemeral Nodes**: Uses ZooKeeper's ephemeral nodes to ensure that locks are automatically released if a process fails
* **Resource Allocation Management**: Shows how to efficiently allocate resources in a distributed environment
* **Comprehensive Metrics**: Tracks meals, attempted meals, fork utilization, wait/eat/think time, and leader election frequency

## Getting Started

1. Clone the repository and navigate to the project directory.
2. Build the project using Maven:
   ```bash
   mvn clean install
   ```
3. Start a ZooKeeper instance (local or remote).
4. Update the ZooKeeper connection string in `ZookeeperMeetsDiningPhilosophers.java` if not using localhost:
   ```java
   // Default: 127.0.0.1:2181
   // For Instaclustr: "node1:2181,node2:2181,node3:2181"
   ```
5. Run the main class:
   ```bash
   java -cp target/*.jar ZookeeperMeetsDiningPhilosophers
   ```

The program runs 5 philosophers for 10 seconds by default and prints comprehensive statistics at completion, including total meals, fork utilization percentages, and per-philosopher metrics.

### Prerequisites

- **Java 8** or higher
- **Apache Maven** for building the project
- **Apache ZooKeeper** (either running locally on `127.0.0.1:2181` or a remote instance such as [Instaclustr managed ZooKeeper](https://www.instaclustr.com/products/apache-zookeeper/))

## Deployment

This project is a standalone demo. To run against a remote ZooKeeper ensemble:

1. Provision a ZooKeeper cluster (e.g., via [Instaclustr](https://www.instaclustr.com/products/apache-zookeeper/)).
2. Update the connection string in the source code with your cluster's node addresses.
3. Ensure your machine's IP is allowed in the cluster firewall configuration.
4. Build and run as described in Getting Started.

## Authors

* [Paul Brebner](https://github.com/paul-brebner) - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the Apache 2.0 license.
