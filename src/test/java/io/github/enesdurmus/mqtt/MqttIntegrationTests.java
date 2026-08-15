package io.github.enesdurmus.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@EnabledIf("dockerAvailable")
@SpringBootTest(classes = MqttIntegrationTests.TestApplication.class)
class MqttIntegrationTests {

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Container
    static final HiveMQContainer BROKER =
            new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"));

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.url", () -> "tcp://" + BROKER.getHost() + ":" + BROKER.getMqttPort());
        registry.add("mqtt.client-id", () -> "integration-test");
        registry.add("mqtt.fail-fast", () -> true);
        registry.add("sensor.topic", () -> "sensors/temperature");
    }

    @Autowired
    MqttOperations mqtt;

    @Autowired
    Listeners listeners;

    @Autowired
    MqttListenerEndpointRegistry registry;

    @BeforeEach
    void reset() {
        listeners.clear();
    }

    @Test
    void deliversAJsonPayloadToAnAnnotatedListener() {
        mqtt.publish("sensors/temperature", new Reading("kitchen", 21.5), 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.readings).containsExactly(new Reading("kitchen", 21.5)));
    }

    @Test
    void bindsTheConcreteTopicForAWildcardSubscription() {
        mqtt.publish("devices/thermostat-7/events", "on", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.deviceEvents)
                        .containsExactly("devices/thermostat-7/events=on"));
    }

    @Test
    void deliversToEveryListenerSharingATopic() {
        mqtt.publish("shared/topic", "ping", 1);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(listeners.sharedFirst).containsExactly("ping");
            assertThat(listeners.sharedSecond).containsExactly("ping");
        });
    }

    @Test
    void roundTripsAStringWithoutJsonQuoting() {
        mqtt.publish("text/plain", "sıcaklık ölçümü", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.texts).containsExactly("sıcaklık ölçümü"));
    }

    @Test
    void retainedMessageReachesAListenerSubscribingLater() {
        mqtt.retain("state/late/door", "open", 1);

        MqttListenerContainer container = registry.getListenerContainer("late-subscriber");
        container.stop();
        listeners.lateState.clear();
        container.start();

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.lateState).contains("open"));

        mqtt.clearRetained("state/late/door", 1);
    }

    @Test
    void doNotSendRetainHandlingSuppressesTheReplay() {
        mqtt.retain("state/live/valve", "closed", 1);

        MqttListenerContainer container = registry.getListenerContainer("live-only");
        container.stop();
        listeners.liveState.clear();
        container.start();

        mqtt.publish("state/live/valve", "opened", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.liveState).containsExactly("opened"));

        mqtt.clearRetained("state/live/valve", 1);
    }

    @Test
    void clearRetainedRemovesTheStoredMessage() {
        mqtt.retain("state/cleared/lamp", "on", 1);
        mqtt.clearRetained("state/cleared/lamp", 1);

        MqttListenerContainer container = registry.getListenerContainer("cleared-subscriber");
        container.stop();
        listeners.clearedState.clear();
        container.start();

        mqtt.publish("state/cleared/lamp", "sentinel", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.clearedState).containsExactly("sentinel"));
    }

    @Test
    void retainAsPublishedPreservesTheRetainFlagOnLiveTraffic() {
        mqtt.retain("state/flagged/pump", "running", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.retainFlags).contains(true));

        mqtt.clearRetained("state/flagged/pump", 1);
    }

    @Test
    void propagatesMqtt5UserProperties() {
        mqtt.publish("meta/props", "x", 1, false, Map.of(
                MqttHeaders.USER_PROPERTIES, Map.of("tenant", "acme")));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.userProperties).containsEntry("tenant", "acme"));
    }

    @Test
    void bindsEveryDocumentedParameterType() {
        mqtt.publish("binding/all", "payload-text", 1);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(listeners.bindings).containsExactly(
                        "payload=payload-text",
                        "topic=binding/all",
                        "qos=1",
                        "headers=binding/all",
                        "rawMessage=payload-text",
                        "accessor=binding/all"));
    }

    @Test
    void routesFailuresToThePerListenerErrorHandler() {
        mqtt.publish("failing/topic", "boom", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.errors).hasSize(1));
    }

    @Test
    void resolvesPropertyPlaceholdersInTopics() {
        mqtt.publish("sensors/temperature", new Reading("attic", 3.0), 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.readings).extracting(Reading::sensor).contains("attic"));
    }

    @Test
    void aStoppedListenerReceivesNothing() {
        MqttListenerContainer container = registry.getListenerContainer("stoppable");
        container.stop();

        mqtt.publish("stoppable/topic", "ignored", 1);
        mqtt.publish("text/plain", "barrier", 1);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.texts).contains("barrier"));
        assertThat(listeners.stoppable).isEmpty();

        container.start();
    }

    @Test
    void programmaticEndpointsAreRegisteredAndReceiveMessages() {
        mqtt.publish("programmatic/topic", "hello", 1);

        assertThat(registry.getListenerContainerIds()).contains("programmatic-listener");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listeners.programmatic).containsExactly("hello"));
    }

    record Reading(String sensor, double value) {
    }

    @Component
    static class Listeners {

        final List<Reading> readings = new CopyOnWriteArrayList<>();
        final List<String> deviceEvents = new CopyOnWriteArrayList<>();
        final List<String> sharedFirst = new CopyOnWriteArrayList<>();
        final List<String> sharedSecond = new CopyOnWriteArrayList<>();
        final List<String> texts = new CopyOnWriteArrayList<>();
        final List<String> lateState = new CopyOnWriteArrayList<>();
        final List<String> liveState = new CopyOnWriteArrayList<>();
        final List<String> clearedState = new CopyOnWriteArrayList<>();
        final List<Boolean> retainFlags = new CopyOnWriteArrayList<>();
        final List<String> stoppable = new CopyOnWriteArrayList<>();
        final List<String> programmatic = new CopyOnWriteArrayList<>();
        final List<String> bindings = new CopyOnWriteArrayList<>();
        final List<Exception> errors = new CopyOnWriteArrayList<>();
        final Map<String, String> userProperties = new ConcurrentHashMap<>();

        void clear() {
            List.of(readings, deviceEvents, sharedFirst, sharedSecond, texts, lateState,
                    liveState, clearedState, retainFlags, stoppable, programmatic, bindings, errors)
                    .forEach(List::clear);
            userProperties.clear();
        }

        @MqttListener(topics = "${sensor.topic}", qos = 1)
        void onReading(@Payload Reading reading) {
            readings.add(reading);
        }

        @MqttListener(topics = "devices/+/events", qos = 1)
        void onDeviceEvent(@Topic String topic, @Payload String payload) {
            deviceEvents.add(topic + "=" + payload);
        }

        @MqttListener(topics = "shared/topic", qos = 1)
        void onSharedFirst(@Payload String payload) {
            sharedFirst.add(payload);
        }

        @MqttListener(topics = "shared/topic", qos = 1)
        void onSharedSecond(@Payload String payload) {
            sharedSecond.add(payload);
        }

        @MqttListener(topics = "text/plain", qos = 1)
        void onText(@Payload String payload) {
            texts.add(payload);
        }

        @MqttListener(id = "late-subscriber", topics = "state/late/door", qos = 1)
        void onLateState(@Payload String payload) {
            lateState.add(payload);
        }

        @MqttListener(id = "live-only", topics = "state/live/valve", qos = 1,
                retainHandling = RetainHandling.DO_NOT_SEND)
        void onLiveState(@Payload String payload) {
            liveState.add(payload);
        }

        @MqttListener(id = "cleared-subscriber", topics = "state/cleared/lamp", qos = 1)
        void onClearedState(@Payload String payload) {
            clearedState.add(payload);
        }

        @MqttListener(topics = "state/flagged/pump", qos = 1, retainAsPublished = true)
        void onFlaggedState(@Header(MqttHeaders.RECEIVED_RETAINED) boolean retained) {
            retainFlags.add(retained);
        }

        @MqttListener(topics = "binding/all", qos = 1)
        void onEveryBinding(@Payload String payload,
                            @Topic String topic,
                            @Header(MqttHeaders.RECEIVED_QOS) int qos,
                            @Headers Map<String, Object> headers,
                            Message<byte[]> rawMessage,
                            MqttMessageHeaderAccessor accessor) {
            bindings.add("payload=" + payload);
            bindings.add("topic=" + topic);
            bindings.add("qos=" + qos);
            bindings.add("headers=" + headers.get(MqttHeaders.RECEIVED_TOPIC));
            bindings.add("rawMessage=" + new String(rawMessage.getPayload()));
            bindings.add("accessor=" + accessor.getReceivedTopic());
        }

        @MqttListener(topics = "meta/props", qos = 1)
        void onUserProperties(@Header(MqttHeaders.USER_PROPERTIES) Map<String, String> properties) {
            userProperties.putAll(properties);
        }

        @MqttListener(id = "stoppable", topics = "stoppable/topic", qos = 1)
        void onStoppable(@Payload String payload) {
            stoppable.add(payload);
        }

        @MqttListener(topics = "failing/topic", qos = 1, errorHandler = "recordingErrorHandler")
        void onFailing(@Payload String payload) {
            throw new IllegalStateException("intentional failure: " + payload);
        }
    }

    @SpringBootApplication
    @Configuration(proxyBeanMethods = false)
    static class TestApplication {

        @Bean
        Listeners listeners() {
            return new Listeners();
        }

        @Bean
        MqttListenerErrorHandler recordingErrorHandler(Listeners listeners) {
            return (message, exception) -> listeners.errors.add(exception);
        }

        @Bean
        MqttListenerConfigurer programmaticListeners(Listeners listeners) {
            return registrar -> registrar.registerEndpoint(
                    SimpleMqttListenerEndpoint.builder("programmatic/topic")
                            .id("programmatic-listener")
                            .qos(1)
                            .messageHandler(message -> listeners.programmatic.add(
                                    new String((byte[]) message.getPayload())))
                            .build());
        }
    }
}
