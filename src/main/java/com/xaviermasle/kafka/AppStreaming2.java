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
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Printed;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * If we generate a messages like these:
 * topic xavi-1  <key,value> <G,qwe> <J,rty>
 * topic xavi-2  <key,value> <Y,vcz> <G,yui>
 *
 * A new entry will be generated in xavi-out with <key,value> <rty-yui,G>
 */
public class AppStreaming2 {

    public static void main(final String[] args) throws Exception {
        createTopic();
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "xavi-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092,localhost:9093,localhost:9094,localhost:9095");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> xavi1Stream = builder.stream("xavi-1");
        KStream<String, String> xavi2Stream = builder.stream("xavi-2");
        final KStream<String, String> joinStream = xavi1Stream.join(xavi2Stream, (value1, value2) -> value1 + "-" + value2, JoinWindows.of(Duration.ofSeconds(30)));
        joinStream.print(Printed.toSysOut());
        joinStream.map((key, value) -> new KeyValue<>(value, key)).to("xavi-output");

        final Topology topology = builder.build();
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();
        runProducer();
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
            final ProducerRecord<String, String> record1 = new ProducerRecord<String, String>("xavi-1", createRandomWord(1), createRandomWord(5));
            final ProducerRecord<String, String> record2 = new ProducerRecord<String, String>("xavi-2", createRandomWord(1), createRandomWord(5));
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

}
