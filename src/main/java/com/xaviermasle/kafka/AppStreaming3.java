package com.xaviermasle.kafka;

import com.xaviermasle.kafka.constants.IKafkaConstants;
import com.xaviermasle.kafka.consumer.ConsumerCreator;
import com.xaviermasle.kafka.producer.ProducerCreator;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class AppStreaming3 {

    public static void main(final String[] args) throws Exception {
        createTopic();
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "xavi-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092,localhost:9093,localhost:9094,localhost:9095");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> xavi3Stream = builder.stream("xavi-3");
        KStream<String, String> xavi4Stream = builder.stream("xavi-4");

        KTable<String, Long> wordCountsStream3 = xavi3Stream
                .flatMapValues(textLine -> Arrays.asList(textLine.toLowerCase().split("\\W+")))
                .groupBy((key, word) -> word)
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("counts-store-3"));
        // wordCountsStream3.toStream().print(Printed.toSysOut());

        KTable<String, Long> wordCountsStream4 = xavi4Stream
                .flatMapValues(textLine -> Arrays.asList(textLine.toLowerCase().split("\\W+")))
                .groupBy((key, word) -> word)
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("counts-store-4"));
        // wordCountsStream4.toStream().print(Printed.toSysOut());

        final KStream<String, Long> stringLongKStream = wordCountsStream3.join(wordCountsStream4, (value1, value2) -> {
            System.out.println("Value 1" + value1.toString());
            System.out.println("Value 2" + value2.toString());
            return Long.valueOf(value1 + value2);
        }).toStream();
        stringLongKStream.print(Printed.toSysOut());
        stringLongKStream.to("xavi-output-2", Produced.with(Serdes.String(), Serdes.Long()));

        final Topology topology = builder.build();
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();
        runProducer();
        runConsumer();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    private static void createTopic() throws ExecutionException, InterruptedException {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IKafkaConstants.KAFKA_BROKERS);
        final AdminClient admin = KafkaAdminClient.create(properties);
        NewTopic xavi1Topic = new NewTopic("xavi-1", 2, (short) 1);
        NewTopic xavi2Topic = new NewTopic("xavi-2", 2, (short) 1);
        admin.createTopics(Arrays.asList(xavi1Topic, xavi2Topic));
    }

    static void runProducer() {
        Producer<String, String> producer = ProducerCreator.createProducerString();

        for (int index = 0; index < IKafkaConstants.MESSAGE_COUNT; index++) {
            final ProducerRecord<String, String> record1 = new ProducerRecord<String, String>("xavi-3", createRandomWord(1), createRandomWord(2));
            final ProducerRecord<String, String> record2 = new ProducerRecord<String, String>("xavi-4", createRandomWord(1), createRandomWord(2));
            try {
                RecordMetadata metadata1 = producer.send(record1).get();
                RecordMetadata metadata2 = producer.send(record2).get();
                System.out.println("Record 1 sent with key " + index + " to partition " + metadata1.partition()
                        + " with offset " + metadata1.offset() + " with value " + record1.value());
                System.out.println("Record 2 sent with key " + index + " to partition " + metadata2.partition()
                        + " with offset " + metadata2.offset() + " with value " + record2.value());
            } catch (ExecutionException | InterruptedException e) {
                System.out.println("Error in sending record");
                System.out.println(e);
            }
        }
    }

    public static String createRandomWord(int len) {
        String name = "";
        for (int i = 0; i < len; i++) {
            int v = 1 + (int) (Math.random() * 26);
            char c = (char) (v + (i == 0 ? 'A' : 'a') - 1);
            name += c;
        }
        return name;
    }

    static void runConsumer() {
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, IKafkaConstants.KAFKA_BROKERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, IKafkaConstants.GROUP_ID_CONFIG);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, IKafkaConstants.MAX_POLL_RECORDS);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, IKafkaConstants.OFFSET_RESET_EARLIER);

        final Consumer<String, Long> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("xavi-output-2"));

        int noMessageToFetch = 0;

        while (true) {
            final ConsumerRecords<String, Long> consumerRecords = consumer.poll(1000);
            if (consumerRecords.count() == 0) {
                noMessageToFetch++;
                if (noMessageToFetch > IKafkaConstants.MAX_NO_MESSAGE_FOUND_COUNT)
                    break;
                else
                    continue;
            }

            consumerRecords.forEach(record -> {
                System.out.println("Record Key " + record.key());
                System.out.println("Record value " + record.value());
                System.out.println("Record partition " + record.partition());
                System.out.println("Record offset " + record.offset());
            });
            consumer.commitAsync();
        }
        consumer.close();
    }

}
