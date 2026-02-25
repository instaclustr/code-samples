# Apache Kafka Sizing Calculator

## Description

This demo calculator accompanies the Apache Kafka Tiered Storage blog series (specifically parts 5 onwards) and is designed to allow for simple Kafka cluster size calculations at the cluster level, given:

- An input producer workload (MBytes/s), replication, and remote storage write workloads (computed automatically from the producer workload)
- Input consumer workloads (real-time, delayed, and remote) - these are assumed to read from cache, local storage, and remote storage only
- Options include SSD or EBS local storage, tiered storage enabled/disabled, and RF (3 by default)
- Outputs are Local IO and Network in MBytes/s, and the fan-out ratio

The calculator includes two versions:
- **kafka_calculator_graphs.html** - Interactive calculator with Chart.js bar graph visualizations for IO and network analysis
- **kafka_storage.html** - Extended version with storage size and monthly cost calculations (local and remote retention, cost per GB/month)

Note that the producer and consumer workloads are independent. This allows you to model alternatives including producer only, consumer only, or some combination of producer and consumer workloads, with or without tiered storage.

You can use it to compare IO and Network requirements (minimums) for an existing Kafka cluster before enabling remote tiered storage, or the impact of increasing the remote consumer workload rate, etc.

There are lots of assumptions — see the blogs for details. The plan is to add functionality as required over time, potentially resulting in a fully-fledged Apache Kafka performance model including IO, Network, and CPU.

## Getting Started

1. Download the HTML file(s) to your local disk.
2. Open the file in your web browser.
3. Adjust the input parameters (producer workload, consumer throughputs, storage type, replication factor).
4. View the calculated results for IO, network, and storage requirements.

### Prerequisites

- A modern web browser (Chrome, Firefox, Edge, Safari)
- No server, build tools, or installation required — these are standalone HTML/JavaScript files

## Deployment

No deployment required. These are self-contained HTML files that run entirely in the browser.

## Blog Series

### Sizing blogs:
- [How to Size Apache Kafka Clusters for Tiered Storage - Part 1](https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-1/)
- [How to Size Apache Kafka Clusters for Tiered Storage - Part 2](https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-2/)
- [How to Size Apache Kafka Clusters for Tiered Storage - Part 3](https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-3/)

### Background blogs on Kafka tiered storage:
- [Apache Kafka Tiered Storage - Part 1](https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-1/)
- [Apache Kafka Tiered Storage - Part 2](https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-2/)
- [Apache Kafka Tiered Storage - Part 3](https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-3/)
- [Apache Kafka Tiered Storage - Part 4](https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-4/)

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
