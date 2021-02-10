package com.xaviermasle.kafka;

import com.xaviermasle.kafka.constants.IKafkaConstants;
import com.xaviermasle.kafka.producer.ProducerCreator;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class AppStreaming {

    public static void main(final String[] args) throws Exception {
        createTopic();
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> textLines = builder.stream("TextLinesTopic");
        KTable<String, Long> wordCounts = textLines
                .flatMapValues(textLine -> Arrays.asList(textLine.toLowerCase().split("\\W+")))
                .groupBy((key, word) -> word)
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("counts-store"));
        wordCounts.toStream().to("WordsWithCountsTopic", Produced.with(Serdes.String(), Serdes.Long()));
        wordCounts.toStream().print(Printed.toSysOut());

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
        runProducer();
    }

    private static void createTopic() throws ExecutionException, InterruptedException {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IKafkaConstants.KAFKA_BROKERS);
        final AdminClient admin = KafkaAdminClient.create(properties);
        NewTopic textLinesTopic = new NewTopic("TextLinesTopic", 4, (short) 2);
        admin.createTopics(Arrays.asList(textLinesTopic));
    }

    static void runProducer() {
        Producer<String, String> producer = ProducerCreator.createProducerString();

        for (int index = 0; index < IKafkaConstants.MESSAGE_COUNT; index++) {
            final ProducerRecord<String, String> record = new ProducerRecord<String, String>("TextLinesTopic",
                    createRandomWord(5));
            try {
                RecordMetadata metadata = producer.send(record).get();
                System.out.println("Record sent with key " + index + " to partition " + metadata.partition()
                        + " with offset " + metadata.offset());
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

}
