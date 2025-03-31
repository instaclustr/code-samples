# Apache Kafka Sizing Calculator

This demo calculator accompanies the Apache Kafka Tiered Storage blog series (specifically parts 5 onwards) and is designed to allow for simple Kafka cluster size calculations at the cluster level, given:

An input producer workload (MBytes/s), replication, and remote storage write workloads (computed automatically from the producer workload).

Input consumer workloads (real-time, delayed, and remote) - these are assumed to read from cache, local storage, and remote storage only.

Options include SSD or EBS local storage, tiered storage enabled/disabled, and RF (3 by default).

Outputs are Local IO and Network in MBytes/s, and the fan-out ratio.

The initial version has bar graphs (kafka_calculator_graphs.html)

Note that the producer and consumer workloads are independent. This allows you to model alternatives including producer only, consumer only, or some combination of producer and consumer workloads, with or without tiered storage. 

You can use it compare IO and Network requirements (minimums) for an existing Kafka cluster before enabling remote tiered storage, or the impact of increasing the remote consumer workload rate, etc.

There are lots of assumptions, see the blogs for details. The plan is to add functionality as required over time, potentially resulting in a fully-fledged Apache Kafka performance model including IO, Network and CPU!

Update for blog part 7! A new version with Kafka storage calculations (but no graphs) is available as kafka_storage.html 

## Instructions

It's just javascript and html, so download the file to your local disk and point your browser at it.

## The blogs are available here:

https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-1/

https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-2/

https://www.instaclustr.com/blog/how-to-size-apache-kafka-clusters-for-tiered-storage-part-3/

Background blogs on Kafka tiered storage

https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-1/

https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-2/

https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-3/

https://www.instaclustr.com/blog/apache-kafka-tiered-storage-part-4/

