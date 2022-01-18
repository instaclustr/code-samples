Example code for Cadence Kafka Blog - how to integrate sending a message to Kafka and getting a reply back, using signals or activity completions.

Needs to have an Instaclustr Kafka and Cadence cluster running, with auto topic creation enabled, with host IPs, user and password set in code and kafka properties files.

Run the Kafka Consumer class first, and then run the Cadence class with either signal or activity completeions flag true/false.
The Kafka cluster detects if the reply should be sent with signal or activity completion.
