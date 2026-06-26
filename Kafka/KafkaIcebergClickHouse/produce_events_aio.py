# produce_events_aio.py
# Produces synthetic user events to a Kafka topic using aiokafka.
#
# Requirements:
#   pip install aiokafka
#
# Set environment variables before running:
#   export KAFKA_BOOTSTRAP="<broker1>:9092,<broker2>:9092"
#   export KAFKA_USER="<your-kafka-username>"
#   export KAFKA_PASSWORD="<your-kafka-password>"

import asyncio, json, os, random
from aiokafka import AIOKafkaProducer
from datetime import datetime, UTC

KAFKA_BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "<your-kafka-bootstrap>:9092")
KAFKA_USER      = os.environ.get("KAFKA_USER", "<your-kafka-username>")
KAFKA_PASSWORD  = os.environ.get("KAFKA_PASSWORD", "<your-kafka-password>")
KAFKA_TOPIC     = os.environ.get("KAFKA_TOPIC", "user-events")

event_types = ["page_view", "click", "signup", "purchase", "logout"]
pages = ["/home", "/pricing", "/docs", "/blog", "/signup", "/checkout"]

async def produce():
    producer = AIOKafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP,
        security_protocol="SASL_PLAINTEXT",
        sasl_mechanism="SCRAM-SHA-256",
        sasl_plain_username=KAFKA_USER,
        sasl_plain_password=KAFKA_PASSWORD,
        value_serializer=lambda v: json.dumps(v).encode(),
    )
    await producer.start()
    try:
        for i in range(50):
            event = {"event_id": i+1, "user_id": f"u_{random.randint(1000,1099)}",
                     "event_type": random.choice(event_types), "page": random.choice(pages),
                     "event_ts": datetime.now(UTC).isoformat()}
            await producer.send(KAFKA_TOPIC, key=event["user_id"].encode(), value=event)
        await producer.flush()
        print(f"Produced 50 events to '{KAFKA_TOPIC}'")
    finally:
        await producer.stop()

asyncio.run(produce())
