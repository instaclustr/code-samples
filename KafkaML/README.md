Streaming data training and evaluation data for incremental Kafka ML experiments. 

The data is intended to be written to a Kafka topic to emulate streaming data coming from the Drone Delivery application.
Each record is an observation at at particular time (day, hour) if each shop is busy or not busy. There are 2 weeks of data, concept drift is introduced in the 2nd week. There is a dependency on time features (hour, day) for most data sets (except where noted).
