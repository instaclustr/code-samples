
# Project Title

Demo code for ZooKeeper Blog - [Apache Zookeeper Meets the Dining Philosophers](https://www.instaclustr.com/blog/apache-zookeeper-meets-the-dining-philosophers/)

# Project statement

This project demonstrates the application of Apache ZooKeeper in solving the classic Dining Philosphers problem, a common synchronization issue in concurrent programming. By utilizing ZooKeeper's distributed coordination capabilities, the project showcases how to prevent deadlocks and manage resource allocation efficiently in distributed systems. The sample code provides a practical example of implementing distributed locks and ephemeral nodes to synchronize actions across multiple processes.

## Features

* Distributed Coordination: Utilizes ZooKeeper to coordinate actionas across distributed systems, preventing deadlocks
* Implementation of Distributed Locks: Demonstrates the use of distributed locks to manage resource access among multiple processes
* Ephemeral Nodes:  Uses ZooKeeper's ephemeral nodes to ensure that locks are automatically released if a process fails
* Resource Allocation Management: Shows how to efficiently allocate resources in a distributed environment

### Prerequisites

Java
ZooKeeper (either on localhost or running on Instaclustr)

## Author

* [Paul Brebner](https://github.com/paul-brebner)

See also the list of [MAINTAINERS]( https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the Apache 2.0 license.
