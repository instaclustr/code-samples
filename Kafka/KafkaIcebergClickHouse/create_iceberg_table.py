# create_iceberg_table.py
# Creates an Iceberg table on S3 using PyIceberg with AWS Glue as the catalog.
# Run this once before deploying the Kafka Connect Iceberg Sink Connector.
#
# Requirements:
#   pip install "pyiceberg[glue,pyiceberg-core]" pyarrow boto3
#
# AWS credentials must have permission to:
#   - Write to your S3 bucket
#   - Create Glue databases and tables

from pyiceberg.catalog.glue import GlueCatalog
from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, StringType, TimestampType, LongType
from pyiceberg.partitioning import PartitionSpec, PartitionField
from pyiceberg.transforms import DayTransform
import pyarrow as pa
from datetime import datetime, timedelta
import random

S3_BUCKET = "<your-bucket-name>"
AWS_REGION = "<your-aws-region>"
WAREHOUSE_PATH = f"s3://{S3_BUCKET}/warehouse"

catalog = GlueCatalog("glue", **{
    "region_name": AWS_REGION,
    "warehouse": WAREHOUSE_PATH,
})

catalog.create_namespace_if_not_exists("analytics")

schema = Schema(
    NestedField(1, "event_id", LongType(), required=False),
    NestedField(2, "user_id", StringType(), required=False),
    NestedField(3, "event_type", StringType(), required=False),
    NestedField(4, "page", StringType(), required=False),
    NestedField(5, "event_ts", TimestampType(), required=False),
)

partition_spec = PartitionSpec(
    PartitionField(source_id=5, field_id=1000, transform=DayTransform(), name="event_day")
)

table = catalog.create_table_if_not_exists(
    identifier="analytics.user_events",
    schema=schema,
    partition_spec=partition_spec,
)

event_types = ["page_view", "click", "signup", "purchase", "logout"]
pages = ["/home", "/pricing", "/docs", "/blog", "/signup", "/checkout"]
base_time = datetime(2025, 2, 10, 8, 0, 0)

records = [{"event_id": i+1, "user_id": f"u_{random.randint(1000,1099)}",
            "event_type": random.choice(event_types), "page": random.choice(pages),
            "event_ts": base_time + timedelta(minutes=random.randint(0, 2880))}
           for i in range(500)]

table.append(pa.Table.from_pylist(records))
print(f"Wrote {len(records)} records to {WAREHOUSE_PATH}")
