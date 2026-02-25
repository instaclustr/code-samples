# Real-Time Tide Data Processing Pipeline

## Description

This series looks at how to build a "zero-code" real-time Tide Data Processing Pipeline using Apache Kafka, Kafka Connect, Elasticsearch, and Kibana with multiple Instaclustr managed services/clusters.

Along the way we also add Apache Camel Kafka Connector, Prometheus and Grafana to the mix. There's no code (that's sort of the point with Kafka Connectors), but lots of examples of Kafka connector configurations, and Prometheus configuration as well. Note that the full explanation of what connectors were used, how to deploy, configure, run and monitor them etc. is in the blogs.

## Blog Series

- [Part 1: Data Processing Pipeline](https://www.instaclustr.com/data-processing-pipeline/)
- [Part 2: Data Processing Pipeline - Part 2](https://www.instaclustr.com/data-processing-pipeline-part-2/)
- [Part 3: Getting to Know Apache Camel Kafka Connectors](https://www.instaclustr.com/blog/getting-to-know-apache-camel-kafka-connectors/)
- [Part 4: Monitoring Kafka Connect Pipeline Metrics With Prometheus](https://www.instaclustr.com/blog/monitoring-kafka-connect-pipeline-metrics-with-prometheus-pipeline-series-part-4/)
- [Part 5: Scaling Kafka Connect Streaming Data Processing](https://www.instaclustr.com/blog/scaling-kafka-connect-streaming-data-processing-pipeline-series-part-5/)
- [Part 6: Kafka Postgres Connector](https://www.instaclustr.com/blog/kafka-postgres-connector-pipeline-series-part-6/)
- [Part 7: Apache Superset](https://www.instaclustr.com/blog/apache-superset-pipeline-series-part-7/)
- [Part 8: Kafka Connect Elasticsearch](https://www.instaclustr.com/blog/kafka-connect-elasticsearch-pipeline-series-part-8/)
- [Part 9: PostgreSQL](https://www.instaclustr.com/blog/postgresql-pipeline-series-part-9/)
- [Part 10: Kafka Connect Pipelines Conclusion](https://www.instaclustr.com/blog/kafka-connect-pipelines-conclusion-pipeline-series-part-10/)

## Getting Started

These are configuration examples from the 2021 [blog series](https://www.instaclustr.com/blog/data-processing-pipeline/). The included files are Kafka Connect connector configs, Elasticsearch mappings, ingest pipelines, Prometheus monitoring configs, and sample data.

### Prerequisites

These configurations were designed to work with the Instaclustr managed services for Apache Kafka, Apache Kafka Connect, Elasticsearch, PostgreSQL and other open source software including Apache Camel Kafka Connectors and Apache Superset. They worked correctly with the versions supported at the time (2021). Note that Elasticsearch has been replaced by OpenSearch. Instaclustr now provides a [managed Kafka Sink Connector for OpenSearch](https://www.instaclustr.com/support/documentation/kafka-connect/bundled-kafka-connect-plugins/opensearch-sink-connector/).

Additional tools and connectors used:
- [Apache Camel Kafka Connectors](https://camel.apache.org/camel-kafka-connector/next/user-guide/index.html)
- [Kafka REST Source Connector](https://github.com/llofberg/kafka-connect-rest)
- [Kafka Elasticsearch Sink Connector (Confluent)](https://github.com/confluentinc/kafka-connect-elasticsearch) (for error handling testing)
- [Kafka Connect DataGen Source Connector](https://github.com/confluentinc/kafka-connect-datagen) (for synthetic load testing)
- [Prometheus and Grafana](https://www.instaclustr.com/support/api-integrations/integrations/instaclustr-monitoring-with-prometheus/) (for monitoring)
- [Custom Kafka PostgreSQL Sink Connector](https://github.com/instaclustr/kafka-connect-jdbc-sink) (Blog 6)
- [Apache Superset](https://superset.apache.org/docs/installation/docker-compose/) (Blog 7)

### Configuration

These are the example configuration files, data, etc. See the specific blogs for further details for the different connector types.

## Deployment

The latest [support documentation for Instaclustr Kafka Connect is here](https://www.instaclustr.com/support/documentation/kafka-connect/).

Deploy connectors using the Kafka Connect REST API or the Instaclustr console. Refer to the individual blog posts for step-by-step deployment instructions for each connector type.

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the Apache 2.0 license.
