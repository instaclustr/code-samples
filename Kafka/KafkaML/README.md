# Streaming Data for Incremental Kafka ML Experiments

## Description

Streaming training and evaluation data for incremental Kafka ML experiments. The data is in simple CSV format (comma separated) intended to be written to a Kafka topic to emulate streaming data coming from the Drone Delivery application.

Each record is an observation at a particular time (day, hour) indicating if each shop is busy or not busy. There are 2 weeks of data, and concept drift is introduced in the 2nd week. There is a dependency on time features (hour, day) for most datasets (except where noted).

## Getting Started

### Datasets

* **drift_2weeks_V2.csv** - Contains 2 weeks of hourly shop busy/not busy data, with rules in the 2nd week different to the 1st week.
* **lots.csv** - Contains 2 weeks of delivery-level data (per delivery), with class being "delayed/not delayed" using similar rules to the shop busy rules.
* **2weeksNoTime.csv** - 2 weeks of data with concept shift in the 2nd week, but the rules do not depend on any time features (hour, day). This is the simplest dataset to learn over.

### Running the Streamer

1. Install the required Python packages:
   ```bash
   pip install kafka-python pandas
   ```
2. Update the Kafka broker address and topic name in `kafka-csv-streamer.py`.
3. Run the script to stream CSV data to a Kafka topic:
   ```bash
   python kafka-csv-streamer.py
   ```

The script reads the CSV file row by row, converts each to JSON, and sends it to the Kafka topic with a 100ms delay between messages to simulate real-time streaming.

### Prerequisites

* **Python 3** with `kafka-python` and `pandas` packages
* **Apache Kafka** broker and topic
* One of the included CSV data files
* The [kafka-csv-streamer.py](https://github.com/instaclustr/code-samples/blob/main/Kafka/KafkaML/kafka-csv-streamer.py) script

## Deployment

This is a local data streaming tool. Run the Python script from any machine that has network access to your Kafka broker. No server-side deployment is required.

## Authors

* **Paul Brebner** - *Initial work* - [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
