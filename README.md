# kafka-docker
Contains a Docker Compose that have 4 brokers and 1 Zookeeper
## Run
`docker-compose up`

`mvn clean install`

Run App.java in order to produce and consume messages. Notice that `runProducers()` must be uncommented

## Stop
`docker-compose down`

## UI
Visit http://localhost:9000
