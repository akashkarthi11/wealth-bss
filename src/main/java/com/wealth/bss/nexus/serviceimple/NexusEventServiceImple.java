package com.wealth.bss.nexus.serviceimple;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.wealth.bss.config.KafkaEndpoint;
import com.wealth.bss.constants.WealthServiceConstants;
import com.wealth.bss.nexus.service.IfNexusEventService;
import com.wealth.common.logger.KarthiLogger;

import jakarta.annotation.PreDestroy;

// One producer and one consumer (group wealth-nexus) on one background thread. Both are built from the current Kafka settings and rebuilt when the servers
// change. A broker that does not answer never blocks a request: sends are bounded to a few seconds and report false, and the consumer only counts as connected
// once Kafka has given it partitions.
@Service
public class NexusEventServiceImple implements IfNexusEventService {

	private static final KarthiLogger LOGGER = new KarthiLogger(NexusEventServiceImple.class);

	private final KafkaEndpoint kafka;
	private final LongSupplier clock;
	private final Function<Map<String, Object>, Producer<String, String>> producerFactory;
	private final Function<Map<String, Object>, org.apache.kafka.clients.consumer.Consumer<String, String>> consumerFactory;
	private volatile Consumer<String> handler = payload -> {
	};
	private volatile Consumer<String> gmailHandler = payload -> {
	};
	private volatile Thread thread;
	private volatile boolean running;
	private volatile long connectedAt;
	private Producer<String, String> producer;
	private String producerServers = "";
	private boolean complained;
	private int failures;

	@Autowired
	public NexusEventServiceImple(KafkaEndpoint kafka) {
		this(kafka, System::currentTimeMillis, NexusEventServiceImple::newProducer, NexusEventServiceImple::newConsumer);
	}

	NexusEventServiceImple(KafkaEndpoint kafka, LongSupplier clock, Function<Map<String, Object>, Producer<String, String>> producerFactory,
			Function<Map<String, Object>, org.apache.kafka.clients.consumer.Consumer<String, String>> consumerFactory) {
		this.kafka = kafka;
		this.clock = clock;
		this.producerFactory = producerFactory;
		this.consumerFactory = consumerFactory;
	}

	private static Producer<String, String> newProducer(Map<String, Object> props) {
		return new KafkaProducer<>(props);
	}

	private static org.apache.kafka.clients.consumer.Consumer<String, String> newConsumer(Map<String, Object> props) {
		return new KafkaConsumer<>(props);
	}

	@Override
	public void onRefresh(Consumer<String> handler) {
		this.handler = handler;
	}

	@Override
	public void onGmailSync(Consumer<String> handler) {
		this.gmailHandler = handler;
	}

	@Override
	public boolean publishRefresh(String cacheKey, String payload) {
		return publish(cacheKey, payload, WealthServiceConstants.KAFKA_EVENT_CACHE_REFRESH);
	}

	@Override
	public boolean publishGmailSync(String userId, String payload) {
		return publish(userId, payload, WealthServiceConstants.KAFKA_EVENT_GMAIL_SYNC);
	}

	private boolean publish(String key, String payload, String eventType) {
		if (!usable() || clock.getAsLong() - connectedAt > WealthServiceConstants.KAFKA_CONSUMER_ALIVE_MILLIS) {
			return false;
		}
		try {
			Producer<String, String> sender = producer();
			ProducerRecord<String, String> record = new ProducerRecord<>(WealthServiceConstants.KAFKA_TOPIC_MONEY, key, payload);
			record.headers().add(WealthServiceConstants.KAFKA_HEADER_EVENT, eventType.getBytes(StandardCharsets.UTF_8));
			sender.send(record).get(WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			LOGGER.warn("Kafka did not take the " + eventType + " request (" + e.getClass().getSimpleName() + "); the BSS does the work itself");
			return false;
		}
	}

	// Starts the consumer thread once the application is up; nothing connects while Kafka is Inactive or not configured.
	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this::loop, "nexus-kafka");
		thread.setDaemon(true);
		thread.start();
	}

	@PreDestroy
	public void stop() {
		running = false;
		Thread worker = thread;
		if (worker != null) {
			worker.interrupt();
		}
		closeProducer();
	}

	private boolean usable() {
		return kafka.isEnabled() && kafka.isConfigured();
	}

	private void loop() {
		org.apache.kafka.clients.consumer.Consumer<String, String> consumer = null;
		String servers = "";
		while (running) {
			try {
				if (!usable()) {
					consumer = close(consumer);
					connectedAt = 0;
					pause(WealthServiceConstants.KAFKA_RETRY_MILLIS);
					continue;
				}
				if (consumer == null || !servers.equals(kafka.current())) {
					close(consumer);
					servers = kafka.current();
					consumer = consumerFactory.apply(consumerProperties(servers));
					consumer.subscribe(List.of(WealthServiceConstants.KAFKA_TOPIC_MONEY));
				}
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(WealthServiceConstants.KAFKA_POLL_MILLIS));
				if (!consumer.assignment().isEmpty()) {
					connectedAt = clock.getAsLong();
					complained = false;
					failures = 0;
				}
				for (ConsumerRecord<String, String> record : records) {
					handle(record);
				}
			} catch (RuntimeException e) {
				if (!complained) {
					complained = true;
					LOGGER.warn("The Kafka consumer stopped (" + e.getClass().getSimpleName() + "); it is retried every few seconds");
				}
				consumer = close(consumer);
				connectedAt = 0;
				failures = Math.min(failures + 1, WealthServiceConstants.KAFKA_RETRY_DOUBLINGS + 1);
				pause(WealthServiceConstants.KAFKA_RETRY_MILLIS << (failures - 1));
			}
		}
		close(consumer);
	}

	// Only refresh and Gmail read requests are handled; a failing handler never stops the consumer.
	void handle(ConsumerRecord<String, String> record) {
		Header header = record.headers().lastHeader(WealthServiceConstants.KAFKA_HEADER_EVENT);
		if (header == null) {
			return;
		}
		String event = new String(header.value(), StandardCharsets.UTF_8);
		Consumer<String> target = WealthServiceConstants.KAFKA_EVENT_CACHE_REFRESH.equals(event) ? handler : WealthServiceConstants.KAFKA_EVENT_GMAIL_SYNC.equals(event) ? gmailHandler : null;
		if (target == null) {
			return;
		}
		try {
			target.accept(record.value());
		} catch (RuntimeException e) {
			LOGGER.warn("A " + event + " request could not be handled (" + e.getClass().getSimpleName() + ")");
		}
	}

	private Map<String, Object> consumerProperties(String servers) {
		Map<String, Object> props = kafka.clientProperties(servers);
		props.put("group.id", WealthServiceConstants.KAFKA_GROUP_NEXUS);
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("enable.auto.commit", true);
		props.put("auto.offset.reset", "latest");
		props.put("max.poll.records", 10);
		props.put("allow.auto.create.topics", false);
		props.put("request.timeout.ms", WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS);
		props.put("default.api.timeout.ms", WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS);
		return props;
	}

	private synchronized Producer<String, String> producer() {
		String servers = kafka.current();
		if (producer == null || !servers.equals(producerServers)) {
			closeProducer();
			Map<String, Object> props = kafka.clientProperties(servers);
			props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			props.put("max.block.ms", WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS);
			props.put("request.timeout.ms", WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS);
			props.put("delivery.timeout.ms", WealthServiceConstants.KAFKA_SEND_TIMEOUT_MS + 2000);
			props.put("acks", "1");
			producer = producerFactory.apply(props);
			producerServers = servers;
		}
		return producer;
	}

	private synchronized void closeProducer() {
		if (producer != null) {
			try {
				producer.close(Duration.ofSeconds(1));
			} catch (RuntimeException e) {
				LOGGER.warn("The Kafka producer did not close cleanly");
			}
			producer = null;
		}
	}

	private org.apache.kafka.clients.consumer.Consumer<String, String> close(org.apache.kafka.clients.consumer.Consumer<String, String> consumer) {
		if (consumer != null) {
			try {
				consumer.close(Duration.ofSeconds(1));
			} catch (RuntimeException e) {
				LOGGER.warn("The Kafka consumer did not close cleanly");
			}
		}
		return null;
	}

	private void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			running = false;
		}
	}
}
