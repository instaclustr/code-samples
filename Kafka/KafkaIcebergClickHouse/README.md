# Kafka -> Iceberg -> ClickHouse

## Description
Sample scripts and minimal IAM policies for streaming events from Kafka into an
Apache Iceberg table on S3 (cataloged in AWS Glue), so the data can be queried
downstream with ClickHouse.

**Technologies:** Apache Kafka (primary), Apache Iceberg, AWS Glue, Amazon S3, ClickHouse.

## What it does
- `create_iceberg_table.py` - creates the `analytics.user_events` Iceberg table on
  S3 via PyIceberg + AWS Glue, partitioned by day, and seeds it with 500 sample rows.
- `produce_events_aio.py` - produces synthetic user events to a Kafka topic using
  aiokafka (SASL/SCRAM).
- `command-config.props` - Kafka CLI credentials file for `kafka-topics.sh` /
  `kafka-acls.sh`.
- `glue-minimal-policy.json` / `s3-minimal-policy.json` - least-privilege IAM
  policies for the Glue and S3 access the pipeline needs.

## Prerequisites
- Apache Kafka cluster (e.g. Instaclustr managed Apache Kafka) with SASL/SCRAM credentials.
- Python 3.11+ (the producer uses `datetime.UTC`).
- `pip install "pyiceberg[glue,pyiceberg-core]" pyarrow boto3 aiokafka`
- An AWS account with an S3 bucket and permission to create Glue databases/tables.
- Kafka CLI tools (for use with `command-config.props`).

## Examples / how to run
1. Fill in placeholders: Kafka credentials in `command-config.props`, `S3_BUCKET`
   and `AWS_REGION` in `create_iceberg_table.py`, and the `KAFKA_*` env vars below.
2. Attach the IAM policies (`glue-minimal-policy.json`, `s3-minimal-policy.json`) to
   the role or user running the scripts.
3. Create the Iceberg table:
   python create_iceberg_table.py
4. Produce events to Kafka:
   export KAFKA_BOOTSTRAP="<broker1>:9092,<broker2>:9092"
   export KAFKA_USER="<your-kafka-username>"
   export KAFKA_PASSWORD="<your-kafka-password>"
   python produce_events_aio.py

## Additional Materials
<!-- TODO: add link to the related blog post / YouTube video / talk -->

## Notes
- All credentials are placeholders - replace them before running, and never commit
  real secrets.
- The IAM policies are intentionally minimal; widen the actions/resources only if
  your setup requires it.

## License
Apache 2.0
