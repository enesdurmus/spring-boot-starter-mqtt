package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.core.MqttHeaders;
import io.github.enesdurmus.mqtt.core.MqttSubscriptionManager;
import io.github.enesdurmus.mqtt.core.MqttSubscriptionOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DefaultMqttListenerContainerTests {

    private StubSubscriptionManager subscriptions;

    @BeforeEach
    void setUp() {
        subscriptions = new StubSubscriptionManager();
    }

    @Test
    void startSubscribesEveryTopicOnceAndStopCancelsThem() {
        DefaultMqttListenerContainer container = container(endpoint("a", List.of("t/1", "t/2"), message -> {
        }));

        container.start();
        container.start();

        assertThat(subscriptions.registered()).containsExactly("t/1", "t/2");
        assertThat(container.isRunning()).isTrue();

        container.stop();
        container.stop();

        assertThat(subscriptions.registered()).isEmpty();
        assertThat(container.isRunning()).isFalse();
    }

    @Test
    void aFailedSubscribeLeavesNoSubscriptionBehind() {
        subscriptions.failOn("t/2");
        DefaultMqttListenerContainer container = container(endpoint("a", List.of("t/1", "t/2"), message -> {
        }));

        assertThat(catchThrowable(container::start)).isInstanceOf(IllegalStateException.class);
        assertThat(subscriptions.registered()).isEmpty();
        assertThat(container.isRunning()).isFalse();
    }

    @Test
    void deliveredMessagesCarryTheListenerId() {
        List<Message<?>> handled = new ArrayList<>();
        DefaultMqttListenerContainer container = container(endpoint("sensors", List.of("t"), handled::add));
        container.start();

        subscriptions.deliver("t", MessageBuilder.withPayload("payload").build());

        assertThat(handled).singleElement()
                .satisfies(message -> assertThat(message.getHeaders().get(MqttHeaders.LISTENER_ID))
                        .isEqualTo("sensors"));
    }

    @Test
    void aFailingListenerFallsBackToTheDefaultErrorHandler() {
        List<Exception> handled = new ArrayList<>();
        RuntimeException failure = new RuntimeException("boom");
        DefaultMqttListenerContainer container = new DefaultMqttListenerContainer(
                endpoint("a", List.of("t"), message -> {
                    throw failure;
                }),
                subscriptions,
                new SyncTaskExecutor(),
                (message, exception) -> handled.add(exception));
        container.start();

        subscriptions.deliver("t", MessageBuilder.withPayload("payload").build());

        assertThat(handled).containsExactly(failure);
    }

    @Test
    void anEndpointErrorHandlerWinsOverTheDefaultOne() {
        List<String> called = new ArrayList<>();
        SimpleMqttListenerEndpoint endpoint = SimpleMqttListenerEndpoint.builder("t")
                .id("a")
                .messageHandler(message -> {
                    throw new RuntimeException("boom");
                })
                .errorHandler((message, exception) -> called.add("endpoint"))
                .build();

        DefaultMqttListenerContainer container = new DefaultMqttListenerContainer(
                endpoint, subscriptions, new SyncTaskExecutor(), (message, exception) -> called.add("default"));
        container.start();

        subscriptions.deliver("t", MessageBuilder.withPayload("payload").build());

        assertThat(called).containsExactly("endpoint");
    }

    private DefaultMqttListenerContainer container(MqttListenerEndpoint endpoint) {
        return new DefaultMqttListenerContainer(endpoint, subscriptions, new SyncTaskExecutor(),
                (message, exception) -> {
                });
    }

    private static SimpleMqttListenerEndpoint endpoint(String id, List<String> topics, MessageHandler handler) {
        return SimpleMqttListenerEndpoint.builder()
                .id(id)
                .topics(topics)
                .messageHandler(handler)
                .build();
    }

    private static final class StubSubscriptionManager implements MqttSubscriptionManager {

        private final Map<String, MessageHandler> handlers = new LinkedHashMap<>();
        private String failingTopic;

        void failOn(String topicFilter) {
            this.failingTopic = topicFilter;
        }

        List<String> registered() {
            return List.copyOf(handlers.keySet());
        }

        void deliver(String topicFilter, Message<?> message) {
            handlers.get(topicFilter).handleMessage(message);
        }

        @Override
        public SubscriptionHandle subscribe(String topicFilter, MqttSubscriptionOptions options,
                                            MessageHandler handler) {
            if (topicFilter.equals(failingTopic)) {
                throw new IllegalStateException("broker rejected [" + topicFilter + "]");
            }
            handlers.put(topicFilter, handler);
            return () -> handlers.remove(topicFilter);
        }
    }
}
