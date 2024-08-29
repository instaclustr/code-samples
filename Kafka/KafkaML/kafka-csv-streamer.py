import pandas as pd
from kafka import KafkaProducer
import json
import time

# Kafka configuration
bootstrap_servers = ['kafkabrokeraddress']  # Replace with your Kafka broker address
topic_name = 'drone-delivery-data'  # Replace with your desired topic name

# CSV file path
csv_file_path = 'drift_2weeks_V2.csv'  # Replace with the path to your CSV file

# Create Kafka producer
producer = KafkaProducer(bootstrap_servers=bootstrap_servers,
                         value_serializer=lambda v: json.dumps(v).encode('utf-8'))

# Read CSV file
df = pd.read_csv(csv_file_path)

# Iterate through each row and send to Kafka topic
for index, row in df.iterrows():
    # Convert row to dictionary
    message = row.to_dict()
    
    # Send message to Kafka topic
    producer.send(topic_name, value=message)
    
    print(f"Sent message: {message}")
    
    # add a small delay to simulate streaming
    time.sleep(0.1)  # Adjust the delay as needed

# Ensure all messages are sent
producer.flush()

print("Finished streaming CSV data to Kafka topic.")
