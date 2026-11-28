package com.wealth.bss.nexus.serviceimple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wealth.bss.config.KafkaEndpoint;

// The brokers are not needed here: a mock producer stands in, and a consumer record is built by hand. A real broker is tried in the local run.
class NexusEventServiceImpleTest {

	private KafkaEndpoint kafka;
	private final AtomicLong now = new AtomicLong(1_000_000);
	private MockProducer<String, String> producer;
	private NexusEventServiceImple events;

	@BeforeEach
	void setUp() {
		kafka = mock(KafkaEndpoint.class);
		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.isConfigured()).thenReturn(true);
		when(kafka.current()).thenReturn("broker:9093");
		when(kafka.clientProperties("broker:9093")).thenAnswer(i -> new HashMap<String, Object>());
		producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
		Function<java.util.Map<String, Object>, Producer<String, String>> producerFactory = props -> producer;
		Function<java.util.Map<String, Object>, Consumer<String, String>> consumerFactory = props -> mock(Consumer.class);
		events = new NexusEventServiceImple(kafka, now::get, producerFactory, consumerFactory);
	}

	// The consumer counts as connected once Kafka has given it partitions; the loop sets this, the test sets it through the field.
	private void consumerConnected() throws Exception {
		java.lang.reflect.Field field = NexusEventServiceImple.class.getDeclaredField("connectedAt");
		field.setAccessible(true);
		field.setLong(events, now.get());
	}

	@Test
	void aRefreshRequestGoesToTheMoneyTopicWithTheEventHeaderAndTheCacheKeyAsTheKey() throws Exception {
		consumerConnected();

		assertTrue(events.publishRefresh("wealth:nexus-totals:u1", "{\"name\":\"totals\"}"));

		assertEquals(1, producer.history().size());
		var sent = producer.history().get(0);
		assertEquals("wealth.money-events", sent.topic());
		assertEquals("wealth:nexus-totals:u1", sent.key());
		assertEquals("{\"name\":\"totals\"}", sent.value());
		assertEquals("cache.refresh", new String(sent.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8));
	}

	@Test
	void nothingIsSentWhenKafkaIsInactiveNotConfiguredOrTheConsumerIsNotConnected() throws Exception {
		assertFalse(events.publishRefresh("k", "p"), "the consumer is not connected yet");
		consumerConnected();
		when(kafka.isEnabled()).thenReturn(false);
		assertFalse(events.publishRefresh("k", "p"));
		when(kafka.isEnabled()).thenReturn(true);
		when(kafka.isConfigured()).thenReturn(false);
		assertFalse(events.publishRefresh("k", "p"));
		consumerConnected();
		when(kafka.isConfigured()).thenReturn(true);
		now.addAndGet(16_000);
		assertFalse(events.publishRefresh("k", "p"), "the consumer has not been connected for a while");

		assertEquals(0, producer.history().size());
	}

	@Test
	void aFailedSendReportsFalseSoTheBssRefreshesByItself() throws Exception {
		consumerConnected();
		MockProducer<String, String> failing = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
		NexusEventServiceImple failingEvents = new NexusEventServiceImple(kafka, now::get, props -> failing, props -> mock(Consumer.class));
		java.lang.reflect.Field field = NexusEventServiceImple.class.getDeclaredField("connectedAt");
		field.setAccessible(true);
		field.setLong(failingEvents, now.get());
		AtomicReference<Boolean> result = new AtomicReference<>();
		Thread sender = new Thread(() -> result.set(failingEvents.publishRefresh("k", "p")));
		sender.start();
		Thread.sleep(200);
		failing.errorNext(new RuntimeException("broker gone"));
		sender.join(5000);

		assertEquals(false, result.get());
	}

	@Test
	void aGmailReadRequestGoesOutWithItsOwnEventHeaderAndComesBackToItsOwnHandler() throws Exception {
		consumerConnected();
		AtomicReference<String> gmail = new AtomicReference<>();
		AtomicReference<String> refresh = new AtomicReference<>();
		events.onGmailSync(gmail::set);
		events.onRefresh(refresh::set);

		assertTrue(events.publishGmailSync("u1", "{\"userId\":\"u1\",\"kind\":\"all\"}"));
		var sent = producer.history().get(0);
		assertEquals("u1", sent.key());
		assertEquals("gmail.sync", new String(sent.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8));

		ConsumerRecord<String, String> record = new ConsumerRecord<>("wealth.money-events", 0, 0, "u1", sent.value());
		record.headers().add("eventType", "gmail.sync".getBytes(StandardCharsets.UTF_8));
		events.handle(record);
		assertEquals(sent.value(), gmail.get());
		assertEquals(null, refresh.get(), "a Gmail request never reaches the refresh handler");
	}

	@Test
	void onlyRefreshMessagesAreHandledAndAFailingHandlerDoesNotStopTheConsumer() {
		AtomicReference<String> got = new AtomicReference<>();
		events.onRefresh(got::set);

		ConsumerRecord<String, String> refresh = new ConsumerRecord<>("wealth.money-events", 0, 0, "k", "payload");
		refresh.headers().add("eventType", "cache.refresh".getBytes(StandardCharsets.UTF_8));
		ConsumerRecord<String, String> other = new ConsumerRecord<>("wealth.money-events", 0, 1, "k", "other");
		other.headers().add("eventType", "payment.done".getBytes(StandardCharsets.UTF_8));
		ConsumerRecord<String, String> bare = new ConsumerRecord<>("wealth.money-events", 0, 2, "k", "bare");

		events.handle(other);
		events.handle(bare);
		assertEquals(null, got.get());
		events.handle(refresh);
		assertEquals("payload", got.get());

		events.onRefresh(payload -> {
			throw new IllegalStateException("boom");
		});
		events.handle(refresh);
		events.onRefresh(got::set);
		events.handle(refresh);
		assertEquals("payload", got.get(), "the next request is still handled");
	}
}
