package com.wealth.bss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Runs only when KAFKA_BOOTSTRAP_SERVERS is set. Sends one random message and reads it back.
@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")
class KafkaConnectionTest {

	private static final String TOPIC = System.getenv().getOrDefault("KAFKA_TEST_TOPIC", "wealth.connection-test");

	private static Map<String, Object> connection() {
		String jaas = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + System.getenv("KAFKA_USERNAME")
				+ "\" password=\"" + System.getenv("KAFKA_PASSWORD") + "\";";
		return Map.of("bootstrap.servers", System.getenv("KAFKA_BOOTSTRAP_SERVERS"), "security.protocol", "SASL_SSL", "sasl.mechanism",
				System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "SCRAM-SHA-256"), "sasl.jaas.config", jaas, "ssl.truststore.type", "PEM",
				"ssl.truststore.location", System.getenv("KAFKA_CA_PATH"));
	}

	private static Map<String, Object> with(Map<String, Object> base, Map<String, Object> extra) {
		Map<String, Object> merged = new java.util.HashMap<>(base);
		merged.putAll(extra);
		return merged;
	}

	@Test
	void sendsOneMessageAndReadsItBack() throws Exception {
		try (AdminClient admin = AdminClient.create(connection())) {
			try {
				admin.createTopics(List.of(new NewTopic(TOPIC, java.util.Optional.empty(), java.util.Optional.empty()))).all().get();
			} catch (java.util.concurrent.ExecutionException e) {
				if (!(e.getCause() instanceof TopicExistsException)) {
					throw e;
				}
			}
		}
		String value = UUID.randomUUID().toString();
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(
				with(connection(), Map.of("key.serializer", StringSerializer.class.getName(), "value.serializer", StringSerializer.class.getName())))) {
			producer.send(new ProducerRecord<>(TOPIC, value)).get();
		}
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(with(connection(),
				Map.of("key.deserializer", StringDeserializer.class.getName(), "value.deserializer", StringDeserializer.class.getName(), "group.id",
						"wealth-connection-test-" + value, "auto.offset.reset", "earliest")))) {
			consumer.subscribe(List.of(TOPIC));
			String received = null;
			long end = System.currentTimeMillis() + 30_000;
			while (received == null && System.currentTimeMillis() < end) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(2))) {
					if (value.equals(record.value())) {
						received = record.value();
					}
				}
			}
			assertEquals(value, received);
		}
	}
}
