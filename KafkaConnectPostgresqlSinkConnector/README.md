Kafka Connect Postgresql Sink Connector (for JSON only)

This is a demo Kafka Connect Postgresql Sink Connector

It's a customised version of this connector: https://github.com/ibm-messaging/kafka-connect-jdbc-sink

It is intented for a single purpose: It takes a Kafka record which has a value in JSON, and inserts the entire JSON object into a Postgresql Table. Currently the column name is hardcoded to be "json_object", which has a data type of jsonb. The table also has an "id" column which is the primary key (automatically incremented).  If the table doesn't exist it's automatically created with these 2 columns, and a gin index for the json_object column (with a unique name). That's it! It just "works" and keeps on working even if there are errors on the Postgresql side (e.g. Postgresql checks if the json if well formed, and complains if it's not, but the connector keeps on running ok). 

TODO Link to blog here once published.
