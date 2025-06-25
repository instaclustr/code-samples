# bash script to perform a rebalancing experiment in Kafka 4.0
# create a topic with 100 partitions, create a consumer group with 10 consumers subscribed to the topic, increase the number of partitions to 1000, run the kafka-consumer-group command to detect when the reblancing completes
# For the new protocol replace “--consumer-property group.protocol=classic” with “--consumer-property  group.protocol=consumer”
# hint: make sure you are changing the --consumer-property, not --property (or nothing will change)
# For this you need Kafka 4.0 downloaded and installed for the new 4.0 CLI commands to work

./kafka-topics.sh --bootstrap-server IP:9092 --topic test100 --create --partitions 100

for c in 1 2 3 4 5 6 7 8 9 10
do
	echo "consumer: $c"
./kafka-console-consumer.sh --bootstrap-server IP:9092 --topic test100 --group 101 --consumer-property group.protocol=classic >/dev/null&
done

sleep 10
date
echo "starting producer"
./kafka-producer-perf-test.sh --producer-props bootstrap.servers=IP:9092 --topic test100 --record-size 10 --throughput 1000 --num-records 2000000&

sleep 10
echo "increasing to 1000 partitions"
./kafka-topics.sh --bootstrap-server IP:9092 --topic test100 --alter --partitions 1000

counter=0
while true
do
	echo "counter is: $counter"
	date
	./kafka-consumer-groups.sh --bootstrap-server IP:9092 --describe --group 101
((counter++))
	sleep 1
done
