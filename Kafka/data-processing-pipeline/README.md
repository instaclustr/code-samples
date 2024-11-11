# Building a Real-Time Tide Data Processing Pipeline: Using Apache Kafka, Kafka Connect, Elasticsearch, and Kibana

## Description

This series looks at how to build a "zero-code" real-time Tide Data Processing Pipeline using multiple Instaclustr Managed Services/clusters.
Along the way we also add Apache Camel Kafka Connector, Prometheus and Grafana to the mix. There's no code (that's sort of the point with Kafka Connectors), but lots of examples of Kafka connector configurations, and Prometheus configuration as well.
Note that the full explanation of what connectors I used, how to deploy, configure, run and monitor them etc is in the blogs!

## [Blog 1](https://www.instaclustr.com/data-processing-pipeline/)

## [Blog 2](https://www.instaclustr.com/data-processing-pipeline-part-2/)

## [Blog 3](https://www.instaclustr.com/blog/getting-to-know-apache-camel-kafka-connectors/)

## [Blog 4](https://www.instaclustr.com/blog/monitoring-kafka-connect-pipeline-metrics-with-prometheus-pipeline-series-part-4/)

## [Blog 5](https://www.instaclustr.com/blog/scaling-kafka-connect-streaming-data-processing-pipeline-series-part-5/)

## [Blog 6](https://www.instaclustr.com/blog/kafka-postgres-connector-pipeline-series-part-6/)

## [Blog 7](https://www.instaclustr.com/blog/apache-superset-pipeline-series-part-7/)

## [Blog 8](https://www.instaclustr.com/blog/kafka-connect-elasticsearch-pipeline-series-part-8/)

## [Blog 9](https://www.instaclustr.com/blog/postgresql-pipeline-series-part-9/)

## [Blog 10](https://www.instaclustr.com/blog/kafka-connect-pipelines-conclusion-pipeline-series-part-10/)

## Getting Started

These are configuration examples from the 2021 [blog series](https://www.instaclustr.com/blog/data-processing-pipeline/)


### Prerequisites

They were designed to work with the Instaclustr managed services for Apache Kafka, Apache Kafka Connect, Elasticsearch, PostgreSQL and other open source software including Apache Camel Kafka Connectors and Apache Superset.
They worked correctly with the versions supported at the time (2021). Note that we no longer provide Elasticsearch, which has been replaced by OpenSearch. We now provide a [managed Kafka Sink Connector for OpenSearch](https://www.instaclustr.com/support/documentation/kafka-connect/bundled-kafka-connect-plugins/opensearch-sink-connector/).

The Apache Camel Kafka Connectors are documented and available [here](https://camel.apache.org/camel-kafka-connector/next/user-guide/index.html).

I also used this open source [Kafka REST source connector](https://github.com/llofberg/kafka-connect-rest).

To test the error handling, I used this alternative [Kafka Elasticsearch sink connector](https://github.com/confluentinc/kafka-connect-elasticsearch). 

And for testing (e.g. synthetic load generation) I used this [Kafaka connect datagen source connector](https://github.com/confluentinc/kafka-connect-datagen). 

For Blog 4, I used Prometheus and Grafana to monitor the connector metrics, see [our support documentation for the current way of monitoring with Prometheus](https://www.instaclustr.com/support/api-integrations/integrations/instaclustr-monitoring-with-prometheus/).

Blog 6 uses a custom Kafka PostgreSQL sink connector that I wrote, [here](https://github.com/instaclustr/kafka-connect-jdbc-sink). 

Blog 7 uses Apache Superset for data visualisation. It can be obtained [here](https://superset.apache.org/docs/installation/docker-compose/).

### Configuration

These are the example configuration files, data, etc.  See the specific blogs for further details for the different connector types.

## Deployment

The latest [support documentation for Instaclustr Kafka Connect is here](https://www.instaclustr.com/support/documentation/kafka-connect/). 

## Authors

* **Paul Brebner** - *Initial work* - [Instaclustr by NetApp](https://github.com/Instaclustr)

See also the list of [MAINTAINERS]( https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License


This project is licensed under the Apache 2.0 license.



