# kafka-docker
Contains a Docker Compose that have 4 brokers and 1 Zookeeper
## Run
`docker-compose up`

`mvn clean install`

### Kafka
Run `App.java` in order to produce and consume messages. Notice that `runProducers()` must be uncommented
### Kafka Streams
Run `AppStreaming.java`

## Stop
`docker-compose down`

## UI
Visit http://localhost:9000
