Streaming data training and evaluation data for incremental Kafka ML experiments. 

The data is in simple CSV format (comma seperated) intended to be written to a Kafka topic to emulate streaming data coming from the Drone Delivery application.
Each record is an observation at at particular time (day, hour) if each shop is busy or not busy. There are 2 weeks of data, concept drift is introduced in the 2nd week. There is a dependency on time features (hour, day) for most data sets (except where noted).

drift_2weeks_V2.csv - contains 2 weeks of hourly shop busy/not busy data, with rules in 2nd week different to 1st week.
lots.csv - contains 2 weeks of delivery level data (per delivery), with class being "delayed/not delayed" - but using similar rules to shop busy rules.
2weeksNoTime.csv - 2 weeks of data, with concept shift in 2nd week, but this time the rules do not depend on any time features (hour, day) - this is the simplest dataset to learn over.
