# Apache Kafka Connect Postgresql Sink Connector

This code is for an Apache Kafka Connect Postgres Sink Connector (for JSON only)

This demo comes from the this [blog post](<https://www.instaclustr.com/blog/kafka-postgres-connector-pipeline-series-part-6/>)

It's a customised version of this connector: <https://github.com/ibm-messaging/kafka-connect-jdbc-sink>

It is intented for a single purpose: It takes a Kafka record which has a value in JSON, and inserts the entire JSON object into a Postgresql Table. Currently the column name is hardcoded to be "json_object", which has a data type of jsonb. The table also has an "id" column which is the primary key (automatically incremented).  If the table doesn't exist it's automatically created with these 2 columns, and a gin index for the json_object column (with a unique name). That's it! It just "works" and keeps on working even if there are errors on the Postgresql side (e.g. Postgresql checks if the json if well formed, and complains if it's not, but the connector keeps on running ok).

## Getting Started

Coming Soon!

### Prerequisites

Coming Soon!

## Deployment

Coming Soon!

## Authors

* **Paul Brebner** - *Initial work* - [Instaclustr by NetApp](https://github.com/Instaclustr)

See also the list of [MAINTAINERS]( https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
