# Apache Kafka Connect PostgreSQL Sink Connector

## Description

This code is for an Apache Kafka Connect PostgreSQL Sink Connector (for JSON only).

This demo comes from this [blog post](https://www.instaclustr.com/blog/kafka-postgres-connector-pipeline-series-part-6/).

It's a customized version of this connector: <https://github.com/ibm-messaging/kafka-connect-jdbc-sink>

It is intended for a single purpose: It takes a Kafka record which has a value in JSON, and inserts the entire JSON object into a PostgreSQL table. Currently the column name is hardcoded to be `json_object`, which has a data type of `jsonb`. The table also has an `id` column which is the primary key (automatically incremented). If the table doesn't exist it's automatically created with these 2 columns, and a GIN index for the `json_object` column (with a unique name). It just "works" and keeps on working even if there are errors on the PostgreSQL side (e.g. PostgreSQL checks if the JSON is well formed, and complains if it's not, but the connector keeps running).

## Getting Started

1. Build the connector JAR from the source code.
2. Deploy the JAR to your Kafka Connect worker's plugin path.
3. Configure and deploy the connector using the Kafka Connect REST API or your managed service's connector management interface.
4. The connector will automatically create the target PostgreSQL table if it does not exist, including the `id` (primary key) and `json_object` (jsonb) columns with a GIN index.

Example connector configuration:
```json
{
  "name": "postgres-json-sink",
  "config": {
    "connector.class": "com.instaclustr.kafka.connect.jdbc.sink.JdbcSinkConnector",
    "tasks.max": "1",
    "topics": "<your-kafka-topic>",
    "connection.url": "jdbc:postgresql://<host>:<port>/<database>",
    "connection.user": "<username>",
    "connection.password": "<password>"
  }
}
```

### Prerequisites

- **Apache Kafka Connect** cluster (e.g., [Instaclustr managed Kafka Connect](https://www.instaclustr.com/products/apache-kafka/))
- **PostgreSQL** database (e.g., [Instaclustr managed PostgreSQL](https://www.instaclustr.com/products/postgresql/))
- **Java 8** or higher (for building from source)
- Kafka topics producing JSON-formatted messages

## Deployment

1. Build the connector:
   ```bash
   mvn clean package
   ```
2. Copy the resulting JAR to your Kafka Connect plugin directory.
3. Restart Kafka Connect workers to pick up the new plugin.
4. Deploy the connector via the [Kafka Connect REST API](https://kafka.apache.org/documentation/#connect_rest):
   ```bash
   curl -X POST http://<connect-host>:8083/connectors \
     -H "Content-Type: application/json" \
     -d @connector-config.json
   ```

For Instaclustr managed Kafka Connect, refer to the [support documentation](https://www.instaclustr.com/support/documentation/kafka-connect/).

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
