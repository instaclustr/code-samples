
# Spinning Apache Kafka Microservices With Cadence Workflows

Example code for Cadence Kafka Blog <https://www.instaclustr.com/blog/spinning-apache-kafka-microservices-with-cadence-workflows-part-2/> on how to integrate sending a message to Kafka and getting a reply back, using signals or activity completions.

## Getting Started

Run the Kafka Consumer class first, and then run the Cadence class with either signal or activity completeions flag true/false.
The Kafka cluster detects if the reply should be sent with signal or activity completion.

### Prerequisites

Needs to have an Instaclustr Kafka and Cadence cluster running, the IP address of the server running the code added to both cluster configurations, with Kafka auto topic creation enabled, with host IPs, user and password set in code and kafka properties files.

## Deployment

Coming Soon!

## Authors

* **Paul Brebner** - *Initial work* - [Instaclustr by NetApp](https://github.com/Instaclustr)

See also the list of [MAINTAINERS]( https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
