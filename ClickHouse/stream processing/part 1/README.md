# ClickHouse as a Stream Processor - Part 1: SQL Logic With Static Data

## Description

Example ClickHouse SQL code for the blog "All I want for Christmas is...another real-time stream processing technology — ClickHouse!"

This is an experiment using ClickHouse as a stream processing system instead of Kafka Streams or RisingWave. It implements the stream processing logic from the earlier blog [An Apache Kafka and RisingWave Stream Processing Christmas Special](https://www.instaclustr.com/blog/apache-kafka-and-risingwave-stream-processing/).

This first part focuses on the SQL logic (joins and time windows) using static data. A follow-up part will integrate with Kafka topics for a complete real-time pipeline.

## Getting Started

1. Set up a ClickHouse instance (see Prerequisites).
2. Connect to ClickHouse using the client:
   ```bash
   clickhouse-client --host <your-host> --port 9000 --user <user> --password <password>
   ```
3. Run the SQL scripts in order:
   ```
   createTables.sql  - Creates the 'toys' and 'boxes' tables
   populate.sql      - Inserts 300 sample rows per table with random data
   query1.sql        - Basic JOIN matching toys to boxes by type (no time window)
   query2.sql        - JOIN with 60-second time window constraint
   query3.sql        - Ranked matches selecting the closest box per toy type
   query4.sql        - Same as query3 filtered to recent data only (last 60 seconds)
   query5.sql        - Tumble window function for fixed 60-second windows
   query6.sql        - Tumble window with start/end time boundaries
   query7.sql        - Hop (sliding) window function with 60s window and 30s hop
   ```

### Prerequisites

- **ClickHouse** - Either open source ClickHouse installed locally or [NetApp Instaclustr managed ClickHouse](https://www.instaclustr.com/support/documentation/clickhouse/)
- A ClickHouse client (CLI or GUI) for executing SQL queries

## Deployment

No special deployment required. These are standalone SQL scripts that run directly against any ClickHouse instance.

For creating an Instaclustr managed ClickHouse cluster, see the [ClickHouse documentation](https://www.instaclustr.com/support/documentation/clickhouse/).

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
